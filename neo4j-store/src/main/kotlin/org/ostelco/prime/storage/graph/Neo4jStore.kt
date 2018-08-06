package org.ostelco.prime.storage.graph

import arrow.core.Either
import arrow.core.None
import arrow.core.Option
import arrow.core.orElse
import org.neo4j.driver.v1.Transaction
import org.ostelco.prime.model.Bundle
import org.ostelco.prime.model.Entity
import org.ostelco.prime.model.Offer
import org.ostelco.prime.model.Product
import org.ostelco.prime.model.ProductClass
import org.ostelco.prime.model.PurchaseRecord
import org.ostelco.prime.model.Segment
import org.ostelco.prime.model.Subscriber
import org.ostelco.prime.model.Subscription
import org.ostelco.prime.module.getResource
import org.ostelco.prime.ocs.OcsAdminService
import org.ostelco.prime.storage.GraphStore
import org.ostelco.prime.storage.NotFoundError
import org.ostelco.prime.storage.StoreError
import org.ostelco.prime.storage.ValidationError
import org.ostelco.prime.storage.graph.Graph.read
import org.ostelco.prime.storage.graph.Relation.BELONG_TO_SEGMENT
import org.ostelco.prime.storage.graph.Relation.HAS_BUNDLE
import org.ostelco.prime.storage.graph.Relation.HAS_SUBSCRIPTION
import org.ostelco.prime.storage.graph.Relation.LINKED_TO_BUNDLE
import org.ostelco.prime.storage.graph.Relation.OFFERED_TO_SEGMENT
import org.ostelco.prime.storage.graph.Relation.OFFER_HAS_PRODUCT
import org.ostelco.prime.storage.graph.Relation.PURCHASED
import org.ostelco.prime.storage.graph.Relation.REFERRED
import java.time.Instant
import java.util.*
import java.util.stream.Collectors

enum class Relation {
    HAS_SUBSCRIPTION,      // (Subscriber) -[HAS_SUBSCRIPTION]-> (Subscription)
    HAS_BUNDLE,            // (Subscriber) -[HAS_BUNDLE]-> (Bundle)
    LINKED_TO_BUNDLE,      // (Subscription) -[LINKED_TO_BUNDLE]-> (Bundle)
    PURCHASED,             // (Subscriber) -[PURCHASED]-> (Product)
    REFERRED,              // (Subscriber) -[REFERRED]-> (Subscriber)
    OFFERED_TO_SEGMENT,    // (Offer) -[OFFERED_TO_SEGMENT]-> (Segment)
    OFFER_HAS_PRODUCT,     // (Offer) -[OFFER_HAS_PRODUCT]-> (Product)
    BELONG_TO_SEGMENT      // (Subscriber) -[BELONG_TO_SEGMENT]-> (Segment)
}


class Neo4jStore : GraphStore by Neo4jStoreSingleton

object Neo4jStoreSingleton : GraphStore {

    private val ocs: OcsAdminService by lazy { getResource<OcsAdminService>() }

    //
    // Entity
    //

    private val subscriberEntity = EntityType(Subscriber::class.java)
    private val subscriberStore = EntityStore(subscriberEntity)

    private val productEntity = EntityType(Product::class.java)
    private val productStore = EntityStore(productEntity)

    private val subscriptionEntity = EntityType(Subscription::class.java)
    private val subscriptionStore = EntityStore(subscriptionEntity)

    private val bundleEntity = EntityType(Bundle::class.java)
    private val bundleStore = EntityStore(bundleEntity)

    //
    // Relation
    //

    private val subscriptionRelation = RelationType(
            relation = HAS_SUBSCRIPTION,
            from = subscriberEntity,
            to = subscriptionEntity,
            dataClass = Void::class.java)
    private val subscriptionRelationStore = RelationStore(subscriptionRelation)

    private val subscriberToBundleRelation = RelationType(
            relation = HAS_BUNDLE,
            from = subscriberEntity,
            to = bundleEntity,
            dataClass = Void::class.java)
    private val subscriberToBundleStore = RelationStore(subscriberToBundleRelation)

    private val subscriptionToBundleRelation = RelationType(
            relation = LINKED_TO_BUNDLE,
            from = subscriptionEntity,
            to = bundleEntity,
            dataClass = Void::class.java)
    private val subscriptionToBundleStore = RelationStore(subscriptionToBundleRelation)

    private val purchaseRecordRelation = RelationType(
            relation = PURCHASED,
            from = subscriberEntity,
            to = productEntity,
            dataClass = PurchaseRecord::class.java)
    private val purchaseRecordRelationStore = RelationStore(purchaseRecordRelation)

    private val referredRelation = RelationType(
            relation = REFERRED,
            from = subscriberEntity,
            to = subscriberEntity,
            dataClass = Void::class.java)
    private val referredRelationStore = RelationStore(referredRelation)

    // -------------
    // Client Store
    // -------------

    //
    // Balance (Subscriber - Bundle)
    //

    override fun getBundles(subscriberId: String): Collection<Bundle>? = readTransaction {
        subscriberStore.getRelated(subscriberId, subscriberToBundleRelation, transaction)
    }

    override fun updateBundle(bundle: Bundle): Boolean = writeTransaction {
        bundleStore.update(bundle, transaction)
    }

    //
    // Subscriber
    //

    override fun getSubscriber(subscriberId: String): Either<StoreError, Subscriber> =
            readTransaction { subscriberStore.get(subscriberId, transaction) }

    // TODO vihang: Move this logic to DSL + Rule Engine + Triggers, when they are ready
    override fun addSubscriber(subscriber: Subscriber, referredBy: String?): Option<StoreError> = writeTransaction {

        if (subscriber.id == referredBy) {
            return@writeTransaction Option(ValidationError(
                    type = subscriberEntity.name,
                    id = subscriber.id,
                    message = "Referred by self"))
        }

        val bundleId = subscriber.id

        var failed = subscriberStore.create(subscriber, transaction)
        if (referredBy != null) {
            // Give 1 GB if subscriber is referred
            failed
                    .ifSuccessThen { referredRelationStore.create(referredBy, subscriber.id, transaction) }
                    .ifSuccessThen { bundleStore.create(Bundle(bundleId, 1_000_000_000), transaction) }
                    .ifSuccessThen {
                        productStore
                                .get("1GB_FREE_ON_REFERRED", transaction)
                                .map {
                                    createPurchaseRecordRelation(
                                            subscriber.id,
                                            PurchaseRecord(product = it, timestamp = Instant.now().toEpochMilli()),
                                            transaction)
                                }
                                .fold({ Option(it) }, { None })
                    }
            if (failed.isEmpty()) {
                ocs.addBundle(Bundle(bundleId, 1_000_000_000))
            }
        } else {
            // Give 100 MB as free initial balance
            failed = failed
                    .ifSuccessThen { bundleStore.create(Bundle(bundleId, 100_000_000), transaction) }
                    .ifSuccessThen {
                        productStore
                                .get("100MB_FREE_ON_JOINING", transaction)
                                .map {
                                    createPurchaseRecordRelation(
                                            subscriber.id,
                                            PurchaseRecord(product = it, timestamp = Instant.now().toEpochMilli()),
                                            transaction)
                                }
                                .fold({ Option(it) }, { None })
                    }
            if (failed.isEmpty()) {
                ocs.addBundle(Bundle(bundleId, 100_000_000))
            }
        }
        failed
                .ifSuccessThen { subscriberToBundleStore.create(subscriber.id, bundleId, transaction) }
                .ifSuccessThen { subscriberToSegmentStore.create(subscriber.id, "all", transaction) }
    }

    override fun updateSubscriber(subscriber: Subscriber): Boolean = writeTransaction {
        subscriberStore.update(subscriber, transaction)
    }

    override fun removeSubscriber(subscriberId: String) = writeTransaction { subscriberStore.delete(subscriberId, transaction) }

    //
    // Subscription
    //

    override fun addSubscription(subscriberId: String, msisdn: String): Option<StoreError> = writeTransaction {
        subscriberStore.get(subscriberId, transaction)
                .map { subscriber ->

                    val bundles = subscriberStore.getRelated(subscriberId, subscriberToBundleRelation, transaction)

                    var failed: Option<StoreError> =
                            if (bundles.isEmpty()) {
                                Option(NotFoundError(type = subscriberToBundleRelation.relation.name, id = "$subscriberId -> *"))
                            } else {
                                None
                            }

                    failed.ifSuccessThen { subscriptionStore.create(Subscription(msisdn), transaction) }
                            .ifSuccessThen {
                                subscriptionStore.get(msisdn, transaction)
                                        .map { subscription ->
                                            bundles.forEach {
                                                subscriptionToBundleStore.create(subscription, it, transaction)
                                                ocs.addMsisdnToBundleMapping(msisdn, it.id)
                                            }
                                            subscriptionRelationStore.create(subscriber, subscription, transaction)
                                        }
                                        .swap()
                                        .toOption()
                            }
                }
                .swap()
                .toOption()
    }

    override fun getSubscriptions(subscriberId: String): Collection<Subscription>? {
        return readTransaction {
            subscriberStore.getRelated(subscriberId, subscriptionRelation, transaction)
        }
    }

    override fun getMsisdn(subscriptionId: String): String? {
        return readTransaction {
            subscriberStore.getRelated(subscriptionId, subscriptionRelation, transaction)
                    .first()
                    .msisdn
        }
    }

    //
    // Products
    //

    override fun getProducts(subscriberId: String): Map<String, Product> {
        return readTransaction {
            read("""
                MATCH (:${subscriberEntity.name} {id: '$subscriberId'})
                -[:${subscriberToSegmentRelation.relation.name}]->(:${segmentEntity.name})
                <-[:${offerToSegmentRelation.relation.name}]-(:${offerEntity.name})
                -[:${offerToProductRelation.relation.name}]->(product:${productEntity.name})
                RETURN product;
                """.trimIndent(),
                    transaction) { statementResult ->
                statementResult
                        .list { ObjectHandler.getObject(it["product"].asMap(), Product::class.java) }
                        .stream()
                        .collect(Collectors.toMap({ it?.sku }, { it }))
            }
        }
    }

    override fun getProduct(subscriberId: String?, sku: String): Either<StoreError, Product> =
            readTransaction { productStore.get(sku, transaction) }

    //
    // Purchase Records
    //

    override fun getPurchaseRecords(subscriberId: String): Collection<PurchaseRecord> {
        return readTransaction {
            subscriberStore.getRelations(subscriberId, purchaseRecordRelation, transaction)
        }
    }

    override fun addPurchaseRecord(subscriberId: String, purchase: PurchaseRecord): Either<StoreError, String> {
        return writeTransaction {
            createPurchaseRecordRelation(subscriberId, purchase, transaction)
        }
    }

    private fun createPurchaseRecordRelation(
            subscriberId: String,
            purchase: PurchaseRecord,
            transaction: Transaction): Either<StoreError, String> {

        return subscriberStore.get(subscriberId, transaction).map { subscriber ->
            productStore.get(purchase.product.sku, transaction).map { product ->

                purchase.id = UUID.randomUUID().toString()
                purchaseRecordRelationStore.create(subscriber, purchase, product, transaction)
                        .toEither { purchase.id }
                        .swap()
            }
        }.fold({ Either.left(it) }, { it.get() })
    }

    //
    // Referrals
    //

    override fun getReferrals(subscriberId: String): Collection<String> = readTransaction {
        subscriberStore.getRelated(subscriberId, referredRelation, transaction).map { it.name }
    }

    override fun getReferredBy(subscriberId: String): String? = readTransaction {
        subscriberStore.getRelatedFrom(subscriberId, referredRelation, transaction).singleOrNull()?.name
    }

    // ------------
    // Admin Store
    // ------------

    //
    // Balance (Subscriber - Subscription - Bundle)
    //

    override fun getMsisdnToBundleMap(): Map<Subscription, Bundle> = readTransaction {
        read("""
                MATCH (subscription:${subscriptionEntity.name})-[:${subscriptionToBundleRelation.relation.name}]->(bundle:${bundleEntity.name})<-[:${subscriberToBundleRelation.relation.name}]-(:${subscriberEntity.name})
                RETURN subscription, bundle
                """.trimIndent(),
                transaction) {
            it.list {
                Pair(ObjectHandler.getObject(it["subscription"].asMap(), Subscription::class.java),
                        ObjectHandler.getObject(it["bundle"].asMap(), Bundle::class.java))
            }.toMap()
        }
    }

    override fun getAllBundles(): Collection<Bundle> = readTransaction {
        read("""
                MATCH (:${subscriberEntity.name})-[:${subscriberToBundleRelation.relation.name}]->(bundle:${bundleEntity.name})<-[:${subscriptionToBundleRelation.relation.name}]-(:${subscriptionEntity.name})
                RETURN bundle
                """.trimIndent(),
                transaction) {
            it.list {
                ObjectHandler.getObject(it["bundle"].asMap(), Bundle::class.java)
            }.toSet()
        }
    }

    override fun getSubscriberToBundleIdMap(): Map<Subscriber, Bundle> = readTransaction {
        read("""
                MATCH (subscriber:${subscriberEntity.name})-[:${subscriberToBundleRelation.relation.name}]->(bundle:${bundleEntity.name})
                RETURN subscriber, bundle
                """.trimIndent(),
                transaction) {
            it.list {
                Pair(ObjectHandler.getObject(it["subscriber"].asMap(), Subscriber::class.java),
                        ObjectHandler.getObject(it["bundle"].asMap(), Bundle::class.java))
            }.toMap()
        }
    }

    override fun getSubscriberToMsisdnMap(): Map<Subscriber, Subscription> = readTransaction {
        read("""
                MATCH (subscriber:${subscriberEntity.name})-[:${subscriptionRelation.relation.name}]->(subscription:${subscriptionEntity.name})
                RETURN subscriber, subscription
                """.trimIndent(),
                transaction) {
            it.list {
                Pair(ObjectHandler.getObject(it["subscriber"].asMap(), Subscriber::class.java),
                        ObjectHandler.getObject(it["subscription"].asMap(), Subscription::class.java))
            }.toMap()
        }
    }

    private val offerEntity = EntityType(Entity::class.java, "Offer")
    private val offerStore = EntityStore(offerEntity)

    private val segmentEntity = EntityType(Entity::class.java, "Segment")
    private val segmentStore = EntityStore(segmentEntity)

    private val offerToSegmentRelation = RelationType(OFFERED_TO_SEGMENT, offerEntity, segmentEntity, Void::class.java)
    private val offerToSegmentStore = RelationStore(offerToSegmentRelation)

    private val offerToProductRelation = RelationType(OFFER_HAS_PRODUCT, offerEntity, productEntity, Void::class.java)
    private val offerToProductStore = RelationStore(offerToProductRelation)

    private val subscriberToSegmentRelation = RelationType(BELONG_TO_SEGMENT, subscriberEntity, segmentEntity, Void::class.java)
    private val subscriberToSegmentStore = RelationStore(subscriberToSegmentRelation)

    private val productClassEntity = EntityType(ProductClass::class.java)
    private val productClassStore = EntityStore(productClassEntity)

    override fun createProductClass(productClass: ProductClass): Option<StoreError> = writeTransaction {
        productClassStore.create(productClass, transaction)
    }

    override fun createProduct(product: Product): Option<StoreError> =
            writeTransaction { productStore.create(product, transaction) }

    override fun createSegment(segment: Segment): Option<StoreError> {
        return writeTransaction {
            segmentStore.create(segment, transaction)
                    .and(subscriberToSegmentStore.create(segment.subscribers, segment.id, transaction))
        }
    }

    override fun createOffer(offer: Offer): Option<StoreError> = writeTransaction {
        offerStore
                .create(offer, transaction)
                .orElse { offerToSegmentStore.create(offer.id, offer.segments, transaction) }
                .orElse { offerToProductStore.create(offer.id, offer.products, transaction) }
    }

    override fun updateSegment(segment: Segment): Option<StoreError> = writeTransaction {
        subscriberToSegmentStore.create(segment.id, segment.subscribers, transaction)
    }

// override fun getOffers(): Collection<Offer> = offerStore.getAll().values.map { Offer().apply { id = it.id } }

// override fun getSegments(): Collection<Segment> = segmentStore.getAll().values.map { Segment().apply { id = it.id } }

// override fun getOffer(id: String): Offer? = offerStore.get(id)?.let { Offer().apply { this.id = it.id } }

// override fun getSegment(id: String): Segment? = segmentStore.get(id)?.let { Segment().apply { this.id = it.id } }

// override fun getProductClass(id: String): ProductClass? = productClassStore.get(id)
}

fun <A> Option<A>.ifSuccessThen(next: () -> Option<A>): Option<A> = this.orElse(next)
fun <L, R> Either<L, R>.ifSuccessThen(next: () -> Either<L, R>): Either<L, R> = this.fold({ Either.left(it) }, { next() })