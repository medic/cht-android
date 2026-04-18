package org.medicmobile.webapp.mobile.p2p;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * ScopeGuard is the critical classification engine for P2P sync.
 * It enforces the 5 inviolable scope rules (RFC Section 10.1) and classifies
 * each document as IN_SCOPE, TRANSIT, or REJECTED.
 *
 * <p>Guard G22: classify() MUST be deterministic — same input always produces same output.
 * This class has NO mutable state and all decisions are purely functional on inputs.</p>
 *
 * <h3>5 Inviolable Rules:</h3>
 * <ol>
 *   <li>CHW can only PUSH docs within their facility subtree</li>
 *   <li>Supervisor can only ACCEPT from CHW in their subtree</li>
 *   <li>Supervisor can only OFFER docs within CHW's scope</li>
 *   <li>Neither party requests docs outside their scope</li>
 *   <li>P2P is ADDITIVE ONLY — no deletions/purges/permission changes</li>
 * </ol>
 */
public final class ScopeGuard {

    private static final String TAG = "ScopeGuard";
    private static final String TYPE_DATA_RECORD = "data_record";
    private static final String REJECT_BRANCH_MISMATCH = "branch_mismatch";

    /**
     * Allowed top-level doc types for P2P sync.
     * "contact" is included because CHT uses it as a generic type with contact_type subtypes.
     */
    private static final Set<String> ALLOWED_DOC_TYPES = new HashSet<>(Arrays.asList(
            TYPE_DATA_RECORD,
            "contact",
            "person",
            "clinic",
            "health_center",
            "district_hospital",
            "form",
            "translation",
            "resources"
    ));

    /**
     * Doc types that are classified as "contact" for scope purposes.
     * These use parent-chain hierarchy for scope determination.
     */
    private static final Set<String> CONTACT_DOC_TYPES = new HashSet<>(Arrays.asList(
            "contact",
            "person",
            "clinic",
            "health_center",
            "district_hospital"
    ));

    /**
     * Classify a single document for P2P sync scope.
     *
     * @param doc            The document to classify (JSONObject with _id, type, parent, etc.)
     * @param senderScope    The sender's (CHW's) scope manifest
     * @param receiverScope  The receiver's (Supervisor's) scope manifest
     * @param parentIndex    Pre-built map of docId -> parentId for the entire batch,
     *                       enabling parent chain traversal without database lookups
     * @return ValidationResult with scope classification or rejection reason
     */
    public ValidationResult classify(JSONObject doc, ScopeManifest senderScope,
                                     ScopeManifest receiverScope, Map<String, String> parentIndex) {
        // Step 1: Reject invalid or system docs
        ValidationResult basicCheck = rejectInvalidDocs(doc);
        if (basicCheck != null) {
            return basicCheck;
        }

        String docId = doc.optString("_id", null);
        String docType = doc.optString("type", null);
        String parentId = getParentId(doc);

        // Step 2: Verify sender scope
        ValidationResult senderCheck = checkSenderScope(docId, docType, parentId, senderScope, parentIndex);
        if (senderCheck != null) {
            return senderCheck;
        }

        // Step 3: Classify against receiver scope
        return classifyForReceiver(docId, docType, parentId, receiverScope, parentIndex);
    }

    private ValidationResult rejectInvalidDocs(JSONObject doc) {
        String docId = doc.optString("_id", null);
        if (docId == null || docId.isEmpty()) {
            return ValidationResult.reject("missing_id");
        }
        if (docId.startsWith("_design/")) {
            return ValidationResult.reject("design_doc");
        }
        if (docId.startsWith("_local/")) {
            return ValidationResult.reject("local_doc");
        }

        String docType = doc.optString("type", null);
        if (docType == null || !ALLOWED_DOC_TYPES.contains(docType)) {
            return ValidationResult.reject("disallowed_type: " + docType);
        }

        if (doc.optBoolean("_deleted", false)) {
            return ValidationResult.reject("deletion_not_allowed");
        }
        return null;
    }

    private ValidationResult checkSenderScope(String docId, String docType, String parentId,
                                              ScopeManifest senderScope, Map<String, String> parentIndex) {
        if (isContactType(docType)
                && !isWithinFacilitySubtree(docId, senderScope.getFacilitySubtreeRoot(), parentIndex)) {
            return ValidationResult.reject("sender_scope_violation: not in sender facility subtree");
        }
        if (TYPE_DATA_RECORD.equals(docType)
                && parentId != null
                && !isWithinFacilitySubtree(parentId, senderScope.getFacilitySubtreeRoot(), parentIndex)) {
            return ValidationResult.reject("sender_scope_violation: report parent not in sender facility subtree");
        }
        return null;
    }

    private ValidationResult classifyForReceiver(String docId, String docType, String parentId,
                                                 ScopeManifest receiverScope, Map<String, String> parentIndex) {
        if (isContactType(docType)) {
            return classifyContact(docId, receiverScope, parentIndex);
        }
        if (TYPE_DATA_RECORD.equals(docType)) {
            return classifyDataRecord(parentId, receiverScope, parentIndex);
        }
        // Shared doc types (form, translation, resources) — always in scope if type is allowed
        return ValidationResult.accept(DocScope.IN_SCOPE);
    }

    private ValidationResult classifyContact(String docId, ScopeManifest receiverScope,
                                             Map<String, String> parentIndex) {
        if (!isWithinFacilitySubtree(docId, receiverScope.getFacilitySubtreeRoot(), parentIndex)) {
            return ValidationResult.reject(REJECT_BRANCH_MISMATCH);
        }
        int depth = getDepthFromFacility(docId, receiverScope.getFacilitySubtreeRoot(), parentIndex);
        if (depth < 0) {
            return ValidationResult.reject(REJECT_BRANCH_MISMATCH);
        }
        if (depth <= receiverScope.getReplicationDepth()) {
            return ValidationResult.accept(DocScope.IN_SCOPE);
        }
        return ValidationResult.accept(DocScope.TRANSIT);
    }

    /**
     * Classify a data_record based on its parent contact's scope.
     *
     * Special rule: if the data_record's DIRECT parent contact is IN_SCOPE,
     * the report is also IN_SCOPE regardless of its own depth calculation.
     */
    private ValidationResult classifyDataRecord(String parentId, ScopeManifest receiverScope,
                                                Map<String, String> parentIndex) {
        if (parentId == null) {
            return ValidationResult.reject("data_record_no_parent");
        }

        // Check if parent is within receiver's facility subtree
        if (!isWithinFacilitySubtree(parentId, receiverScope.getFacilitySubtreeRoot(), parentIndex)) {
            return ValidationResult.reject(REJECT_BRANCH_MISMATCH);
        }

        // Get the parent contact's depth from receiver's facility root
        int parentDepth = getDepthFromFacility(parentId, receiverScope.getFacilitySubtreeRoot(), parentIndex);
        if (parentDepth < 0) {
            return ValidationResult.reject(REJECT_BRANCH_MISMATCH);
        }

        // Special case: if the direct parent contact is in-scope, report is in-scope too
        if (parentDepth <= receiverScope.getReplicationDepth()) {
            return ValidationResult.accept(DocScope.IN_SCOPE);
        }

        // Parent is transit, so the report is also transit
        return ValidationResult.accept(DocScope.TRANSIT);
    }

    private static final int MAX_PARENT_CHAIN_DEPTH = 20;

    /**
     * Check if a doc is within a facility subtree by walking its parent chain.
     * Returns true if the parent chain reaches the facilityRoot.
     *
     * @param docId        The document ID to check
     * @param facilityRoot The facility subtree root ID
     * @param parentIndex  Map of docId -> parentId
     */
    boolean isWithinFacilitySubtree(String docId, String facilityRoot, Map<String, String> parentIndex) {
        return getDepthFromFacility(docId, facilityRoot, parentIndex) >= 0;
    }

    /**
     * Calculate the depth of a doc from the facility root.
     *
     * Depth 0 = the facility root itself
     * Depth 1 = direct child of facility root
     * Depth 2 = grandchild, etc.
     *
     * @return depth >= 0, or -1 if the doc is not within the facility subtree
     */
    int getDepthFromFacility(String docId, String facilityRoot, Map<String, String> parentIndex) {
        if (docId == null) {
            return -1;
        }
        if (docId.equals(facilityRoot)) {
            return 0;
        }
        return walkParentChain(docId, facilityRoot, parentIndex);
    }

    /**
     * Walk the parent chain from docId, counting depth until facilityRoot is found.
     *
     * @return depth >= 1 if facilityRoot is found, or -1 if not in subtree
     */
    private int walkParentChain(String docId, String facilityRoot, Map<String, String> parentIndex) {
        String current = docId;
        int depth = 0;
        for (int i = 0; i < MAX_PARENT_CHAIN_DEPTH; i++) {
            String parentId = parentIndex.get(current);
            if (parentId == null) {
                return -1;
            }
            depth++;
            if (parentId.equals(facilityRoot)) {
                return depth;
            }
            current = parentId;
        }
        return -1;
    }

    /**
     * Extract the parent._id from a doc's parent field.
     */
    private String getParentId(JSONObject doc) {
        JSONObject parent = doc.optJSONObject("parent");
        if (parent == null) {
            return null;
        }
        return parent.optString("_id", null);
    }

    /**
     * Check if the doc type is a contact type (uses parent hierarchy for scope).
     */
    private boolean isContactType(String docType) {
        return CONTACT_DOC_TYPES.contains(docType);
    }
}
