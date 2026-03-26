package org.medicmobile.webapp.mobile.p2p;

import org.json.JSONException;
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

    /**
     * Allowed top-level doc types for P2P sync.
     * "contact" is included because CHT uses it as a generic type with contact_type subtypes.
     */
    private static final Set<String> ALLOWED_DOC_TYPES = new HashSet<>(Arrays.asList(
            "data_record",
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
        try {
            return doClassify(doc, senderScope, receiverScope, parentIndex);
        } catch (JSONException e) {
            return ValidationResult.reject("malformed_doc: " + e.getMessage());
        }
    }

    private ValidationResult doClassify(JSONObject doc, ScopeManifest senderScope,
                                        ScopeManifest receiverScope, Map<String, String> parentIndex)
            throws JSONException {

        // Step 1: Extract _id and reject system docs
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

        // Step 2: Reject docs with disallowed types
        String docType = doc.optString("type", null);
        if (docType == null || !ALLOWED_DOC_TYPES.contains(docType)) {
            return ValidationResult.reject("disallowed_type: " + docType);
        }

        // Step 3: Rule 5 — reject deletions
        if (doc.optBoolean("_deleted", false)) {
            return ValidationResult.reject("deletion_not_allowed");
        }

        // Step 4: Get the parent ID from the doc
        String parentId = getParentId(doc);

        // Step 5: Verify sender scope — doc must be within sender's facility subtree
        if (isContactType(docType)) {
            if (!isWithinFacilitySubtree(docId, senderScope.getFacilitySubtreeRoot(), parentIndex)) {
                return ValidationResult.reject("sender_scope_violation: not in sender facility subtree");
            }
        } else if ("data_record".equals(docType)) {
            if (parentId != null && !isWithinFacilitySubtree(parentId, senderScope.getFacilitySubtreeRoot(), parentIndex)) {
                return ValidationResult.reject("sender_scope_violation: report parent not in sender facility subtree");
            }
        }

        // Step 6: Check receiver scope — doc must be within receiver's facility subtree
        if (isContactType(docType)) {
            if (!isWithinFacilitySubtree(docId, receiverScope.getFacilitySubtreeRoot(), parentIndex)) {
                return ValidationResult.reject("branch_mismatch");
            }
            int depth = getDepthFromFacility(docId, receiverScope.getFacilitySubtreeRoot(), parentIndex);
            if (depth < 0) {
                return ValidationResult.reject("branch_mismatch");
            }
            if (depth <= receiverScope.getReplicationDepth()) {
                return ValidationResult.accept(DocScope.IN_SCOPE);
            }
            return ValidationResult.accept(DocScope.TRANSIT);
        }

        if ("data_record".equals(docType)) {
            return classifyDataRecord(parentId, receiverScope, parentIndex);
        }

        // Shared doc types (form, translation, resources) — always in scope if type is allowed
        return ValidationResult.accept(DocScope.IN_SCOPE);
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
            return ValidationResult.reject("branch_mismatch");
        }

        // Get the parent contact's depth from receiver's facility root
        int parentDepth = getDepthFromFacility(parentId, receiverScope.getFacilitySubtreeRoot(), parentIndex);
        if (parentDepth < 0) {
            return ValidationResult.reject("branch_mismatch");
        }

        // Special case: if the direct parent contact is in-scope, report is in-scope too
        if (parentDepth <= receiverScope.getReplicationDepth()) {
            return ValidationResult.accept(DocScope.IN_SCOPE);
        }

        // Parent is transit, so the report is also transit
        return ValidationResult.accept(DocScope.TRANSIT);
    }

    /**
     * Check if a doc is within a facility subtree by walking its parent chain.
     * Returns true if the parent chain reaches the facilityRoot.
     *
     * @param docId        The document ID to check
     * @param facilityRoot The facility subtree root ID
     * @param parentIndex  Map of docId -> parentId
     */
    boolean isWithinFacilitySubtree(String docId, String facilityRoot, Map<String, String> parentIndex) {
        if (docId == null) {
            return false;
        }
        if (docId.equals(facilityRoot)) {
            return true;
        }

        String current = docId;
        int maxDepth = 20; // safety limit to prevent infinite loops
        for (int i = 0; i < maxDepth; i++) {
            String parentId = parentIndex.get(current);
            if (parentId == null) {
                // Reached the top without finding facilityRoot
                return false;
            }
            if (parentId.equals(facilityRoot)) {
                return true;
            }
            current = parentId;
        }

        return false;
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

        String current = docId;
        int depth = 0;
        int maxDepth = 20; // safety limit
        for (int i = 0; i < maxDepth; i++) {
            String parentId = parentIndex.get(current);
            if (parentId == null) {
                return -1; // not in subtree
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
