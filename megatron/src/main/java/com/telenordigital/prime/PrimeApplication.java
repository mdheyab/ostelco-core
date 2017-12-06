package com.telenordigital.prime;

import com.telenordigital.prime.config.PrimeConfiguration;
import com.telenordigital.prime.disruptor.ClearingEventHandler;
import com.telenordigital.prime.disruptor.PrimeDisruptor;
import com.telenordigital.prime.disruptor.PrimeEventProducer;
import com.telenordigital.prime.events.EventProcessor;
import com.telenordigital.prime.events.OcsBalanceUpdater;
import com.telenordigital.prime.events.Storage;
import com.telenordigital.prime.firebase.FbStorage;
import com.telenordigital.prime.ocs.OcsServer;
import com.telenordigital.prime.ocs.OcsService;
import com.telenordigital.prime.ocs.state.OcsState;
import io.dropwizard.Application;
import io.dropwizard.setup.Environment;
import com.telenordigital.prime.events.OcsBalanceUpdaterImpl;

/**
 * @author Vihang Patil <vihang.patil@telenordigital.com>
 */
public class PrimeApplication extends Application<PrimeConfiguration> {

    public static void main(final String[] args) throws Exception {
        new PrimeApplication().run(args);
    }

    @Override
    public void run(
            final PrimeConfiguration primeConfiguration,
            final Environment environment) throws Exception {

        final PrimeDisruptor disruptor = new PrimeDisruptor();
        // Disruptor provides RingBuffer, which is used by Producer
        final PrimeEventProducer producer = new PrimeEventProducer(disruptor.getDisruptor().getRingBuffer());
        // OcsService uses Producer to produce events for incoming requests from PGw
        final OcsService ocsService = new OcsService(producer);
        // OcsServer assigns OcsService as handler for gRPC requests
        final OcsServer server = new OcsServer(8082, ocsService);

        final OcsState ocsState = new OcsState();

        final Storage storage = new FbStorage(
                primeConfiguration.getEventProcessorConfig().getDatabaseName(),
                primeConfiguration.getEventProcessorConfig().getConfigFile(),
                ocsState);

        final OcsBalanceUpdater ocsBalanceUpdater = new OcsBalanceUpdaterImpl(producer);
        final EventProcessor eventProcessor = new EventProcessor(storage, ocsBalanceUpdater);


        // Events flow:
        //      Producer:(OcsService, Subscriber)
        //          -> Handler:(OcsState)
        //              -> Handler:(OcsService, Subscriber)
        //                  -> Clear
        disruptor.getDisruptor()
                .handleEventsWith(ocsState)
                .then(ocsService, eventProcessor)
                .then(new ClearingEventHandler());

        // dropwizard starts event processor
         environment.lifecycle().manage(eventProcessor);

        // dropwizard starts disruptor
        environment.lifecycle().manage(disruptor);
        // dropwizard starts server
        environment.lifecycle().manage(server);
    }
}
