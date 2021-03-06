package com.couchbase.lite;

import com.couchbase.lite.replicator.Replication;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class DatabaseTest extends LiteTestCase {

    public void testPruneRevsToMaxDepth() throws Exception {

        Map<String,Object> properties = new HashMap<String,Object>();
        properties.put("testName", "testDatabaseCompaction");
        properties.put("tag", 1337);

        Document doc=createDocumentWithProperties(database, properties);
        SavedRevision rev = doc.getCurrentRevision();

        database.setMaxRevTreeDepth(1);
        for (int i=0; i<10; i++) {
            Map<String,Object> properties2 = new HashMap<String,Object>(properties);
            properties2.put("tag", i);
            rev = rev.createRevision(properties2);
        }

        int numPruned = database.pruneRevsToMaxDepth(1);
        assertEquals(9, numPruned);

        Document fetchedDoc = database.getDocument(doc.getId());
        List<SavedRevision> revisions = fetchedDoc.getRevisionHistory();
        assertEquals(1, revisions.size());

        numPruned = database.pruneRevsToMaxDepth(1);
        assertEquals(0, numPruned);

    }

    public void testPruneRevsToMaxDepthViaCompact() throws Exception {

        Map<String,Object> properties = new HashMap<String,Object>();
        properties.put("testName", "testDatabaseCompaction");
        properties.put("tag", 1337);

        Document doc=createDocumentWithProperties(database, properties);
        SavedRevision rev = doc.getCurrentRevision();

        database.setMaxRevTreeDepth(1);
        for (int i=0; i<10; i++) {
            Map<String,Object> properties2 = new HashMap<String,Object>(properties);
            properties2.put("tag", i);
            rev = rev.createRevision(properties2);
        }

        database.compact();

        Document fetchedDoc = database.getDocument(doc.getId());
        List<SavedRevision> revisions = fetchedDoc.getRevisionHistory();
        assertEquals(1, revisions.size());

    }

    /**
     * When making inserts in a transaction, the change notifications should
     * be batched into a single change notification (rather than a change notification
     * for each insert)
     */
    public void testChangeListenerNotificationBatching() throws Exception {

        final int numDocs = 50;
        final AtomicInteger atomicInteger = new AtomicInteger(0);
        final CountDownLatch countDownLatch = new CountDownLatch(1);

        database.addChangeListener(new Database.ChangeListener() {
            @Override
            public void changed(Database.ChangeEvent event) {
                atomicInteger.incrementAndGet();
            }
        });

        database.runInTransaction(new TransactionalTask() {
            @Override
            public boolean run() {
                createDocuments(database, numDocs);
                countDownLatch.countDown();
                return true;
            }
        });

        boolean success = countDownLatch.await(30, TimeUnit.SECONDS);
        assertTrue(success);

        assertEquals(1, atomicInteger.get());


    }

    /**
     * When making inserts outside of a transaction, there should be a change notification
     * for each insert (no batching)
     */
    public void testChangeListenerNotification() throws Exception {

        final int numDocs = 50;
        final AtomicInteger atomicInteger = new AtomicInteger(0);
        final CountDownLatch countDownLatch = new CountDownLatch(1);

        database.addChangeListener(new Database.ChangeListener() {
            @Override
            public void changed(Database.ChangeEvent event) {
                atomicInteger.incrementAndGet();
            }
        });

        createDocuments(database, numDocs);

        assertEquals(numDocs, atomicInteger.get());


    }

    public void testGetActiveReplications() throws Exception {

        URL remote = getReplicationURL();
        Replication replication = (Replication) database.createPullReplication(remote);

        assertEquals(0, database.getAllReplications().size());
        assertEquals(0, database.getActiveReplications().size());

        replication.start();

        assertEquals(1, database.getAllReplications().size());
        assertEquals(1, database.getActiveReplications().size());

        CountDownLatch replicationDoneSignal = new CountDownLatch(1);
        ReplicationFinishedObserver replicationFinishedObserver = new ReplicationFinishedObserver(replicationDoneSignal);
        replication.addChangeListener(replicationFinishedObserver);

        boolean success = replicationDoneSignal.await(60, TimeUnit.SECONDS);
        assertTrue(success);

        assertEquals(1, database.getAllReplications().size());
        assertEquals(0, database.getActiveReplications().size());

    }

}
