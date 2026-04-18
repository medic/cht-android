package org.medicmobile.webapp.mobile.p2p;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * POST /_p2p/accept-docs — Receive docs from an authenticated CHW.
 *
 * Two-phase approach:
 *   Phase 1: Accept ALL docs from JWT-authenticated CHWs (write to PouchDB).
 *            Only reject: _deleted docs (additive only) and oversized docs.
 *   Phase 2: After write, query PouchDB's contacts_by_depth view to classify
 *            which docs are beyond the Supervisor's replication_depth (TRANSIT).
 *            Transit doc IDs are tracked in _local/p2p-transit-docs for UI filtering.
 *
 * This uses the same contacts_by_depth view that CHT's server uses for replication
 * scope, making classification config-driven and hierarchy-agnostic.
 *
 * Guards: G22 (deterministic classification), G23 (transit hidden from UI)
 */
public final class AcceptDocsEndpoint {

    private static final String TAG = "AcceptDocsEndpoint";
    private static final int MAX_BATCH_SIZE = 500;
    private static final String KEY_ID = "_id";
    private static final String KEY_ERROR = "error";

    private final PouchDbBridge bridge;
    private final TransitDocCallback transitCallback;
    private final ScopeManifest supervisorScope;
    private final P2pConfig config;

    public AcceptDocsEndpoint(PouchDbBridge bridge, TransitDocCallback transitCallback,
                              ScopeManifest supervisorScope, P2pConfig config) {
        this.bridge = bridge;
        this.transitCallback = transitCallback;
        this.supervisorScope = supervisorScope;
        this.config = config;
    }

    /**
     * Handle POST /_p2p/accept-docs request.
     *
     * @param requestBody Raw JSON request body
     * @param session     Active P2P session (provides CHW's scope)
     * @return JSON response object
     */
    public JSONObject handle(String requestBody, P2pSession session) {
        try {
            return doHandle(requestBody, session);
        } catch (JSONException e) {
            Log.e(TAG, "Error processing accept-docs", e);
            return errorResponse("malformed_request");
        }
    }

    private JSONObject doHandle(String requestBody, P2pSession session) throws JSONException {
        if (requestBody == null || requestBody.isEmpty()) {
            return errorResponse("empty_request");
        }
        if (session == null) {
            return errorResponse("no_active_session");
        }

        JSONObject body = new JSONObject(requestBody);
        JSONArray docs = body.optJSONArray("docs");
        if (docs == null || docs.length() == 0) {
            return buildResponse(0, 0, 0, new JSONArray());
        }

        if (docs.length() > MAX_BATCH_SIZE) {
            return errorResponse("batch_too_large: max " + MAX_BATCH_SIZE + " docs per request");
        }

        // Phase 1: Accept all valid docs
        JSONArray errors = new JSONArray();
        List<JSONObject> acceptedDocs = new ArrayList<>();
        long totalBytes = validateAndAcceptDocs(docs, acceptedDocs, errors);

        // Enforce cumulative relay size limit
        long maxRelayBytes = config.getMaxRelaySizeBytes();
        if (maxRelayBytes > 0 && (session.getBytesTransferred() + totalBytes) > maxRelayBytes) {
            return errorResponse("relay_size_exceeded: max " + (maxRelayBytes / (1024 * 1024)) + " MB");
        }

        if (!acceptedDocs.isEmpty()) {
            hydrateParentLineage(acceptedDocs);
            writeDocsToPouchDb(acceptedDocs);
            trackAcceptedDocs(acceptedDocs, session);
        }

        // Phase 2: Classify transit docs
        int transitCount = acceptedDocs.isEmpty() ? 0 : classifyTransitDocs(acceptedDocs, session);
        int inScopeCount = acceptedDocs.size() - transitCount;

        updateSessionCounters(session, inScopeCount, transitCount, errors.length(), totalBytes);

        Log.i(TAG, "accept-docs: in_scope=" + inScopeCount
                + " transit=" + transitCount
                + " rejected=" + errors.length());

        return buildResponse(inScopeCount, transitCount, errors.length(), errors);
    }

    private long validateAndAcceptDocs(JSONArray docs, List<JSONObject> acceptedDocs,
                                       JSONArray errors) throws JSONException {
        long totalBytes = 0;
        for (int i = 0; i < docs.length(); i++) {
            JSONObject doc = docs.getJSONObject(i);
            String docId = doc.optString(KEY_ID, "<unknown>");

            if (doc.optBoolean("_deleted", false)) {
                addError(errors, docId, "deleted_not_allowed");
                continue;
            }

            int docSize = doc.toString().length();
            if (docSize > config.getMaxDocSizeBytes()) {
                addError(errors, docId, "doc_too_large: " + docSize + " bytes");
                continue;
            }

            totalBytes += docSize;
            acceptedDocs.add(doc);
        }
        return totalBytes;
    }

    private void addError(JSONArray errors, String docId, String reason) throws JSONException {
        JSONObject err = new JSONObject();
        err.put("id", docId);
        err.put("reason", reason);
        errors.put(err);
    }

    private void writeDocsToPouchDb(List<JSONObject> acceptedDocs) {
        JSONArray docsToWrite = new JSONArray();
        for (JSONObject doc : acceptedDocs) {
            docsToWrite.put(doc);
        }
        String writeResult = bridge.writeDocs(docsToWrite.toString());
        Log.i(TAG, "writeDocs: " + acceptedDocs.size() + " docs, result="
                + (writeResult != null ? writeResult.substring(0, Math.min(200, writeResult.length())) : "null"));
    }

    private void trackAcceptedDocs(List<JSONObject> acceptedDocs, P2pSession session) {
        if (transitCallback == null) {
            return;
        }
        List<String> allAcceptedIds = new ArrayList<>();
        for (JSONObject doc : acceptedDocs) {
            String docId = doc.optString(KEY_ID, "");
            if (!docId.isEmpty()) {
                allAcceptedIds.add(docId);
            }
        }
        if (!allAcceptedIds.isEmpty()) {
            transitCallback.trackTransitDocs(
                    allAcceptedIds,
                    session.getPeerDeviceId(),
                    session.getPeerUserId()
            );
            Log.i(TAG, "Tracked " + allAcceptedIds.size() + " accepted doc IDs for purge");
        }
    }

    private void updateSessionCounters(P2pSession session, int inScopeCount, int transitCount,
                                       int rejectedCount, long totalBytes) {
        session.incrementDocsPulled(inScopeCount);
        session.incrementTransitDocs(transitCount);
        session.incrementDocsRejected(rejectedCount);
        session.addBytesTransferred(totalBytes);
        session.updateLastActivity();
    }

    /**
     * Hydrate truncated parent lineages in incoming docs.
     *
     * CHW-created docs store a truncated parent chain that stops at the CHW's facility.
     * When the Supervisor replicates to the server, server-side auth walks parent.parent...
     * to verify the doc belongs to the Supervisor's subtree. A truncated chain causes rejection.
     *
     * This method fetches the missing parent docs from PouchDB and extends each doc's
     * parent chain to include the full hierarchy up to the top.
     */
    private void hydrateParentLineage(List<JSONObject> docs) {
        try {
            // Build a map of docs in the current batch (by _id) for quick lookup
            Map<String, JSONObject> batchMap = new HashMap<>();
            for (JSONObject doc : docs) {
                String id = doc.optString(KEY_ID, "");
                if (!id.isEmpty()) {
                    batchMap.put(id, doc);
                }
            }

            // Collect terminal parent IDs — the deepest parent that has no further parent
            Set<String> terminalParentIds = new HashSet<>();
            for (JSONObject doc : docs) {
                JSONObject deepest = getDeepestParent(doc);
                if (deepest != null && deepest.has(KEY_ID) && !deepest.has("parent")) {
                    String parentId = deepest.getString(KEY_ID);
                    // Only fetch if not already in the batch
                    if (!batchMap.containsKey(parentId)) {
                        terminalParentIds.add(parentId);
                    }
                }
            }

            if (terminalParentIds.isEmpty()) {
                return;
            }

            // Fetch parent docs from PouchDB and cache their parent chains
            Map<String, JSONObject> parentChainCache = new HashMap<>();
            fetchAndCacheParentChains(terminalParentIds, batchMap, parentChainCache);

            // Extend each doc's parent chain
            int hydrated = 0;
            for (JSONObject doc : docs) {
                JSONObject deepest = getDeepestParent(doc);
                if (deepest != null && deepest.has(KEY_ID) && !deepest.has("parent")) {
                    String parentId = deepest.getString(KEY_ID);
                    JSONObject cachedParent = parentChainCache.get(parentId);
                    if (cachedParent != null && cachedParent.has("parent")) {
                        // Attach the parent's parent chain to extend the lineage
                        deepest.put("parent", cachedParent.getJSONObject("parent"));
                        hydrated++;
                    }
                }
            }

            if (hydrated > 0) {
                Log.i(TAG, "Hydrated parent lineage for " + hydrated + " docs");
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error hydrating parent lineage, docs will be written as-is", e);
        }
    }

    /**
     * Walk to the deepest parent in a doc's parent chain.
     * Returns the JSONObject of the last parent that has an _id but no further parent.
     */
    private JSONObject getDeepestParent(JSONObject doc) {
        JSONObject current = doc.optJSONObject("parent");
        if (current == null) {
            return null;
        }
        while (current.has("parent")) {
            JSONObject next = current.optJSONObject("parent");
            if (next == null) {
                break;
            }
            current = next;
        }
        return current;
    }

    /**
     * Fetch parent docs from PouchDB and build their lineage maps.
     * Recursively fetches parents until chains are complete (no more parents to fetch).
     */
    private void fetchAndCacheParentChains(Set<String> idsToFetch,
                                            Map<String, JSONObject> batchMap,
                                            Map<String, JSONObject> cache) throws JSONException {
        Set<String> currentIds = new HashSet<>(idsToFetch);
        int maxIterations = 10; // prevent infinite loops in circular references

        while (!currentIds.isEmpty() && maxIterations-- > 0) {
            // Remove IDs we already have cached
            currentIds.removeAll(cache.keySet());
            if (currentIds.isEmpty()) {
                break;
            }

            // Fetch from PouchDB
            JSONArray idsArray = new JSONArray();
            for (String id : currentIds) {
                idsArray.put(id);
            }

            String resultJson = bridge.getDocsByIds(idsArray.toString());
            if (resultJson == null || resultJson.isEmpty() || "[]".equals(resultJson)) {
                break;
            }

            JSONArray fetchedDocs = new JSONArray(resultJson);
            Set<String> nextIds = new HashSet<>();

            for (int i = 0; i < fetchedDocs.length(); i++) {
                JSONObject fetched = fetchedDocs.optJSONObject(i);
                if (fetched == null) continue;

                String id = fetched.optString(KEY_ID, "");
                if (id.isEmpty()) continue;

                // Build a lightweight parent reference (just _id + parent chain)
                JSONObject parentRef = new JSONObject();
                parentRef.put(KEY_ID, id);

                JSONObject fetchedParent = fetched.optJSONObject("parent");
                if (fetchedParent != null) {
                    parentRef.put("parent", cloneParentChain(fetchedParent));

                    // Check if this parent's chain is also truncated
                    JSONObject deepest = getDeepestParent(fetched);
                    if (deepest != null && deepest.has(KEY_ID) && !deepest.has("parent")) {
                        String nextId = deepest.getString(KEY_ID);
                        if (!cache.containsKey(nextId) && !batchMap.containsKey(nextId)) {
                            nextIds.add(nextId);
                        }
                    }
                }

                cache.put(id, parentRef);
            }

            currentIds = nextIds;
        }

        // Second pass: stitch together multi-level fetched chains
        // E.g., if we fetched clinic-1a (parent: {_id: hc-1}) and hc-1 (parent: {_id: district-1}),
        // we need clinic-1a's chain to include hc-1's parent too
        for (int pass = 0; pass < maxIterations; pass++) {
            boolean changed = false;
            for (Map.Entry<String, JSONObject> entry : cache.entrySet()) {
                JSONObject cached = entry.getValue();
                JSONObject deepest = getDeepestParent(cached);
                if (deepest != null && deepest.has(KEY_ID) && !deepest.has("parent")) {
                    String parentId = deepest.getString(KEY_ID);
                    JSONObject parentCached = cache.get(parentId);
                    if (parentCached != null && parentCached.has("parent")) {
                        deepest.put("parent", cloneParentChain(parentCached.getJSONObject("parent")));
                        changed = true;
                    }
                }
            }
            if (!changed) break;
        }
    }

    /**
     * Deep-clone a parent chain, keeping only _id and parent fields.
     */
    private JSONObject cloneParentChain(JSONObject parent) throws JSONException {
        JSONObject clone = new JSONObject();
        clone.put(KEY_ID, parent.getString(KEY_ID));
        JSONObject next = parent.optJSONObject("parent");
        if (next != null) {
            clone.put("parent", cloneParentChain(next));
        }
        return clone;
    }

    /**
     * Classify which accepted docs are TRANSIT (beyond Supervisor's replication_depth).
     *
     * Queries PouchDB's contacts_by_depth view with the Supervisor's facility_id
     * and replication_depth. Any contact not in the result set, or any data_record
     * whose subject is not in the set, is classified as TRANSIT.
     *
     * @return number of transit docs found
     */
    private int classifyTransitDocs(List<JSONObject> acceptedDocs, P2pSession session) {
        try {
            String facilityId = supervisorScope.getFacilitySubtreeRoot();
            int replicationDepth = supervisorScope.getReplicationDepth();

            if (facilityId == null || facilityId.isEmpty() || replicationDepth < 0) {
                Log.w(TAG, "Invalid scope params (facility=" + facilityId
                        + ", depth=" + replicationDepth + "), skipping transit classification");
                return 0;
            }

            // Query PouchDB's contacts_by_depth view for in-scope contact IDs
            String inScopeJson = bridge.queryContactsByDepth(facilityId, replicationDepth);
            if (inScopeJson == null || inScopeJson.isEmpty() || "[]".equals(inScopeJson)) {
                Log.w(TAG, "contacts_by_depth returned empty, treating all as in-scope");
                return 0;
            }

            // Build set of in-scope contact IDs
            Set<String> inScopeIds = new HashSet<>();
            JSONArray inScopeArray = new JSONArray(inScopeJson);
            for (int i = 0; i < inScopeArray.length(); i++) {
                inScopeIds.add(inScopeArray.getString(i));
            }

            Log.d(TAG, "contacts_by_depth: " + inScopeIds.size() + " in-scope contacts");

            // Classify each accepted doc
            List<String> transitDocIds = new ArrayList<>();
            for (JSONObject doc : acceptedDocs) {
                String docId = doc.optString(KEY_ID, "");
                String type = doc.optString("type", "");

                if (isContactType(type)) {
                    // Contact doc: transit if its _id is NOT in the in-scope set
                    if (!inScopeIds.contains(docId)) {
                        transitDocIds.add(docId);
                    }
                } else if ("data_record".equals(type)) {
                    // Data record: transit if its subject contact is NOT in scope
                    String subjectId = getDataRecordSubject(doc);
                    if (subjectId != null && !inScopeIds.contains(subjectId)) {
                        transitDocIds.add(docId);
                    }
                }
                // Other doc types (e.g. task, target) → always in-scope
            }

            if (!transitDocIds.isEmpty()) {
                Log.i(TAG, "Classified " + transitDocIds.size() + " transit docs (tracking already done in doHandle)");
            }

            return transitDocIds.size();

        } catch (JSONException e) {
            Log.e(TAG, "Error classifying transit docs, treating all as in-scope", e);
            return 0;
        }
    }

    /**
     * Check if a doc type is a contact type in CHT.
     */
    private boolean isContactType(String type) {
        return "person".equals(type)
                || "clinic".equals(type)
                || "health_center".equals(type)
                || "district_hospital".equals(type)
                || "contact".equals(type);
    }

    /**
     * Extract the subject contact ID from a data_record.
     * CHT data_records reference their subject via:
     *   - contact._id (the submitter)
     *   - fields.patient_id or patient_id (the patient)
     *   - parent._id (the facility)
     */
    private String getDataRecordSubject(JSONObject doc) {
        // Try contact._id first (most common for reports about a person)
        JSONObject contact = doc.optJSONObject("contact");
        if (contact != null) {
            String contactId = contact.optString(KEY_ID, null);
            if (contactId != null) return contactId;
        }

        // Try fields.patient_id
        JSONObject fields = doc.optJSONObject("fields");
        if (fields != null) {
            String patientId = fields.optString("patient_id", null);
            if (patientId != null && !patientId.isEmpty()) return patientId;
            patientId = fields.optString("patient_uuid", null);
            if (patientId != null && !patientId.isEmpty()) return patientId;
        }

        // Try top-level patient_id
        String patientId = doc.optString("patient_id", null);
        if (patientId != null && !patientId.isEmpty()) return patientId;

        return null;
    }

    private JSONObject buildResponse(int accepted, int transit, int rejected,
                                     JSONArray errors) throws JSONException {
        JSONObject response = new JSONObject();
        response.put("ok", true);
        response.put("accepted", accepted);
        response.put("transit", transit);
        response.put("rejected", rejected);
        if (errors != null && errors.length() > 0) {
            response.put("errors", errors);
        }
        return response;
    }

    private JSONObject errorResponse(String error) {
        try {
            JSONObject response = new JSONObject();
            response.put("ok", false);
            response.put(KEY_ERROR, error);
            return response;
        } catch (JSONException e) {
            throw new IllegalStateException("Failed to build error response", e);
        }
    }
}
