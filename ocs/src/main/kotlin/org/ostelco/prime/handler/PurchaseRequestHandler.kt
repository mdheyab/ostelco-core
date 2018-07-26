package org.ostelco.prime.handler

import org.ostelco.prime.disruptor.PrimeEventProducer
import org.ostelco.prime.events.EventProcessorException
import org.ostelco.prime.logger
import org.ostelco.prime.module.getResource
import org.ostelco.prime.storage.ClientGraphStore

class PurchaseRequestHandler(
        private val producer: PrimeEventProducer,
        private val storage: ClientGraphStore = getResource()) {

    private val logger by logger()

    @Throws(EventProcessorException::class)
    fun handlePurchaseRequest(
            msisdn: String,
            productSku: String) {

        logger.info("Handling purchase request - msisdn: {} sku = {}", msisdn, productSku)

        // get Product by SKU
        val product = storage.getProduct("id", productSku) ?: throw EventProcessorException("Not a valid SKU: $productSku")

        val noOfBytes = product.properties["noOfBytes"]?.toLong()

        if (noOfBytes != null && noOfBytes > 0) {

            logger.info("Handling topup product - msisdn: {} topup: {}", msisdn, noOfBytes)

            producer.topupDataBundleBalanceEvent(msisdn, noOfBytes)
        }
    }
}
