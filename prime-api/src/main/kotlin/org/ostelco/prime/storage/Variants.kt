package org.ostelco.prime.storage

import org.ostelco.prime.model.ApplicationToken
import org.ostelco.prime.model.Offer
import org.ostelco.prime.model.Product
import org.ostelco.prime.model.ProductClass
import org.ostelco.prime.model.PurchaseRecord
import org.ostelco.prime.model.Segment
import org.ostelco.prime.model.Subscriber
import org.ostelco.prime.model.Subscription

interface ClientDocumentStore {

    /**
     * Get token used for sending notification to user application
     */
    fun getNotificationTokens(msisdn : String): Collection<ApplicationToken>

    /**
     * Add token used for sending notification to user application
     */
    fun addNotificationToken(msisdn: String, token: ApplicationToken) : Boolean

    /**
     * Get token used for sending notification to user application
     */
    fun getNotificationToken(msisdn: String, applicationID: String): ApplicationToken?

    /**
     * Get token used for sending notification to user application
     */
    fun removeNotificationToken(msisdn: String, applicationID: String): Boolean
}

interface AdminDocumentStore

interface ClientGraphStore {

    val balances: Map<String, Long>

    /**
     * Get Subscriber Profile
     */
    fun getSubscriber(id: String): Subscriber?

    /**
     * Create Subscriber Profile
     */
    fun addSubscriber(subscriber: Subscriber, referredBy: String? = null): Boolean

    /**
     * Update Subscriber Profile
     */
    fun updateSubscriber(subscriber: Subscriber): Boolean

    /**
     * Remove Subscriber for testing
     */
    fun removeSubscriber(id: String): Boolean

    /**
     * Link Subscriber to MSISDN
     */
    fun addSubscription(id: String, msisdn: String): Boolean

    /**
     * Get Products for a given subscriber
     */
    fun getProducts(subscriberId: String): Map<String, Product>

    /**
     * Get Product to perform OCS Topup
     */
    fun getProduct(subscriberId: String?, sku: String): Product?

    /**
     * Get balance for Client
     */
    fun getSubscriptions(id: String): Collection<Subscription>?

    /**
     * Set balance after OCS Topup or Consumption
     */
    fun setBalance(msisdn: String, noOfBytes: Long): Boolean

    /**
     * Get msisdn for the given subscription-id
     */
    fun getMsisdn(subscriptionId: String): String?

    /**
     * Get all PurchaseRecords
     */
    fun getPurchaseRecords(id: String): Collection<PurchaseRecord>

    /**
     * Add PurchaseRecord after Purchase operation
     */
    fun addPurchaseRecord(id: String, purchase: PurchaseRecord): String?

    /**
     * Get list of users this user has referred to
     */
    fun getReferrals(id: String): Collection<String>

    /**
     * Get user who has referred this user.
     */
    fun getReferredBy(id: String): String?
}

interface AdminGraphStore {

    // simple create
    fun createProductClass(productClass: ProductClass): Boolean
    fun createProduct(product: Product): Boolean
    fun createSegment(segment: Segment): Boolean
    fun createOffer(offer: Offer): Boolean

    // simple update
    // updating an Offer and Product is not allowed
    fun updateSegment(segment: Segment): Boolean

    // simple getAll
    // fun getOffers(): Collection<Offer>
    // fun getSegments(): Collection<Segment>
    // fun getSubscribers(): Collection<Subscriber>
    // fun getProducts(): Collection<Product>
    // fun getProductClasses(): Collection<ProductClass>

    // simple get by id
    // fun getOffer(id: String): Offer?
    // fun getSegment(id: String): Segment?
    // fun getProductClass(id: String): ProductClass?
}
