package org.ostelco.at.jersey

import org.junit.Test
import org.ostelco.at.common.StripePayment
import org.ostelco.at.common.createProfile
import org.ostelco.at.common.createSubscription
import org.ostelco.at.common.expectedProducts
import org.ostelco.at.common.getLogger
import org.ostelco.at.common.randomInt
import org.ostelco.prime.client.model.ActivePseudonyms
import org.ostelco.prime.client.model.ApplicationToken
import org.ostelco.prime.client.model.Bundle
import org.ostelco.prime.client.model.BundleList
import org.ostelco.prime.client.model.Consent
import org.ostelco.prime.client.model.PaymentSource
import org.ostelco.prime.client.model.PaymentSourceList
import org.ostelco.prime.client.model.Person
import org.ostelco.prime.client.model.Price
import org.ostelco.prime.client.model.Product
import org.ostelco.prime.client.model.Profile
import org.ostelco.prime.client.model.PurchaseRecord
import org.ostelco.prime.client.model.PurchaseRecordList
import org.ostelco.prime.client.model.Subscription
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileTest {

    @Test
    fun `jersey test - GET and PUT profile`() {

        val email = "profile-${randomInt()}@test.com"

        val createProfile = Profile()
                .email(email)
                .name("Test Profile User")
                .address("")
                .city("")
                .country("NO")
                .postCode("")
                .referralId("")

        val createdProfile: Profile = post {
            path = "/profile"
            body = createProfile
            subscriberId = email
        }

        assertEquals(createProfile.email, createdProfile.email, "Incorrect 'email' in created profile")
        assertEquals(createProfile.name, createdProfile.name, "Incorrect 'name' in created profile")
        assertEquals(createProfile.email, createdProfile.referralId, "Incorrect 'referralId' in created profile")

        val profile: Profile = get {
            path = "/profile"
            subscriberId = email
        }

        assertEquals(email, profile.email, "Incorrect 'email' in fetched profile")
        assertEquals(createProfile.name, profile.name, "Incorrect 'name' in fetched profile")
        assertEquals(email, profile.referralId, "Incorrect 'referralId' in fetched profile")

        profile
                .address("Some place")
                .postCode("418")
                .city("Udacity")
                .country("Online")

        val updatedProfile: Profile = put {
            path = "/profile"
            body = profile
            subscriberId = email
        }

        assertEquals(email, updatedProfile.email, "Incorrect 'email' in response after updating profile")
        assertEquals(createProfile.name, updatedProfile.name, "Incorrect 'name' in response after updating profile")
        assertEquals("Some place", updatedProfile.address, "Incorrect 'address' in response after updating profile")
        assertEquals("418", updatedProfile.postCode, "Incorrect 'postcode' in response after updating profile")
        assertEquals("Udacity", updatedProfile.city, "Incorrect 'city' in response after updating profile")
        assertEquals("Online", updatedProfile.country, "Incorrect 'country' in response after updating profile")

        updatedProfile
                .address("")
                .postCode("")
                .city("")

        val clearedProfile: Profile = put {
            path = "/profile"
            body = updatedProfile
            subscriberId = email
        }

        assertEquals(email, clearedProfile.email, "Incorrect 'email' in response after clearing profile")
        assertEquals(createProfile.name, clearedProfile.name, "Incorrect 'name' in response after clearing profile")
        assertEquals("", clearedProfile.address, "Incorrect 'address' in response after clearing profile")
        assertEquals("", clearedProfile.postCode, "Incorrect 'postcode' in response after clearing profile")
        assertEquals("", clearedProfile.city, "Incorrect 'city' in response after clearing profile")

        updatedProfile.country("")

        // A test in 'HttpClientUtil' checks for status code 200 while the
        // expected status code is actually 400.
        assertFailsWith(AssertionError::class, "Incorrectly accepts that 'country' is cleared/not set") {
            put {
                path = "/profile"
                body = updatedProfile
                subscriberId = email
            }
        }
    }

    @Test
    fun `jersey test - POST application token`() {

        val email = "token-${randomInt()}@test.com"
        createProfile("Test Token User", email)

        createSubscription(email)

        val token = UUID.randomUUID().toString()
        val applicationId = "testApplicationId"
        val tokenType = "FCM"

        val testToken = ApplicationToken()
                .token(token)
                .applicationID(applicationId)
                .tokenType(tokenType)

        val reply: ApplicationToken = post {
            path = "/applicationtoken"
            body = testToken
            subscriberId = email
        }

        assertEquals(token, reply.token, "Incorrect token in reply after posting new token")
        assertEquals(applicationId, reply.applicationID, "Incorrect applicationId in reply after posting new token")
        assertEquals(tokenType, reply.tokenType, "Incorrect tokenType in reply after posting new token")
    }
}

class GetSubscriptions {

    @Test
    fun `jersey test - GET subscriptions`() {

        val email = "subs-${randomInt()}@test.com"
        createProfile(name = "Test Subscriptions User", email = email)
        val msisdn = createSubscription(email)

        val subscriptions: Collection<Subscription> = get {
            path = "/subscriptions"
            subscriberId = email
        }

        assertEquals(listOf(msisdn), subscriptions.map { it.msisdn })
    }
}

class BundlesAndPurchasesTest {

    private val logger by getLogger()

    @Test
    fun `jersey test - GET bundles`() {

        val email = "balance-${randomInt()}@test.com"
        createProfile(name = "Test Balance User", email = email)

        val bundles: BundleList = get {
            path = "/bundles"
            subscriberId = email
        }

        logger.info("Balance: ${bundles[0].balance}")

        val freeProduct = Product()
                .sku("100MB_FREE_ON_JOINING")
                .price(Price().apply {
                    this.amount = 0
                    this.currency = "NOK"
                })
                .properties(mapOf("noOfBytes" to "100_000_000"))
                .presentation(emptyMap<String, String>())

        val purchaseRecords:PurchaseRecordList = get {
            path = "/purchases"
            subscriberId = email
        }
        purchaseRecords.sortBy { it.timestamp }

        assertEquals(listOf(freeProduct), purchaseRecords.map { it.product }, "Incorrect first 'Product' in purchase record")
    }
}

class GetPseudonymsTest {

    private val logger by getLogger()

    @Test
    fun `jersey test - GET active pseudonyms`() {

        val email = "pseu-${randomInt()}@test.com"
        createProfile(name = "Test Pseudonyms User", email = email)

        createSubscription(email)

        val activePseudonyms: ActivePseudonyms = get {
            path = "/subscription/activePseudonyms"
            subscriberId = email
        }

        logger.info("Current: ${activePseudonyms.current.pseudonym}")
        logger.info("Next: ${activePseudonyms.next.pseudonym}")
        assertNotNull(activePseudonyms.current.pseudonym, "Empty current pseudonym")
        assertNotNull(activePseudonyms.next.pseudonym, "Empty next pseudonym")
        assertEquals(activePseudonyms.current.end + 1, activePseudonyms.next.start, "The pseudonyms are not in order")
    }
}

class GetProductsTest {

    @Test
    fun `jersey test - GET products`() {

        val email = "products-${randomInt()}@test.com"
        createProfile(name = "Test Products User", email = email)

        val products: List<Product> = get {
            path = "/products"
            subscriberId = email
        }

        assertEquals(expectedProducts().toSet(), products.toSet(), "Incorrect 'Products' fetched")
    }
}

class SourceTest {

    @Test
    fun `jersey test - POST source create`() {

        val email = "purchase-${randomInt()}@test.com"
        try {

            createProfile(name = "Test Payment Source", email = email)

            val tokenId = StripePayment.createPaymentTokenId()

            // Ties source with user profile both local and with Stripe
            post<PaymentSource> {
                path = "/paymentSources"
                subscriberId = email
                queryParams = mapOf("sourceId" to tokenId)
            }

            Thread.sleep(200)

            val sources: PaymentSourceList = get {
                path = "/paymentSources"
                subscriberId = email
            }
            assert(sources.isNotEmpty()) { "Expected at least one payment source for profile $email" }

            val cardId = StripePayment.getCardIdForTokenId(tokenId)
            assertNotNull(sources.first { it.id == cardId }, "Expected card $cardId in list of payment sources for profile $email")
        } finally {
            StripePayment.deleteCustomer(email = email)
        }
    }

    @Test
    fun `jersey test - GET list sources`() {

        val email = "purchase-${randomInt()}@test.com"

        try {
            createProfile(name = "Test Payment Source", email = email)

            Thread.sleep(200)

            val createdIds = listOf(createTokenWithStripe(email),
                    createSourceWithStripe(email),
                    createTokenWithStripe(email),
                    createSourceWithStripe(email))

            val sources: PaymentSourceList = get {
                path = "/paymentSources"
                subscriberId = email
            }

            val ids = createdIds.map { getCardIdForTokenFromStripe(it) }

            assert(sources.isNotEmpty()) { "Expected at least one payment source for profile $email" }
            assert(sources.map { it.id }.containsAll(ids))
            { "Expected to find all of $ids in list of sources for profile $email" }

            sources.forEach {
                assert(it.id.isNotEmpty()) { "Expected 'id' to be set in source account details for profile $email" }
                assert(arrayOf("card", "source").contains(it.type)) {
                    "Unexpected source account type ${it.type} for profile $email"
                }
            }
        } finally {
            StripePayment.deleteCustomer(email = email)
        }
    }

    @Test
    fun `jersey test - GET list sources no profile `() {

        val email = "purchase-${randomInt()}@test.com"

        try {

            val sources: PaymentSourceList = get {
                path = "/paymentSources"
                subscriberId = email
            }

            assert(sources.isEmpty()) { "Expected no payment source for profile $email" }

            assertNotNull(StripePayment.getCustomerIdForEmail(email)) { "Customer Id should have been created" }

        } finally {
            StripePayment.deleteCustomer(email = email)
        }
    }

    @Test
    fun `jersey test - PUT source set default`() {

        val email = "purchase-${randomInt()}@test.com"
        try {
            createProfile(name = "Test Payment Source", email = email)

            val tokenId = StripePayment.createPaymentTokenId()
            val cardId = StripePayment.getCardIdForTokenId(tokenId)

            // Ties source with user profile both local and with Stripe
            post<PaymentSource> {
                path = "/paymentSources"
                subscriberId = email
                queryParams = mapOf("sourceId" to tokenId)
            }

            Thread.sleep(200)

            val newTokenId = StripePayment.createPaymentTokenId()
            val newCardId = StripePayment.getCardIdForTokenId(newTokenId)

            post<PaymentSource> {
                path = "/paymentSources"
                subscriberId = email
                queryParams = mapOf("sourceId" to newTokenId)
            }

            // TODO: Update to fetch the Stripe customerId from 'admin' API when ready.
            val customerId = StripePayment.getCustomerIdForEmail(email)

            // Verify that original 'sourceId/card' is default.
            assertEquals(cardId, StripePayment.getDefaultSourceForCustomer(customerId),
                    "Expected $cardId to be default source for $customerId")

            // Set new default card.
            put<PaymentSource> {
                path = "/paymentSources"
                subscriberId = email
                queryParams = mapOf("sourceId" to newCardId)
            }

            assertEquals(newCardId, StripePayment.getDefaultSourceForCustomer(customerId),
                    "Expected $newCardId to be default source for $customerId")
        } finally {
            StripePayment.deleteCustomer(email = email)
        }
    }

    @Test
    fun `jersey test - DELETE source`() {

        val email = "purchase-${randomInt()}@test.com"

        try {

            createProfile(name = "Test Payment Source", email = email)

            Thread.sleep(200)

            val createdIds = listOf(getCardIdForTokenFromStripe(createTokenWithStripe(email)),
                    createSourceWithStripe(email))

            val deletedIds = createdIds.map { it -> removeSourceWithStripe(email, it) }

            assert(createdIds.containsAll(deletedIds.toSet())) {
                "Failed to delete one or more sources: ${createdIds.toSet() - deletedIds.toSet()}"
            }
        } finally {
            StripePayment.deleteCustomer(email = email)
        }
    }

    // Helpers for source handling with Stripe.

    private fun getCardIdForTokenFromStripe(id: String): String {
        if (id.startsWith("tok_")) {
            return StripePayment.getCardIdForTokenId(id)
        }
        return id
    }

    private fun createTokenWithStripe(email: String): String {
        val tokenId = StripePayment.createPaymentTokenId()

        post<PaymentSource> {
            path = "/paymentSources"
            subscriberId = email
            queryParams = mapOf("sourceId" to tokenId)
        }

        return tokenId
    }

    private fun createSourceWithStripe(email: String): String {
        val sourceId = StripePayment.createPaymentSourceId()

        post<PaymentSource> {
            path = "/paymentSources"
            subscriberId = email
            queryParams = mapOf("sourceId" to sourceId)
        }

        return sourceId
    }

    private fun removeSourceWithStripe(email: String, sourceId: String): String {
        val removedSource = delete<PaymentSource> {
            path = "/paymentSources"
            subscriberId = email
            queryParams = mapOf("sourceId" to sourceId)
        }

        return removedSource.id
    }
}

class PurchaseTest {

    @Test
    fun `jersey test - POST products purchase`() {

        val email = "purchase-${randomInt()}@test.com"
        try {
            createProfile(name = "Test Purchase User", email = email)

            val balanceBefore = get<List<Bundle>> {
                path = "/bundles"
                subscriberId = email
            }.first().balance

            val productSku = "1GB_249NOK"
            val sourceId = StripePayment.createPaymentTokenId()

            post<String> {
                path = "/products/$productSku/purchase"
                subscriberId = email
                queryParams = mapOf("sourceId" to sourceId)
            }

            Thread.sleep(100) // wait for 100 ms for balance to be updated in db

            val balanceAfter = get<List<Bundle>> {
                path = "/bundles"
                subscriberId = email
            }.first().balance

            assertEquals(1_000_000_000, balanceAfter - balanceBefore, "Balance did not increased by 1GB after Purchase")

            val purchaseRecords: PurchaseRecordList = get {
                path = "/purchases"
                subscriberId = email
            }

            purchaseRecords.sortBy { it.timestamp }

            assert(Instant.now().toEpochMilli() - purchaseRecords.last().timestamp < 10_000) { "Missing Purchase Record" }
            assertEquals(expectedProducts().first(), purchaseRecords.last().product, "Incorrect 'Product' in purchase record")
        } finally {
            StripePayment.deleteCustomer(email = email)
        }
    }

    @Test
    fun `jersey test - POST products purchase using default source`() {

        val email = "purchase-${randomInt()}@test.com"
        try {
            createProfile(name = "Test Purchase User with Default Payment Source", email = email)

            val sourceId = StripePayment.createPaymentTokenId()

            val paymentSource: PaymentSource = post {
                path = "/paymentSources"
                subscriberId = email
                queryParams = mapOf("sourceId" to sourceId)
            }

            assertNotNull(paymentSource.id, message = "Failed to create payment source")

            val balanceBefore = get<List<Bundle>> {
                path = "/bundles"
                subscriberId = email
            }.first().balance

            val productSku = "1GB_249NOK"

            post<String> {
                path = "/products/$productSku/purchase"
                subscriberId = email
            }

            Thread.sleep(100) // wait for 100 ms for balance to be updated in db

            val balanceAfter = get<List<Bundle>> {
                path = "/bundles"
                subscriberId = email
            }.first().balance

            assertEquals(1_000_000_000, balanceAfter - balanceBefore, "Balance did not increased by 1GB after Purchase")

            val purchaseRecords: PurchaseRecordList = get {
                path = "/purchases"
                subscriberId = email
            }

            purchaseRecords.sortBy { it.timestamp }

            assert(Instant.now().toEpochMilli() - purchaseRecords.last().timestamp < 10_000) { "Missing Purchase Record" }
            assertEquals(expectedProducts().first(), purchaseRecords.last().product, "Incorrect 'Product' in purchase record")
        } finally {
            StripePayment.deleteCustomer(email = email)
        }
    }

    @Test
    fun `jersey test - POST products purchase add source then pay with it`() {

        val email = "purchase-${randomInt()}@test.com"
        try {
            createProfile(name = "Test Purchase User with Default Payment Source", email = email)

            val sourceId = StripePayment.createPaymentTokenId()

            val paymentSource: PaymentSource = post {
                path = "/paymentSources"
                subscriberId = email
                queryParams = mapOf("sourceId" to sourceId)
            }

            assertNotNull(paymentSource.id, message = "Failed to create payment source")

            val bundlesBefore: BundleList = get {
                path = "/bundles"
                subscriberId = email
            }
            val balanceBefore = bundlesBefore[0].balance

            val productSku = "1GB_249NOK"

            post<String> {
                path = "/products/$productSku/purchase"
                subscriberId = email
                queryParams = mapOf("sourceId" to paymentSource.id)
            }

            Thread.sleep(100) // wait for 100 ms for balance to be updated in db

            val bundlesAfter: BundleList = get {
                path = "/bundles"
                subscriberId = email
            }
            val balanceAfter = bundlesAfter[0].balance

            assertEquals(1_000_000_000, balanceAfter - balanceBefore, "Balance did not increased by 1GB after Purchase")

            val purchaseRecords: PurchaseRecordList = get {
                path = "/purchases"
                subscriberId = email
            }

            purchaseRecords.sortBy { it.timestamp }

            assert(Instant.now().toEpochMilli() - purchaseRecords.last().timestamp < 10_000) { "Missing Purchase Record" }
            assertEquals(expectedProducts().first(), purchaseRecords.last().product, "Incorrect 'Product' in purchase record")
        } finally {
            StripePayment.deleteCustomer(email = email)
        }
    }
}

class AnalyticsTest {

    @Test
    fun testReportEvent() {

        val email = "analytics-${randomInt()}@test.com"
        createProfile(name = "Test Analytics User", email = email)

        post<String> {
            path = "/analytics"
            body = "event"
            subscriberId = email
        }
    }
}

class ConsentTest {

    private val consentId = "privacy"

    @Test
    fun `jersey test - GET and PUT consent`() {

        val email = "consent-${randomInt()}@test.com"
        createProfile(name = "Test Consent User", email = email)

        val defaultConsent: List<Consent> = get {
            path = "/consents"
            subscriberId = email
        }

        assertEquals(1, defaultConsent.size, "Incorrect number of consents fetched")
        assertEquals(consentId, defaultConsent[0].consentId, "Incorrect 'consent id' in fetched consent")

        val acceptedConsent: Consent = put {
            path = "/consents/$consentId"
            subscriberId = email
        }

        assertEquals(consentId, acceptedConsent.consentId, "Incorrect 'consent id' in response after accepting consent")
        assertTrue(acceptedConsent.isAccepted
                ?: false, "Accepted consent not reflected in response after accepting consent")

        val rejectedConsent: Consent = put {
            path = "/consents/$consentId"
            queryParams = mapOf("accepted" to "false")
            subscriberId = email
        }

        assertEquals(consentId, rejectedConsent.consentId, "Incorrect 'consent id' in response after rejecting consent")
        assertFalse(rejectedConsent.isAccepted
                ?: true, "Accepted consent not reflected in response after rejecting consent")
    }
}

class ReferralTest {

    @Test
    fun `jersey test - POST profile with invalid referred by`() {

        val email = "referred_by_invalid-${randomInt()}@test.com"

        val invalid = "invalid_referrer@test.com"

        val profile = Profile()
                .email(email)
                .name("Test Referral Second User")
                .address("")
                .city("")
                .country("")
                .postCode("")
                .referralId("")

        val failedToCreate = assertFails {
            post<Profile> {
                path = "/profile"
                body = profile
                subscriberId = email
                queryParams = mapOf("referred_by" to invalid)
            }
        }

        assertEquals("""
{"description":"Incomplete profile description. Subscriber - $invalid not found."} expected:<201> but was:<403>
        """.trimIndent(), failedToCreate.message)

        val failedToGet = assertFails {
            get<Profile> {
                path = "/profile"
                subscriberId = email
            }
        }

        assertEquals("""
{"description":"Incomplete profile description. Subscriber - $email not found."} expected:<200> but was:<404>
        """.trimIndent(), failedToGet.message)
    }

    @Test
    fun `jersey test - POST profile`() {

        val firstEmail = "referral_first-${randomInt()}@test.com"
        createProfile(name = "Test Referral First User", email = firstEmail)

        val secondEmail = "referral_second-${randomInt()}@test.com"

        val profile = Profile()
                .email(secondEmail)
                .name("Test Referral Second User")
                .address("")
                .city("")
                .country("")
                .postCode("")
                .referralId("")

        post<Profile> {
            path = "/profile"
            body = profile
            subscriberId = secondEmail
            queryParams = mapOf("referred_by" to firstEmail)
        }

        // for first
        val referralsForFirst: List<Person> = get {
            path = "/referred"
            subscriberId = firstEmail
        }
        assertEquals(listOf("Test Referral Second User"), referralsForFirst.map { it.name })

        val referredByForFirst: Person = get {
            path = "/referred/by"
            subscriberId = firstEmail
        }
        assertNull(referredByForFirst.name)

        // No need to test SubscriptionStatus for first, since it is already tested in GetSubscriptionStatusTest.

        // for referred_by_foo
        val referralsForSecond: List<Person> = get {
            path = "/referred"
            subscriberId = secondEmail
        }
        assertEquals(emptyList(), referralsForSecond.map { it.name })

        val referredByForSecond: Person = get {
            path = "/referred/by"
            subscriberId = secondEmail
        }
        assertEquals("Test Referral First User", referredByForSecond.name)

        val secondSubscriberBundles: BundleList = get {
            path = "/bundles"
            subscriberId = secondEmail
        }

        assertEquals(1_000_000_000, secondSubscriberBundles[0].balance)

        val secondSubscriberPurchases: PurchaseRecordList = get {
            path = "/purchases"
            subscriberId = secondEmail
        }

        val freeProductForReferred = Product()
                .sku("1GB_FREE_ON_REFERRED")
                .price(Price().apply {
                    this.amount = 0
                    this.currency = "NOK"
                })
                .properties(mapOf("noOfBytes" to "1_000_000_000"))
                .presentation(emptyMap<String, String>())

        assertEquals(listOf(freeProductForReferred), secondSubscriberPurchases.map { it.product })
    }
}

class GraphQlTests {

    data class Subscriber(
            val profile: Profile? = null,
            val bundles: Collection<Bundle>? = null,
            val subscriptions: Collection<Subscription>? = null,
            val products: Collection<Product>? = null,
            val purchases: Collection<PurchaseRecord>? = null)

    data class Data(var subscriber: Subscriber? = null)

    data class GraphQlResponse(var data: Data? = null)

    @Test
    fun `jersey test - POST graphql`() {

        val email = "graphql-${randomInt()}@test.com"
        createProfile("Test GraphQL Endpoint", email)

        val msisdn = createSubscription(email)

        val subscriber = post<GraphQlResponse>(expectedResultCode = 200) {
            path = "/graphql"
            subscriberId = email
            body = mapOf("query" to """{ subscriber { profile { email } subscriptions { msisdn } } }""")
        }.data?.subscriber

        assertEquals(expected = email, actual = subscriber?.profile?.email)
        assertEquals(expected = msisdn, actual = subscriber?.subscriptions?.first()?.msisdn )
    }

    @Test
    fun `jersey test - GET graphql`() {

        val email = "graphql-${randomInt()}@test.com"
        createProfile("Test GraphQL Endpoint", email)

        val msisdn = createSubscription(email)

        val subscriber = get<GraphQlResponse> {
            path = "/graphql"
            subscriberId = email
            queryParams = mapOf("query" to URLEncoder.encode("""{subscriber{profile{email}subscriptions{msisdn}}}""", StandardCharsets.UTF_8.name()))
        }.data?.subscriber

        assertEquals(expected = email, actual = subscriber?.profile?.email)
        assertEquals(expected = msisdn, actual = subscriber?.subscriptions?.first()?.msisdn )
    }
}