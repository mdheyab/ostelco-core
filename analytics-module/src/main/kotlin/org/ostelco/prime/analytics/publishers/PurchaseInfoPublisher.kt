package org.ostelco.prime.analytics.publishers

import com.google.api.core.ApiFutureCallback
import com.google.api.core.ApiFutures
import com.google.api.gax.rpc.ApiException
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import com.google.protobuf.ByteString
import com.google.pubsub.v1.PubsubMessage
import org.ostelco.prime.analytics.ConfigRegistry
import org.ostelco.prime.logger
import org.ostelco.prime.model.PurchaseRecord
import org.ostelco.prime.model.PurchaseRecordInfo
import org.ostelco.prime.module.getResource
import org.ostelco.prime.pseudonymizer.PseudonymizerService
import java.net.URLEncoder


/**
 * This class publishes the purchase information events to the Google Cloud Pub/Sub.
 */
object PurchaseInfoPublisher :
        PubSubPublisher by DelegatePubSubPublisher(topicId = ConfigRegistry.config.purchaseInfoTopicId) {

    private val logger by logger()

    private val pseudonymizerService by lazy { getResource<PseudonymizerService>() }

    private var gson: Gson = createGson()

    private fun createGson(): Gson {
        val builder = GsonBuilder()
        // Type for this conversion is explicitly set to java.util.Map
        // This is needed because of kotlin's own Map interface
        val mapType = object : TypeToken<java.util.Map<String, String>>() {}.type
        val serializer = JsonSerializer<java.util.Map<String, String>> { src, _, _ ->
            val array = JsonArray()
            src.forEach { k, v ->
                val property = JsonObject()
                property.addProperty("key", k)
                property.addProperty("value", v)
                array.add(property)
            }
            array
        }
        builder.registerTypeAdapter(mapType, serializer)
        return builder.create()
    }

    private fun convertToJson(purchaseRecordInfo: PurchaseRecordInfo): ByteString =
            ByteString.copyFromUtf8(gson.toJson(purchaseRecordInfo))


    fun publish(purchaseRecord: PurchaseRecord, subscriberId: String, status: String) {

        val encodedSubscriberId = URLEncoder.encode(subscriberId, "UTF-8")
        val pseudonym = pseudonymizerService.getSubscriberIdPseudonym(encodedSubscriberId, purchaseRecord.timestamp).pseudonym

        val pubsubMessage = PubsubMessage.newBuilder()
                .setData(convertToJson(PurchaseRecordInfo(purchaseRecord, pseudonym, status)))
                .build()

        //schedule a message to be published, messages are automatically batched
        val future = publishPubSubMessage(pubsubMessage)

        // add an asynchronous callback to handle success / failure
        ApiFutures.addCallback(future, object : ApiFutureCallback<String> {

            override fun onFailure(throwable: Throwable) {
                if (throwable is ApiException) {
                    // details on the API exception
                    logger.warn("Status code: {}", throwable.statusCode.code)
                    logger.warn("Retrying: {}", throwable.isRetryable)
                }
                logger.warn("Error publishing purchase record for msisdn: {}", purchaseRecord.msisdn)
            }

            override fun onSuccess(messageId: String) {
                // Once published, returns server-assigned message ids (unique within the topic)
                logger.debug(messageId)
            }
        }, singleThreadScheduledExecutor)
    }
}
