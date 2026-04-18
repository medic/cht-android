package org.medicmobile.webapp.mobile.p2p;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests for ScopeGuard.classify() — the critical classification engine.
 * Uses test data from p2p-dev/test-transit/.
 *
 * G22: classify() MUST be deterministic — same input always produces same output.
 */
@SuppressWarnings("java:S5976") // Parameterized tests not warranted: each case has distinct setup and semantics
public class ScopeGuardTest {

    private ScopeGuard scopeGuard;
    private ScopeManifest senderScope;
    private ScopeManifest receiverScope;
    private Map<String, String> parentIndex;

    @Before
    public void setUp() {
        scopeGuard = new ScopeGuard();

        // Sender is a CHW under clinic-1a
        senderScope = new ScopeManifest("clinic-1a", 2,
                Arrays.asList("person", "clinic", "data_record"), "2026-03-23");

        // Receiver is a Supervisor at hc-1 with replication_depth=1
        receiverScope = new ScopeManifest("hc-1", 1,
                Arrays.asList("person", "clinic", "data_record"), "2026-03-23");

        // Build parent index from the test data hierarchy:
        //   hc-1
        //   ├── clinic-1a
        //   │   ├── household-1a
        //   │   │   ├── patient-1a
        //   │   │   └── patient-1b
        //   │   └── household-1b
        //   │       └── patient-1c
        //   └── clinic-1b
        //   hc-2
        //   └── clinic-2a
        //       └── household-2a
        //           └── patient-2a
        parentIndex = new HashMap<>();
        parentIndex.put("clinic-1a", "hc-1");
        parentIndex.put("clinic-1b", "hc-1");
        parentIndex.put("household-1a", "clinic-1a");
        parentIndex.put("household-1b", "clinic-1a");
        parentIndex.put("patient-1a", "household-1a");
        parentIndex.put("patient-1b", "household-1a");
        parentIndex.put("patient-1c", "household-1b");
        parentIndex.put("clinic-2a", "hc-2");
        parentIndex.put("household-2a", "clinic-2a");
        parentIndex.put("patient-2a", "household-2a");
    }

    // ========================================================================
    // Classification tests from expected-classification.json
    // ========================================================================

    @Test
    public void testClinic1aInScope() throws JSONException {
        // clinic-1a: depth=1 from hc-1, replication_depth=1 → IN_SCOPE
        JSONObject doc = makeContact("clinic-1a", "contact", "hc-1");
        ValidationResult result = scopeGuard.classify(doc, senderScope, receiverScope, parentIndex);

        assertTrue("clinic-1a should be accepted", result.isAccepted());
        assertEquals(DocScope.IN_SCOPE, result.getScope());
    }

    @Test
    public void testHousehold1aTransit() throws JSONException {
        // household-1a: depth=2 from hc-1, replication_depth=1 → TRANSIT
        JSONObject doc = makeContact("household-1a", "contact", "clinic-1a");
        ValidationResult result = scopeGuard.classify(doc, senderScope, receiverScope, parentIndex);

        assertTrue("household-1a should be accepted", result.isAccepted());
        assertEquals(DocScope.TRANSIT, result.getScope());
    }

    @Test
    public void testHousehold1bTransit() throws JSONException {
        // household-1b: depth=2 from hc-1, replication_depth=1 → TRANSIT
        JSONObject doc = makeContact("household-1b", "contact", "clinic-1a");
        ValidationResult result = scopeGuard.classify(doc, senderScope, receiverScope, parentIndex);

        assertTrue("household-1b should be accepted", result.isAccepted());
        assertEquals(DocScope.TRANSIT, result.getScope());
    }

    @Test
    public void testPatient1aTransit() throws JSONException {
        // patient-1a: depth=3 from hc-1, replication_depth=1 → TRANSIT
        JSONObject doc = makeContact("patient-1a", "contact", "household-1a");
        ValidationResult result = scopeGuard.classify(doc, senderScope, receiverScope, parentIndex);

        assertTrue("patient-1a should be accepted", result.isAccepted());
        assertEquals(DocScope.TRANSIT, result.getScope());
    }

    @Test
    public void testPatient1bTransit() throws JSONException {
        // patient-1b: depth=3 from hc-1, replication_depth=1 → TRANSIT
        JSONObject doc = makeContact("patient-1b", "contact", "household-1a");
        ValidationResult result = scopeGuard.classify(doc, senderScope, receiverScope, parentIndex);

        assertTrue("patient-1b should be accepted", result.isAccepted());
        assertEquals(DocScope.TRANSIT, result.getScope());
    }

    @Test
    public void testPatient1cTransit() throws JSONException {
        // patient-1c: depth=3 from hc-1, replication_depth=1 → TRANSIT
        JSONObject doc = makeContact("patient-1c", "contact", "household-1b");
        ValidationResult result = scopeGuard.classify(doc, senderScope, receiverScope, parentIndex);

        assertTrue("patient-1c should be accepted", result.isAccepted());
        assertEquals(DocScope.TRANSIT, result.getScope());
    }

    @Test
    public void testReportPatient1aTransit() throws JSONException {
        // report-patient-1a: parent is patient-1a (depth=3), > replication_depth → TRANSIT
        JSONObject doc = makeDataRecord("report-patient-1a", "patient-1a");
        ValidationResult result = scopeGuard.classify(doc, senderScope, receiverScope, parentIndex);

        assertTrue("report-patient-1a should be accepted", result.isAccepted());
        assertEquals(DocScope.TRANSIT, result.getScope());
    }

    @Test
    public void testReportPatient1bTransit() throws JSONException {
        // report-patient-1b: parent is patient-1b (depth=3), > replication_depth → TRANSIT
        JSONObject doc = makeDataRecord("report-patient-1b", "patient-1b");
        ValidationResult result = scopeGuard.classify(doc, senderScope, receiverScope, parentIndex);

        assertTrue("report-patient-1b should be accepted", result.isAccepted());
        assertEquals(DocScope.TRANSIT, result.getScope());
    }

    @Test
    public void testReportClinic1aInScope() throws JSONException {
        // report-clinic-1a: parent is clinic-1a (depth=1), <= replication_depth → IN_SCOPE
        JSONObject doc = makeDataRecord("report-clinic-1a", "clinic-1a");
        ValidationResult result = scopeGuard.classify(doc, senderScope, receiverScope, parentIndex);

        assertTrue("report-clinic-1a should be accepted", result.isAccepted());
        assertEquals(DocScope.IN_SCOPE, result.getScope());
    }

    @Test
    public void testClinic2aRejected() throws JSONException {
        // clinic-2a: under hc-2, not under hc-1 → REJECTED (sender scope violation)
        JSONObject doc = makeContact("clinic-2a", "contact", "hc-2");

        // Sender scope is clinic-1a; clinic-2a is NOT within clinic-1a subtree
        ValidationResult result = scopeGuard.classify(doc, senderScope, receiverScope, parentIndex);

        assertFalse("clinic-2a should be rejected", result.isAccepted());
        assertEquals(DocScope.REJECTED, result.getScope());
        assertNotNull(result.getReason());
    }

    @Test
    public void testHousehold2aRejected() throws JSONException {
        // household-2a: under clinic-2a → hc-2, not in sender's facility → REJECTED
        JSONObject doc = makeContact("household-2a", "contact", "clinic-2a");
        ValidationResult result = scopeGuard.classify(doc, senderScope, receiverScope, parentIndex);

        assertFalse("household-2a should be rejected", result.isAccepted());
        assertEquals(DocScope.REJECTED, result.getScope());
    }

    @Test
    public void testPatient2aRejected() throws JSONException {
        // patient-2a: under household-2a → clinic-2a → hc-2 → REJECTED
        JSONObject doc = makeContact("patient-2a", "contact", "household-2a");
        ValidationResult result = scopeGuard.classify(doc, senderScope, receiverScope, parentIndex);

        assertFalse("patient-2a should be rejected", result.isAccepted());
        assertEquals(DocScope.REJECTED, result.getScope());
    }

    // ========================================================================
    // System doc rejection tests
    // ========================================================================

    @Test
    public void testRejectDesignDoc() throws JSONException {
        JSONObject doc = new JSONObject();
        doc.put("_id", "_design/medic");
        doc.put("type", "contact");
        ValidationResult result = scopeGuard.classify(doc, senderScope, receiverScope, parentIndex);

        assertFalse("design doc should be rejected", result.isAccepted());
        assertEquals(DocScope.REJECTED, result.getScope());
        assertTrue(result.getReason().contains("design_doc"));
    }

    @Test
    public void testRejectLocalDoc() throws JSONException {
        JSONObject doc = new JSONObject();
        doc.put("_id", "_local/some-local-doc");
        doc.put("type", "contact");
        ValidationResult result = scopeGuard.classify(doc, senderScope, receiverScope, parentIndex);

        assertFalse("local doc should be rejected", result.isAccepted());
        assertEquals(DocScope.REJECTED, result.getScope());
        assertTrue(result.getReason().contains("local_doc"));
    }

    @Test
    public void testRejectUnknownType() throws JSONException {
        JSONObject doc = new JSONObject();
        doc.put("_id", "some-unknown-doc");
        doc.put("type", "task");
        ValidationResult result = scopeGuard.classify(doc, senderScope, receiverScope, parentIndex);

        assertFalse("unknown type should be rejected", result.isAccepted());
        assertEquals(DocScope.REJECTED, result.getScope());
        assertTrue(result.getReason().contains("disallowed_type"));
    }

    @Test
    public void testRejectNullType() throws JSONException {
        JSONObject doc = new JSONObject();
        doc.put("_id", "no-type-doc");
        // no "type" field
        ValidationResult result = scopeGuard.classify(doc, senderScope, receiverScope, parentIndex);

        assertFalse("doc with no type should be rejected", result.isAccepted());
        assertEquals(DocScope.REJECTED, result.getScope());
        assertTrue(result.getReason().contains("disallowed_type"));
    }

    @Test
    public void testRejectDeletedDoc() throws JSONException {
        JSONObject doc = makeContact("clinic-1a", "contact", "hc-1");
        doc.put("_deleted", true);
        ValidationResult result = scopeGuard.classify(doc, senderScope, receiverScope, parentIndex);

        assertFalse("deleted doc should be rejected", result.isAccepted());
        assertEquals(DocScope.REJECTED, result.getScope());
        assertTrue(result.getReason().contains("deletion_not_allowed"));
    }

    // ========================================================================
    // Edge cases
    // ========================================================================

    @Test
    public void testRejectMissingId() throws JSONException {
        JSONObject doc = new JSONObject();
        doc.put("type", "contact");
        ValidationResult result = scopeGuard.classify(doc, senderScope, receiverScope, parentIndex);

        assertFalse("doc with no _id should be rejected", result.isAccepted());
        assertTrue(result.getReason().contains("missing_id"));
    }

    @Test
    public void testRejectEmptyId() throws JSONException {
        JSONObject doc = new JSONObject();
        doc.put("_id", "");
        doc.put("type", "contact");
        ValidationResult result = scopeGuard.classify(doc, senderScope, receiverScope, parentIndex);

        assertFalse("doc with empty _id should be rejected", result.isAccepted());
        assertTrue(result.getReason().contains("missing_id"));
    }

    @Test
    public void testDataRecordWithNoParentRejected() throws JSONException {
        JSONObject doc = new JSONObject();
        doc.put("_id", "orphan-report");
        doc.put("type", "data_record");
        // no parent field
        ValidationResult result = scopeGuard.classify(doc, senderScope, receiverScope, parentIndex);

        assertFalse("data_record with no parent should be rejected", result.isAccepted());
        assertTrue(result.getReason().contains("data_record_no_parent"));
    }

    @Test
    public void testSharedDocTypesAlwaysInScope() throws JSONException {
        // "form" type docs are shared and always in-scope
        JSONObject doc = new JSONObject();
        doc.put("_id", "form:pregnancy");
        doc.put("type", "form");
        ValidationResult result = scopeGuard.classify(doc, senderScope, receiverScope, parentIndex);

        assertTrue("form type should be accepted", result.isAccepted());
        assertEquals(DocScope.IN_SCOPE, result.getScope());
    }

    @Test
    public void testTranslationDocAlwaysInScope() throws JSONException {
        JSONObject doc = new JSONObject();
        doc.put("_id", "messages-en");
        doc.put("type", "translation");
        ValidationResult result = scopeGuard.classify(doc, senderScope, receiverScope, parentIndex);

        assertTrue("translation type should be accepted", result.isAccepted());
        assertEquals(DocScope.IN_SCOPE, result.getScope());
    }

    @Test
    public void testResourcesDocAlwaysInScope() throws JSONException {
        JSONObject doc = new JSONObject();
        doc.put("_id", "resources");
        doc.put("type", "resources");
        ValidationResult result = scopeGuard.classify(doc, senderScope, receiverScope, parentIndex);

        assertTrue("resources type should be accepted", result.isAccepted());
        assertEquals(DocScope.IN_SCOPE, result.getScope());
    }

    @Test
    public void testFacilityRootItselfAtDepthZero() throws JSONException {
        // hc-1 itself: depth=0 from hc-1, but we need a sender scope that includes hc-1
        ScopeManifest broadSender = new ScopeManifest("hc-1", 3,
                Arrays.asList("person", "clinic", "health_center"), "2026-03-23");
        parentIndex.put("hc-1", "district-1"); // hc-1's parent

        JSONObject doc = makeContact("hc-1", "health_center", "district-1");
        ValidationResult result = scopeGuard.classify(doc, broadSender, receiverScope, parentIndex);

        assertTrue("facility root itself should be accepted", result.isAccepted());
        assertEquals(DocScope.IN_SCOPE, result.getScope());
    }

    @Test
    public void testPersonTypeContact() throws JSONException {
        // "person" is a contact type, should use parent chain
        JSONObject doc = new JSONObject();
        doc.put("_id", "clinic-1a");
        doc.put("type", "person");
        doc.put("parent", new JSONObject().put("_id", "hc-1"));
        parentIndex.put("clinic-1a", "hc-1");

        ValidationResult result = scopeGuard.classify(doc, senderScope, receiverScope, parentIndex);
        assertTrue("person type should be accepted in scope", result.isAccepted());
        assertEquals(DocScope.IN_SCOPE, result.getScope());
    }

    @Test
    public void testReplicationDepthZero() throws JSONException {
        // Receiver with depth=0: only the facility root itself is in-scope
        ScopeManifest zeroDepth = new ScopeManifest("hc-1", 0,
                Arrays.asList("person", "clinic", "data_record"), "2026-03-23");

        JSONObject doc = makeContact("clinic-1a", "contact", "hc-1");
        ValidationResult result = scopeGuard.classify(doc, senderScope, zeroDepth, parentIndex);

        assertTrue("clinic-1a at depth 1 should be transit when depth limit is 0", result.isAccepted());
        assertEquals(DocScope.TRANSIT, result.getScope());
    }

    @Test
    public void testHighReplicationDepthAllInScope() throws JSONException {
        // Receiver with depth=10: everything under hc-1 should be IN_SCOPE
        ScopeManifest deepScope = new ScopeManifest("hc-1", 10,
                Arrays.asList("person", "clinic", "data_record"), "2026-03-23");

        JSONObject doc = makeContact("patient-1a", "contact", "household-1a");
        ValidationResult result = scopeGuard.classify(doc, senderScope, deepScope, parentIndex);

        assertTrue("patient-1a should be in-scope with high depth limit", result.isAccepted());
        assertEquals(DocScope.IN_SCOPE, result.getScope());
    }

    // ========================================================================
    // G22: Determinism test — same input, same output, 1000 iterations
    // ========================================================================

    @Test
    public void testDeterminism() throws JSONException {
        JSONObject[] testDocs = {
                makeContact("clinic-1a", "contact", "hc-1"),
                makeContact("household-1a", "contact", "clinic-1a"),
                makeContact("patient-1a", "contact", "household-1a"),
                makeDataRecord("report-patient-1a", "patient-1a"),
                makeDataRecord("report-clinic-1a", "clinic-1a"),
                makeContact("clinic-2a", "contact", "hc-2"),
        };

        // Classify once to get baseline
        ValidationResult[] baseline = new ValidationResult[testDocs.length];
        for (int i = 0; i < testDocs.length; i++) {
            baseline[i] = scopeGuard.classify(testDocs[i], senderScope, receiverScope, parentIndex);
        }

        // Repeat 1000 times and verify identical results
        for (int iteration = 0; iteration < 1000; iteration++) {
            for (int i = 0; i < testDocs.length; i++) {
                ValidationResult result = scopeGuard.classify(testDocs[i], senderScope, receiverScope, parentIndex);
                assertEquals("Determinism violated for doc " + testDocs[i].getString("_id")
                        + " at iteration " + iteration, baseline[i], result);
            }
        }
    }

    // ========================================================================
    // isWithinFacilitySubtree tests (package-private method)
    // ========================================================================

    @Test
    public void testIsWithinFacilitySubtreeDirectChild() {
        assertTrue(scopeGuard.isWithinFacilitySubtree("clinic-1a", "hc-1", parentIndex));
    }

    @Test
    public void testIsWithinFacilitySubtreeDeepDescendant() {
        assertTrue(scopeGuard.isWithinFacilitySubtree("patient-1a", "hc-1", parentIndex));
    }

    @Test
    public void testIsWithinFacilitySubtreeRootItself() {
        assertTrue(scopeGuard.isWithinFacilitySubtree("hc-1", "hc-1", parentIndex));
    }

    @Test
    public void testIsWithinFacilitySubtreeDifferentBranch() {
        assertFalse(scopeGuard.isWithinFacilitySubtree("clinic-2a", "hc-1", parentIndex));
    }

    @Test
    public void testIsWithinFacilitySubtreeNullDoc() {
        assertFalse(scopeGuard.isWithinFacilitySubtree(null, "hc-1", parentIndex));
    }

    @Test
    public void testIsWithinFacilitySubtreeOrphanDoc() {
        assertFalse(scopeGuard.isWithinFacilitySubtree("orphan-id", "hc-1", parentIndex));
    }

    // ========================================================================
    // getDepthFromFacility tests (package-private method)
    // ========================================================================

    @Test
    public void testGetDepthFromFacilityRoot() {
        assertEquals(0, scopeGuard.getDepthFromFacility("hc-1", "hc-1", parentIndex));
    }

    @Test
    public void testGetDepthFromFacilityDirectChild() {
        assertEquals(1, scopeGuard.getDepthFromFacility("clinic-1a", "hc-1", parentIndex));
    }

    @Test
    public void testGetDepthFromFacilityGrandchild() {
        assertEquals(2, scopeGuard.getDepthFromFacility("household-1a", "hc-1", parentIndex));
    }

    @Test
    public void testGetDepthFromFacilityGreatGrandchild() {
        assertEquals(3, scopeGuard.getDepthFromFacility("patient-1a", "hc-1", parentIndex));
    }

    @Test
    public void testGetDepthFromFacilityNotInSubtree() {
        assertEquals(-1, scopeGuard.getDepthFromFacility("clinic-2a", "hc-1", parentIndex));
    }

    @Test
    public void testGetDepthFromFacilityNull() {
        assertEquals(-1, scopeGuard.getDepthFromFacility(null, "hc-1", parentIndex));
    }

    // ========================================================================
    // Summary count validation (matches expected-classification.json summary)
    // ========================================================================

    @Test
    public void testBatchClassificationSummary() throws JSONException {
        // Recreate the full batch from mixed-depth-batch.json
        JSONObject[] batch = {
                makeContact("clinic-1a", "contact", "hc-1"),
                makeContact("household-1a", "contact", "clinic-1a"),
                makeContact("household-1b", "contact", "clinic-1a"),
                makeContact("patient-1a", "contact", "household-1a"),
                makeContact("patient-1b", "contact", "household-1a"),
                makeContact("patient-1c", "contact", "household-1b"),
                makeDataRecord("report-patient-1a", "patient-1a"),
                makeDataRecord("report-patient-1b", "patient-1b"),
                makeDataRecord("report-clinic-1a", "clinic-1a"),
                makeContact("clinic-2a", "contact", "hc-2"),
                makeContact("household-2a", "contact", "clinic-2a"),
                makeContact("patient-2a", "contact", "household-2a"),
        };

        int inScope = 0, transit = 0, rejected = 0;
        for (JSONObject doc : batch) {
            ValidationResult result = scopeGuard.classify(doc, senderScope, receiverScope, parentIndex);
            switch (result.getScope()) {
                case IN_SCOPE: inScope++; break;
                case TRANSIT: transit++; break;
                case REJECTED: rejected++; break;
            }
        }

        // Expected from expected-classification.json: 2 in_scope, 7 transit, 3 rejected
        assertEquals("IN_SCOPE count mismatch", 2, inScope);
        assertEquals("TRANSIT count mismatch", 7, transit);
        assertEquals("REJECTED count mismatch", 3, rejected);
        assertEquals("Total count mismatch", 12, batch.length);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private JSONObject makeContact(String id, String type, String parentId) throws JSONException {
        JSONObject doc = new JSONObject();
        doc.put("_id", id);
        doc.put("type", type);
        doc.put("reported_date", 1678000000000L);
        if (parentId != null) {
            doc.put("parent", new JSONObject().put("_id", parentId));
        }
        return doc;
    }

    private JSONObject makeDataRecord(String id, String parentId) throws JSONException {
        JSONObject doc = new JSONObject();
        doc.put("_id", id);
        doc.put("type", "data_record");
        doc.put("form", "assessment");
        doc.put("reported_date", 1678000000000L);
        if (parentId != null) {
            doc.put("parent", new JSONObject().put("_id", parentId));
        }
        return doc;
    }
}
