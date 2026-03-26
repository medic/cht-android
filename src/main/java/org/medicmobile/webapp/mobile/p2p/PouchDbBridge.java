package org.medicmobile.webapp.mobile.p2p;

/**
 * Callback interface for accessing PouchDB data from the WebView.
 *
 * All methods execute synchronously from the HTTP server thread and
 * must internally coordinate with the WebView (e.g. via CountDownLatch +
 * evaluateJavascript). The actual implementation lives in P2pBridgeMethods
 * (Wave 3) — this interface decouples the HTTP layer from the WebView layer.
 *
 * All inputs and outputs are JSON strings.
 */
public interface PouchDbBridge {

    /**
     * Get all doc IDs and revisions from the local PouchDB.
     *
     * @return JSON array of objects: [{"_id": "...", "_rev": "..."}, ...]
     */
    String getAllDocIds();

    /**
     * Get full documents by their IDs.
     *
     * @param idsJson JSON array of ID strings: ["doc1", "doc2", ...]
     * @return JSON array of full doc objects
     */
    String getDocsByIds(String idsJson);

    /**
     * Write documents to PouchDB using bulkDocs with new_edits:false.
     *
     * @param docsJson JSON array of full doc objects (with _id and _rev)
     * @return JSON array of results: [{"ok": true, "id": "...", "rev": "..."}, ...]
     */
    String writeDocs(String docsJson);

    /**
     * Query the medic-client/contacts_by_depth view to get all contact IDs
     * within the Supervisor's replication scope.
     *
     * Uses the same view CHT's server uses for replication filtering:
     *   startkey: [facilityId]
     *   endkey: [facilityId, maxDepth, {}]
     *
     * @param facilityId The Supervisor's facility_id (subtree root)
     * @param maxDepth   The Supervisor's replication_depth
     * @return JSON array of in-scope contact ID strings: ["id1", "id2", ...]
     */
    String queryContactsByDepth(String facilityId, int maxDepth);
}
