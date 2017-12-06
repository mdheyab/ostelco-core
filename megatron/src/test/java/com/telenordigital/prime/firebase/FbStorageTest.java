package com.telenordigital.prime.firebase;

import com.telenordigital.prime.events.*;
import com.telenordigital.prime.ocs.state.OcsState;
import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


import static com.telenordigital.prime.events.Products.DATA_TOPUP_3GB;
import static java.lang.Thread.sleep;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

public class FbStorageTest {
    private static final Logger LOG = LoggerFactory.getLogger(FbStorageTest.class);

    private final static String PAYMENT_TOKEN = "thisIsAPaymentToken";
    private final static String EPHERMERAL_MSISDN = "+4747116996";

    private FbStorage fbStorage;
    private Storage storage;
    private Collection<String> prids;

    @Before
    public void setUp() throws StorageException, InterruptedException {
        this.fbStorage = new FbStorage(
                "pantel-tests",
                "src/test/resources/pantel-tests.json" ,
                new OcsState());
        this.storage = fbStorage;
        sleep(3000);
        storage.removeSubscriberByMsisdn(EPHERMERAL_MSISDN);
        storage.insertNewSubscriber(EPHERMERAL_MSISDN);
        this.prids = new ArrayList<>();
    }

    @After
    public void cleanUp() throws StorageException {
        storage.removeSubscriberByMsisdn(EPHERMERAL_MSISDN);
        for (final String prid : prids) {
            storage.removePurchaseRequestById(prid);
        }
    }

    @Test
    public void getStorageByMsisdnTest() throws InterruptedException, StorageException {
        final Subscriber subscriberByMsisdn = storage.getSubscriberFromMsisdn(EPHERMERAL_MSISDN);
        assertNotEquals(null, subscriberByMsisdn);
        assertEquals(EPHERMERAL_MSISDN, subscriberByMsisdn.getMsisdn());
    }

    @Test
    public void insertNewSubscriberTest() throws InterruptedException, StorageException {
        assertNotEquals(null, storage.getSubscriberFromMsisdn(EPHERMERAL_MSISDN));
    }

    @Test
    public void setRemainingByMsisdnTest() throws StorageException {
        storage.setRemainingByMsisdn(EPHERMERAL_MSISDN, 92l);
        assertEquals(92l, storage.getSubscriberFromMsisdn(EPHERMERAL_MSISDN).getNoOfBytesLeft());
        storage.setRemainingByMsisdn(EPHERMERAL_MSISDN, 0);
        assertEquals(0l, storage.getSubscriberFromMsisdn(EPHERMERAL_MSISDN).getNoOfBytesLeft());
    }

    @Test
    public void updateDisplayDatastructureTest() throws StorageException {
        storage.setRemainingByMsisdn(EPHERMERAL_MSISDN, 92l);
        storage.updatedisplaydatastructure(EPHERMERAL_MSISDN);
        // XXX  Some verification missing, but it looks like the right thing
    }

    @Test
    public void  addRecordOfPurchaseByMsisdnTest() throws StorageException {
        final long now;
        now = Instant.now().toEpochMilli();
        final String id =
                storage.addRecordOfPurchaseByMsisdn(
                        EPHERMERAL_MSISDN,
                        DATA_TOPUP_3GB.getSku(),
                        now);
        storage.removeRecordOfPurchaseById(id);
    }

    @Test
    public void testWriteThenReactToUpdateRequest() throws InterruptedException {

        final CountDownLatch latch = new CountDownLatch(2);

        storage.addPurchaseRequestListener(new PurchaseRequestListener() {
            @Override
            public void onPurchaseRequest(final PurchaseRequest req) {
                assertNotEquals(null, req);
                assertEquals(PAYMENT_TOKEN, req.getPaymentToken());
                assertEquals(DATA_TOPUP_3GB.getSku(), req.getSku());
                latch.countDown();
            }
        });

        final FbPurchaseRequest cr =
                new FbPurchaseRequest(DATA_TOPUP_3GB, PAYMENT_TOKEN);
        final String id = fbStorage.injectPurchaseRequest(cr);
        final String id2 = fbStorage.injectPurchaseRequest(cr);
        prids.add(id);
        prids.add(id2);

        if (!latch.await(10, TimeUnit.SECONDS)) {
            fail("Read/react failed");
        }

        storage.removePurchaseRequestById(id);
        storage.removePurchaseRequestById(id2);
    }
}
