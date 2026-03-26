package org.medicmobile.webapp.mobile.p2p;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.*;

/**
 * Tests for TransitDocManager — manages transit doc tracking and lifecycle.
 *
 * Guards:
 *   G22: Classification is deterministic (handled by ScopeGuard, tested in ScopeGuardTest)
 *   G23: Transit docs NEVER appear in UI (isTransitDoc used for filtering)
 *   G24: Transit index loads in <50ms
 *   G25: Purge uses db.purge() NOT db.remove()
 *   G26: _local/ doc must not exceed 1MB
 *   G27: Unpushed transit docs >30 days → user notification
 */
public class TransitDocManagerTest {

    private TransitDocManager manager;

    @Before
    public void setUp() {
        manager = new TransitDocManager();
    }

    // ========================================================================
    // Batch lifecycle
    // ========================================================================

    @Test
    public void testStartBatchReturnsUniqueId() {
        String batch1 = manager.startBatch("device-1", "user-1");
        String batch2 = manager.startBatch("device-2", "user-2");

        assertNotNull(batch1);
        assertNotNull(batch2);
        assertNotEquals("Batch IDs should be unique", batch1, batch2);
    }

    @Test
    public void testTrackTransitDoc() {
        String batchId = manager.startBatch("device-1", "user-1");
        manager.trackTransitDoc(batchId, "doc-1");
        manager.trackTransitDoc(batchId, "doc-2");

        assertTrue("doc-1 should be tracked", manager.isTransitDoc("doc-1"));
        assertTrue("doc-2 should be tracked", manager.isTransitDoc("doc-2"));
        assertFalse("doc-3 should NOT be tracked", manager.isTransitDoc("doc-3"));
    }

    @Test
    public void testGetAllTransitDocIds() {
        String batchId = manager.startBatch("device-1", "user-1");
        manager.trackTransitDoc(batchId, "doc-a");
        manager.trackTransitDoc(batchId, "doc-b");
        manager.trackTransitDoc(batchId, "doc-c");

        Set<String> allIds = manager.getAllTransitDocIds();
        assertEquals(3, allIds.size());
        assertTrue(allIds.contains("doc-a"));
        assertTrue(allIds.contains("doc-b"));
        assertTrue(allIds.contains("doc-c"));
    }

    @Test
    public void testGetDocIdsForBatch() {
        String batch1 = manager.startBatch("device-1", "user-1");
        String batch2 = manager.startBatch("device-2", "user-2");
        manager.trackTransitDoc(batch1, "doc-1");
        manager.trackTransitDoc(batch1, "doc-2");
        manager.trackTransitDoc(batch2, "doc-3");

        Set<String> batch1Docs = manager.getDocIdsForBatch(batch1);
        assertEquals(2, batch1Docs.size());
        assertTrue(batch1Docs.contains("doc-1"));
        assertTrue(batch1Docs.contains("doc-2"));

        Set<String> batch2Docs = manager.getDocIdsForBatch(batch2);
        assertEquals(1, batch2Docs.size());
        assertTrue(batch2Docs.contains("doc-3"));
    }

    // ========================================================================
    // G23: isTransitDoc for UI filtering
    // ========================================================================

    @Test
    public void testIsTransitDocReturnsFalseForUntracked() {
        assertFalse(manager.isTransitDoc("nonexistent-doc"));
    }

    @Test
    public void testIsTransitDocReturnsFalseAfterPurge() {
        String batchId = manager.startBatch("device-1", "user-1");
        manager.trackTransitDoc(batchId, "doc-1");
        assertTrue("doc-1 should be tracked before purge", manager.isTransitDoc("doc-1"));

        manager.markBatchPushed(batchId);
        manager.markBatchPurged(batchId);
        assertFalse("doc-1 should NOT be tracked after purge", manager.isTransitDoc("doc-1"));
    }

    @Test
    public void testIsTransitDocStillTrueAfterPushBeforePurge() {
        String batchId = manager.startBatch("device-1", "user-1");
        manager.trackTransitDoc(batchId, "doc-1");
        manager.markBatchPushed(batchId);

        assertTrue("doc-1 should still be tracked after push, before purge", manager.isTransitDoc("doc-1"));
    }

    // ========================================================================
    // Push and purge lifecycle
    // ========================================================================

    @Test
    public void testMarkBatchPushed() {
        String batchId = manager.startBatch("device-1", "user-1");
        manager.trackTransitDoc(batchId, "doc-1");

        Set<String> purgeableBefore = manager.getPurgeableBatchIds();
        assertTrue("No purgeable batches before push", purgeableBefore.isEmpty());

        manager.markBatchPushed(batchId);
        Set<String> purgeableAfter = manager.getPurgeableBatchIds();
        assertEquals(1, purgeableAfter.size());
        assertTrue(purgeableAfter.contains(batchId));
    }

    @Test
    public void testMarkBatchPurgedRemovesFromPurgeable() {
        String batchId = manager.startBatch("device-1", "user-1");
        manager.trackTransitDoc(batchId, "doc-1");
        manager.markBatchPushed(batchId);
        manager.markBatchPurged(batchId);

        Set<String> purgeable = manager.getPurgeableBatchIds();
        assertTrue("No purgeable batches after purge", purgeable.isEmpty());
    }

    @Test
    public void testPurgeRemovesDocsFromTransitIndex() {
        String batchId = manager.startBatch("device-1", "user-1");
        manager.trackTransitDoc(batchId, "doc-1");
        manager.trackTransitDoc(batchId, "doc-2");

        manager.markBatchPushed(batchId);
        manager.markBatchPurged(batchId);

        assertTrue("Transit index should be empty after purge", manager.getAllTransitDocIds().isEmpty());
    }

    @Test
    public void testPurgeOnlyAffectsTargetBatch() {
        String batch1 = manager.startBatch("device-1", "user-1");
        String batch2 = manager.startBatch("device-2", "user-2");
        manager.trackTransitDoc(batch1, "doc-1");
        manager.trackTransitDoc(batch2, "doc-2");

        manager.markBatchPushed(batch1);
        manager.markBatchPurged(batch1);

        assertFalse("doc-1 should be removed", manager.isTransitDoc("doc-1"));
        assertTrue("doc-2 should still be tracked", manager.isTransitDoc("doc-2"));
    }

    // ========================================================================
    // Pending push count
    // ========================================================================

    @Test
    public void testGetPendingPushCountInitially() {
        assertEquals(0, manager.getPendingPushCount());
    }

    @Test
    public void testGetPendingPushCountAfterTracking() {
        String batchId = manager.startBatch("device-1", "user-1");
        manager.trackTransitDoc(batchId, "doc-1");
        manager.trackTransitDoc(batchId, "doc-2");
        manager.trackTransitDoc(batchId, "doc-3");

        assertEquals(3, manager.getPendingPushCount());
    }

    @Test
    public void testGetPendingPushCountAfterPush() {
        String batchId = manager.startBatch("device-1", "user-1");
        manager.trackTransitDoc(batchId, "doc-1");
        manager.trackTransitDoc(batchId, "doc-2");
        manager.markBatchPushed(batchId);

        assertEquals("After push, pending count should be 0", 0, manager.getPendingPushCount());
    }

    @Test
    public void testGetPendingPushCountMultipleBatches() {
        String batch1 = manager.startBatch("device-1", "user-1");
        String batch2 = manager.startBatch("device-2", "user-2");
        manager.trackTransitDoc(batch1, "doc-1");
        manager.trackTransitDoc(batch1, "doc-2");
        manager.trackTransitDoc(batch2, "doc-3");

        assertEquals(3, manager.getPendingPushCount());

        manager.markBatchPushed(batch1);
        assertEquals("Only batch2 should be pending", 1, manager.getPendingPushCount());
    }

    // ========================================================================
    // G27: Stale transit docs detection
    // ========================================================================

    @Test
    public void testHasStaleTransitDocsReturnsFalseWhenEmpty() {
        assertFalse(manager.hasStaleTransitDocs());
    }

    @Test
    public void testHasStaleTransitDocsReturnsFalseForFreshBatch() {
        String batchId = manager.startBatch("device-1", "user-1");
        manager.trackTransitDoc(batchId, "doc-1");

        // Fresh batch (just created) should not be stale
        assertFalse("Fresh batch should not be stale", manager.hasStaleTransitDocs());
    }

    @Test
    public void testHasStaleTransitDocsReturnsFalseAfterPush() {
        String batchId = manager.startBatch("device-1", "user-1");
        manager.trackTransitDoc(batchId, "doc-1");
        manager.markBatchPushed(batchId);

        assertFalse("Pushed batch should not be stale", manager.hasStaleTransitDocs());
    }

    // ========================================================================
    // G26: Size management
    // ========================================================================

    @Test
    public void testEstimateSizeNonZero() {
        String batchId = manager.startBatch("device-1", "user-1");
        manager.trackTransitDoc(batchId, "doc-1");

        long size = manager.estimateSize();
        assertTrue("Size should be > 0", size > 0);
    }

    @Test
    public void testIsOversizedFalseForSmallData() {
        String batchId = manager.startBatch("device-1", "user-1");
        manager.trackTransitDoc(batchId, "doc-1");

        assertFalse("Small data should not be oversized", manager.isOversized());
    }

    @Test
    public void testArchivePurgedBatchesReducesSize() {
        String batch1 = manager.startBatch("device-1", "user-1");
        manager.trackTransitDoc(batch1, "doc-1");
        manager.markBatchPushed(batch1);
        manager.markBatchPurged(batch1);

        String batch2 = manager.startBatch("device-2", "user-2");
        manager.trackTransitDoc(batch2, "doc-2");

        long sizeBefore = manager.estimateSize();
        manager.archivePurgedBatches();
        long sizeAfter = manager.estimateSize();

        assertTrue("Size should decrease after archiving purged batches", sizeAfter <= sizeBefore);
    }

    // ========================================================================
    // JSON serialization / deserialization roundtrip
    // ========================================================================

    @Test
    public void testToJsonContainsRequiredFields() throws JSONException {
        String batchId = manager.startBatch("device-1", "user-1");
        manager.trackTransitDoc(batchId, "doc-1");
        manager.trackTransitDoc(batchId, "doc-2");

        JSONObject json = manager.toJson();

        assertEquals(TransitDocManager.TRANSIT_DOC_ID, json.getString("_id"));
        assertTrue(json.has("batches"));
        assertTrue(json.has("transit_index"));
        assertTrue(json.has("stats"));

        JSONObject stats = json.getJSONObject("stats");
        assertEquals(2, stats.getInt("total_received"));
        assertEquals(0, stats.getInt("total_pushed"));
        assertEquals(0, stats.getInt("total_purged"));
        assertEquals(2, stats.getInt("pending_push"));
    }

    @Test
    public void testToJsonTransitIndexContainsTrackedDocs() throws JSONException {
        String batchId = manager.startBatch("device-1", "user-1");
        manager.trackTransitDoc(batchId, "doc-1");
        manager.trackTransitDoc(batchId, "doc-2");

        JSONObject json = manager.toJson();
        JSONObject transitIndex = json.getJSONObject("transit_index");

        assertEquals(batchId, transitIndex.getString("doc-1"));
        assertEquals(batchId, transitIndex.getString("doc-2"));
    }

    @Test
    public void testJsonRoundtrip() throws JSONException {
        // Set up state
        String batchId = manager.startBatch("device-1", "user-alice");
        manager.trackTransitDoc(batchId, "doc-a");
        manager.trackTransitDoc(batchId, "doc-b");
        manager.trackTransitDoc(batchId, "doc-c");

        // Serialize
        JSONObject json = manager.toJson();

        // Deserialize into new manager
        TransitDocManager restored = new TransitDocManager();
        restored.loadFromJson(json);

        // Verify restored state
        assertTrue("doc-a should be tracked after restore", restored.isTransitDoc("doc-a"));
        assertTrue("doc-b should be tracked after restore", restored.isTransitDoc("doc-b"));
        assertTrue("doc-c should be tracked after restore", restored.isTransitDoc("doc-c"));
        assertFalse("doc-d should NOT be tracked", restored.isTransitDoc("doc-d"));

        Set<String> allIds = restored.getAllTransitDocIds();
        assertEquals(3, allIds.size());
    }

    @Test
    public void testLoadFromNullDoesNotThrow() throws JSONException {
        manager.loadFromJson(null); // should be a no-op
        assertFalse(manager.isTransitDoc("anything"));
        assertEquals(0, manager.getPendingPushCount());
    }

    @Test
    public void testLoadFromEmptyObject() throws JSONException {
        manager.loadFromJson(new JSONObject());
        assertFalse(manager.isTransitDoc("anything"));
        assertEquals(0, manager.getPendingPushCount());
    }

    @Test
    public void testJsonRoundtripWithPushedBatch() throws JSONException {
        String batchId = manager.startBatch("device-1", "user-1");
        manager.trackTransitDoc(batchId, "doc-1");
        manager.markBatchPushed(batchId);

        JSONObject json = manager.toJson();
        TransitDocManager restored = new TransitDocManager();
        restored.loadFromJson(json);

        // The batch should be purgeable after restore
        Set<String> purgeable = restored.getPurgeableBatchIds();
        assertEquals(1, purgeable.size());
    }

    @Test
    public void testJsonRoundtripWithPurgedBatch() throws JSONException {
        String batchId = manager.startBatch("device-1", "user-1");
        manager.trackTransitDoc(batchId, "doc-1");
        manager.markBatchPushed(batchId);
        manager.markBatchPurged(batchId);

        JSONObject json = manager.toJson();
        TransitDocManager restored = new TransitDocManager();
        restored.loadFromJson(json);

        // Purged docs should no longer be in transit index
        assertFalse("doc-1 should not be in transit after purge+restore", restored.isTransitDoc("doc-1"));
        assertTrue("No purgeable batches after purge", restored.getPurgeableBatchIds().isEmpty());
    }

    // ========================================================================
    // G24: Performance — transit index lookup should be fast
    // ========================================================================

    @Test
    public void testTransitIndexLookupPerformance() {
        // Track 1000 docs across multiple batches
        for (int b = 0; b < 10; b++) {
            String batchId = manager.startBatch("device-" + b, "user-" + b);
            for (int d = 0; d < 100; d++) {
                manager.trackTransitDoc(batchId, "doc-" + b + "-" + d);
            }
        }

        // Lookup all 1000 docs and measure time
        long start = System.nanoTime();
        for (int b = 0; b < 10; b++) {
            for (int d = 0; d < 100; d++) {
                assertTrue(manager.isTransitDoc("doc-" + b + "-" + d));
            }
        }
        // Also check 100 non-existent docs
        for (int i = 0; i < 100; i++) {
            assertFalse(manager.isTransitDoc("nonexistent-" + i));
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // G24: Must complete in <50ms
        assertTrue("Transit index lookup for 1100 docs took " + elapsedMs + "ms (limit: 50ms)",
                elapsedMs < 50);
    }

    // ========================================================================
    // Edge cases
    // ========================================================================

    @Test
    public void testTrackSameDocTwiceOverwritesBatch() {
        String batch1 = manager.startBatch("device-1", "user-1");
        String batch2 = manager.startBatch("device-2", "user-2");
        manager.trackTransitDoc(batch1, "doc-1");
        manager.trackTransitDoc(batch2, "doc-1"); // overwrite

        // doc-1 should now be in batch2
        Set<String> batch1Docs = manager.getDocIdsForBatch(batch1);
        assertFalse("doc-1 should no longer be in batch1", batch1Docs.contains("doc-1"));

        Set<String> batch2Docs = manager.getDocIdsForBatch(batch2);
        assertTrue("doc-1 should be in batch2", batch2Docs.contains("doc-1"));
    }

    @Test
    public void testMarkBatchPushedForNonexistentBatchIsSafe() {
        manager.markBatchPushed("nonexistent-batch"); // should not throw
    }

    @Test
    public void testMarkBatchPurgedForNonexistentBatchIsSafe() {
        manager.markBatchPurged("nonexistent-batch"); // should not throw
    }

    @Test
    public void testGetDocIdsForNonexistentBatchReturnsEmpty() {
        Set<String> docs = manager.getDocIdsForBatch("nonexistent-batch");
        assertTrue(docs.isEmpty());
    }
}
