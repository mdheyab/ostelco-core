package org.ostelco.ocsgw.data.grpc;

import com.google.auth.oauth2.ServiceAccountJwtAccessCredentials;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.auth.MoreCallCredentials;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.ostelco.prime.metrics.api.OcsgwAnalyticsReply;
import org.ostelco.prime.metrics.api.OcsgwAnalyticsReport;
import org.ostelco.prime.metrics.api.OcsgwAnalyticsServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

class OcsgwMetrics {

    private static final Logger LOG = LoggerFactory.getLogger(OcsgwMetrics.class);

    private static final int KEEP_ALIVE_TIMEOUT_IN_MINUTES = 1;

    private static final int KEEP_ALIVE_TIME_IN_SECONDS = 50;

    private OcsgwAnalyticsServiceGrpc.OcsgwAnalyticsServiceStub ocsgwAnalyticsServiceStub;

    private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    private StreamObserver<OcsgwAnalyticsReport> ocsgwAnalyticsReport;

    private ScheduledFuture initAnalyticsFuture = null;

    private ScheduledFuture keepAliveFuture = null;

    private ScheduledFuture autoReportAnalyticsFuture = null;

    private OcsgwAnalyticsReport lastActiveSessions = null;

    OcsgwMetrics(String metricsServerHostname, ServiceAccountJwtAccessCredentials credentials) {

        try {
            final NettyChannelBuilder nettyChannelBuilder = NettyChannelBuilder
                    .forTarget(metricsServerHostname)
                    .keepAliveWithoutCalls(true)
                    .keepAliveTimeout(KEEP_ALIVE_TIMEOUT_IN_MINUTES, TimeUnit.MINUTES)
                    .keepAliveTime(KEEP_ALIVE_TIME_IN_SECONDS, TimeUnit.SECONDS);

            final ManagedChannelBuilder channelBuilder = Files.exists(Paths.get("/config/metrics.crt"))
                        ? nettyChannelBuilder.sslContext(GrpcSslContexts.forClient().trustManager(new File("/config/metrics.crt")).build())
                        : nettyChannelBuilder;

            final ManagedChannel channel = channelBuilder
                    .useTransportSecurity()
                    .build();
            if (credentials != null) {
                ocsgwAnalyticsServiceStub  = OcsgwAnalyticsServiceGrpc.newStub(channel)
                        .withCallCredentials(MoreCallCredentials.from(credentials));
            } else {
                ocsgwAnalyticsServiceStub = OcsgwAnalyticsServiceGrpc.newStub(channel);
            }
        } catch (SSLException e) {
            LOG.warn("Failed to setup OcsMetrics", e);
        }
    }

    private abstract class AnalyticsRequestObserver<T> implements StreamObserver<T> {
        public final void onError(Throwable t) {
            LOG.error("AnalyticsRequestObserver error", t);
            if (t instanceof StatusRuntimeException) {
                reconnectAnalyticsReport();
            }
        }

        public final void onCompleted() {
            // Nothing to do here
        }
    }

    private void reconnectKeepAlive() {
        LOG.info("reconnectKeepAlive called");
        if (keepAliveFuture != null) {
            keepAliveFuture.cancel(true);
        }
    }

    private void reconnectAnalyticsReport() {
        LOG.info("reconnectAnalyticsReport called");

        if (initAnalyticsFuture != null) {
            initAnalyticsFuture.cancel(true);
        }

        LOG.info("Schedule new Callable initAnalyticsRequest");
        initAnalyticsFuture = executorService.schedule((Callable<Object>) () -> {
                    reconnectKeepAlive();
                    LOG.info("Calling initAnalyticsRequest");
                    initAnalyticsRequest();
                    sendAnalytics(lastActiveSessions);
                    return "Called!";
                },
                5,
                TimeUnit.SECONDS);
    }

    private void initAutoReportAnalyticsReport() {
        autoReportAnalyticsFuture = executorService.scheduleAtFixedRate((Runnable) () -> {
                    sendAnalytics(lastActiveSessions);
                },
                30,
                30,
                TimeUnit.MINUTES);
    }

    void initAnalyticsRequest() {
        ocsgwAnalyticsReport = ocsgwAnalyticsServiceStub.ocsgwAnalyticsEvent(
                new AnalyticsRequestObserver<OcsgwAnalyticsReply>() {

                    @Override
                    public void onNext(OcsgwAnalyticsReply value) {
                        // Ignore reply from Prime
                    }
                }
        );
        initKeepAlive();
        initAutoReportAnalyticsReport();
    }

    private void initKeepAlive() {
        // this is used to keep connection alive
        keepAliveFuture = executorService.scheduleWithFixedDelay(() -> {
                    sendAnalytics(OcsgwAnalyticsReport.newBuilder().setKeepAlive(true).build());
                },
                15,
                50,
                TimeUnit.SECONDS);
    }

    void sendAnalytics(OcsgwAnalyticsReport report) {
        if (report != null) {
            ocsgwAnalyticsReport.onNext(report);
            lastActiveSessions = report;
        }
    }
}