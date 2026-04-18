package org.medicmobile.webapp.mobile.p2p;

import java.util.List;

/**
	* Callback interface for tracking transit documents.
	*
	* Transit docs are docs that are outside the Supervisor's replication_depth
	* but need to pass through to reach the server. They are written to PouchDB
	* but must be tracked in _local/p2p-transit-docs so the UI can hide them.
	*
	* The actual implementation lives in TransitDocManager (Wave 2, Agent C).
	* This interface decouples AcceptDocsEndpoint from the transit tracking layer.
	*/
public interface TransitDocCallback {

	/**
		* Track a batch of doc IDs as transit documents.
		*
		* @param docIds		 List of document IDs classified as TRANSIT
		* @param sourceDeviceId The device that sent these docs
		* @param sourceUserId   The user that sent these docs
		*/
	void trackTransitDocs(List<String> docIds, String sourceDeviceId, String sourceUserId);
}
