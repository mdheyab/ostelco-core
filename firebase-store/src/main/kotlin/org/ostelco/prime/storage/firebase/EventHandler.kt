package org.ostelco.prime.storage.firebase

import org.ostelco.prime.model.ProductCatalogItem
import org.ostelco.prime.model.PurchaseRequest
import org.ostelco.prime.storage.legacy.PurchaseRequestHandler
import java.time.Instant

class EventHandler {

    private val executor: StorageInitiatedEventExecutor = StorageInitiatedEventExecutor()

    private val millisSinceEpoch: Long
        get() = Instant.now().toEpochMilli()


    fun purchaseRequestHandler(key: String, req: PurchaseRequest) {
        req.id = key
        req.millisSinceEpoch = millisSinceEpoch
        executor.onPurchaseRequest(req)
        // return null // XXX Hack to satisfy BiFunction's void return type
    }

    fun productCatalogItemHandler(item: ProductCatalogItem) {
        val sku = item.sku
        val noOfBytes = item.noOfBytes
        if (sku != null && noOfBytes != null) {
            ProductDescriptionCacheImpl.addTopupProduct(sku, noOfBytes)
        }
    }

    fun addPurchaseRequestHandler(handler: PurchaseRequestHandler) {
        executor.addPurchaseRequestHandler(handler)
    }
}
