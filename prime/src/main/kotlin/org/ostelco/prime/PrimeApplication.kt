package org.ostelco.prime

import io.dropwizard.Application
import io.dropwizard.setup.Environment
import org.ostelco.prime.analytics.DataConsumptionInfoPublisher
import org.ostelco.prime.config.PrimeConfiguration
import org.ostelco.prime.disruptor.ClearingEventHandler
import org.ostelco.prime.disruptor.PrimeDisruptor
import org.ostelco.prime.disruptor.PrimeEventProducerImpl
import org.ostelco.prime.events.EventProcessor
import org.ostelco.prime.events.OcsBalanceUpdaterImpl
import org.ostelco.prime.ocs.OcsServer
import org.ostelco.prime.ocs.OcsService
import org.ostelco.prime.ocs.OcsState

class PrimeApplication : Application<PrimeConfiguration>() {

    private val LOG by logger()

    @Throws(Exception::class)
    override fun run(
            primeConfiguration: PrimeConfiguration,
            environment: Environment) {

        primeConfiguration.services.forEach { it.init(environment) }

        val disruptor = PrimeDisruptor()

        // Disruptor provides RingBuffer, which is used by Producer
        val producer = PrimeEventProducerImpl(disruptor.disruptor.ringBuffer)

        // OcsService uses Producer to produce events for incoming requests from P-GW
        val ocsService = OcsService(producer)

        // OcsServer assigns OcsService as handler for gRPC requests
        val server = OcsServer(8082, ocsService.asOcsServiceImplBase())

        val ocsState = OcsState()

        val eventProcessorConfig = primeConfiguration.eventProcessorConfig

        val ocsBalanceUpdater = OcsBalanceUpdaterImpl(producer)
        val eventProcessor = EventProcessor(ocsBalanceUpdater)

        val dataConsumptionInfoPublisher = DataConsumptionInfoPublisher(
                eventProcessorConfig.projectId,
                eventProcessorConfig.topicId)

        // Events flow:
        //      Producer:(OcsService, Subscriber)
        //          -> Handler:(OcsState)
        //              -> Handler:(OcsService, Subscriber, AnalyticsPublisher)
        //                  -> Clear

        disruptor.disruptor
                .handleEventsWith(ocsState)
                .then(ocsService.asEventHandler(), eventProcessor, dataConsumptionInfoPublisher)
                .then(ClearingEventHandler())

        // dropwizard starts Analytics events publisher
        environment.lifecycle().manage(dataConsumptionInfoPublisher)
        // dropwizard starts event processor
        environment.lifecycle().manage(eventProcessor)
        // dropwizard starts disruptor
        environment.lifecycle().manage(disruptor)
        // dropwizard starts server
        environment.lifecycle().manage(server)
    }

    companion object {

        @Throws(Exception::class)
        @JvmStatic
        fun main(args: Array<String>) {
            PrimeApplication().run(*args)
        }
    }
}
