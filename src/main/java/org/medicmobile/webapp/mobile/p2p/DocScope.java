package org.medicmobile.webapp.mobile.p2p;

/**
 * Classification of a document during P2P sync scope evaluation.
 *
 * IN_SCOPE:  depth from supervisor's facility <= replication_depth.
 *            Written to PouchDB normally, visible in UI.
 *
 * TRANSIT:   depth from supervisor's facility > replication_depth.
 *            Written to PouchDB for server sync + tracked in _local/p2p-transit-docs.
 *            Hidden from UI (contacts, search, tasks, reports, targets).
 *
 * REJECTED:  Doc's facility branch != supervisor's facility branch, or doc is invalid.
 *            Discarded with logged rejection reason.
 */
public enum DocScope {
    IN_SCOPE,
    TRANSIT,
    REJECTED
}
