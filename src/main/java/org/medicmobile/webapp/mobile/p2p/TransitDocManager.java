package org.medicmobile.webapp.mobile.p2p;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
	* Manages transit docs that pass through the Supervisor to the server.
	*
	* Transit docs are written to the main PouchDB (for server sync) but tracked
	* in _local/p2p-transit-docs (never replicates) so the webapp can:
	* 1. Filter them from UI
	* 2. Purge them after server push (db.purge() ONLY, never db.remove())
	*
	* CRITICAL INVARIANTS:
	* - Classification is deterministic (handled by ScopeGuard)
	* - Transit docs NEVER appear in UI
	* - Transit index loads in <50ms
	* - Purge uses db.purge() NOT db.remove()
	* - _local/ doc must not exceed 1MB
	* - Unpushed transit docs >30 days → user notification
	*
	* Schema from .
	*/
public final class TransitDocManager {

	static final String TRANSIT_DOC_ID = "_local/p2p-transit-docs";
	private static final long MAX_LOCAL_DOC_SIZE = 1024L * 1024; // 1MB limit
	private static final long STALE_THRESHOLD_MS = 30L * 24 * 60 * 60 * 1000; // 30 days
	private static final String KEY_PUSHED_AT = "pushed_at";
	private static final String KEY_PURGED_AT = "purged_at";

	private final Map<String, TransitBatch> batches = new ConcurrentHashMap<>();
	private final Map<String, String> transitIndex = new ConcurrentHashMap<>(); // docId -> batchId
	private final AtomicInteger totalReceived = new AtomicInteger(0);
	private final AtomicInteger totalPushed = new AtomicInteger(0);
	private final AtomicInteger totalPurged = new AtomicInteger(0);

	/**
		* Load existing transit state from _local/p2p-transit-docs JSON.
		* Called at startup to restore state.
		*/
	public synchronized void loadFromJson(JSONObject transitDoc) throws JSONException {
		if (transitDoc == null) {
			return;
		}

		loadBatches(transitDoc.optJSONObject("batches"));
		loadTransitIndex(transitDoc.optJSONObject("transit_index"));
		loadStats(transitDoc.optJSONObject("stats"));
	}

	private void loadBatches(JSONObject batchesJson) throws JSONException {
		if (batchesJson == null) {
			return;
		}
		Iterator<String> keys = batchesJson.keys();
		while (keys.hasNext()) {
			String batchId = keys.next();
			JSONObject batchJson = batchesJson.getJSONObject(batchId);
			TransitBatch batch = parseBatch(batchId, batchJson);
			batches.put(batchId, batch);
		}
	}

	private TransitBatch parseBatch(String batchId, JSONObject batchJson) throws JSONException {
		TransitBatch batch = new TransitBatch(
				batchId,
				batchJson.optString("source_device_id", ""),
				batchJson.optString("source_user", ""),
				batchJson.optLong("received_at", 0)
		);
		batch.docCount = batchJson.optInt("doc_count", 0);
		batch.pushedToServer = batchJson.optBoolean("pushed_to_server", false);
		batch.pushedAt = !batchJson.isNull(KEY_PUSHED_AT) ? batchJson.getLong(KEY_PUSHED_AT) : null;
		batch.purged = batchJson.optBoolean("purged", false);
		batch.purgedAt = !batchJson.isNull(KEY_PURGED_AT) ? batchJson.getLong(KEY_PURGED_AT) : null;
		return batch;
	}

	private void loadTransitIndex(JSONObject indexJson) throws JSONException {
		if (indexJson == null) {
			return;
		}
		Iterator<String> keys = indexJson.keys();
		while (keys.hasNext()) {
			String docId = keys.next();
			transitIndex.put(docId, indexJson.getString(docId));
		}
	}

	private void loadStats(JSONObject stats) {
		if (stats == null) {
			return;
		}
		totalReceived.set(stats.optInt("total_received", 0));
		totalPushed.set(stats.optInt("total_pushed", 0));
		totalPurged.set(stats.optInt("total_purged", 0));
	}

	/**
		* Start a new transit batch for incoming docs from a CHW.
		* @return batch ID
		*/
	public String startBatch(String sourceDeviceId, String sourceUser) {
		String batchId = UUID.randomUUID().toString();
		TransitBatch batch = new TransitBatch(batchId, sourceDeviceId, sourceUser, System.currentTimeMillis());
		batches.put(batchId, batch);
		return batchId;
	}

	/**
		* Track a doc as transit within the current batch.
		*/
	public void trackTransitDoc(String batchId, String docId) {
		transitIndex.put(docId, batchId);
		TransitBatch batch = batches.get(batchId);
		if (batch != null) {
			synchronized (batch) {
				batch.docCount++;
			}
		}
		totalReceived.incrementAndGet();
	}

	/**
		* Check if a doc ID is a transit doc (for UI filtering ).
		* This method MUST be fast (<50ms for the entire index).
		*/
	public boolean isTransitDoc(String docId) {
		return transitIndex.containsKey(docId);
	}

	/**
		* Get all transit doc IDs (for bulk UI filtering).
		*/
	public Set<String> getAllTransitDocIds() {
		return new HashSet<>(transitIndex.keySet());
	}

	/**
		* Get transit doc IDs for a specific batch (for purging).
		*/
	public Set<String> getDocIdsForBatch(String batchId) {
		Set<String> docIds = new HashSet<>();
		for (Map.Entry<String, String> entry : transitIndex.entrySet()) {
			if (batchId.equals(entry.getValue())) {
				docIds.add(entry.getKey());
			}
		}
		return docIds;
	}

	/**
		* Mark a batch as successfully pushed to server.
		*/
	public void markBatchPushed(String batchId) {
		TransitBatch batch = batches.get(batchId);
		if (batch != null) {
			synchronized (batch) {
				batch.pushedToServer = true;
				batch.pushedAt = System.currentTimeMillis();
				totalPushed.addAndGet(batch.docCount);
			}
		}
	}

	/**
		* Mark a batch as purged (after db.purge() confirmed).
		* MUST use db.purge() — NEVER db.remove()
		*/
	public void markBatchPurged(String batchId) {
		TransitBatch batch = batches.get(batchId);
		if (batch != null) {
			synchronized (batch) {
				batch.purged = true;
				batch.purgedAt = System.currentTimeMillis();
				totalPurged.addAndGet(batch.docCount);
			}
			// Remove from transit index (ConcurrentHashMap supports concurrent removeIf)
			transitIndex.entrySet().removeIf(e -> batchId.equals(e.getValue()));
		}
	}

	/**
		* Get batch IDs that are pushed but not yet purged.
		*/
	public Set<String> getPurgeableBatchIds() {
		Set<String> purgeable = new HashSet<>();
		for (Map.Entry<String, TransitBatch> entry : batches.entrySet()) {
			TransitBatch batch = entry.getValue();
			if (batch.pushedToServer && !batch.purged) {
				purgeable.add(entry.getKey());
			}
		}
		return purgeable;
	}

	/**
		* Check if there are stale (unpushed >30 days) transit batches.
		*/
	public boolean hasStaleTransitDocs() {
		long now = System.currentTimeMillis();
		for (TransitBatch batch : batches.values()) {
			if (!batch.pushedToServer && !batch.purged && (now - batch.receivedAt) > STALE_THRESHOLD_MS) {
				return true;
			}
		}
		return false;
	}

	/** Get count of pending (unpushed) transit docs. */
	public int getPendingPushCount() {
		int pending = 0;
		for (TransitBatch batch : batches.values()) {
			if (!batch.pushedToServer && !batch.purged) {
				pending += batch.docCount;
			}
		}
		return pending;
	}

	/**
		* Build the _local/p2p-transit-docs JSON for saving.
		* Schema from .
		*/
	public JSONObject toJson() throws JSONException {
		JSONObject doc = new JSONObject();
		doc.put("_id", TRANSIT_DOC_ID);
		doc.put("batches", batchesToJson());
		doc.put("transit_index", transitIndexToJson());
		doc.put("stats", statsToJson());
		return doc;
	}

	private JSONObject batchesToJson() throws JSONException {
		JSONObject batchesJson = new JSONObject();
		for (Map.Entry<String, TransitBatch> entry : batches.entrySet()) {
			batchesJson.put(entry.getKey(), batchToJson(entry.getValue()));
		}
		return batchesJson;
	}

	private JSONObject batchToJson(TransitBatch b) throws JSONException {
		JSONObject batchJson = new JSONObject();
		batchJson.put("source_device_id", b.sourceDeviceId);
		batchJson.put("source_user", b.sourceUser);
		batchJson.put("received_at", b.receivedAt);
		batchJson.put("doc_count", b.docCount);
		batchJson.put("pushed_to_server", b.pushedToServer);
		batchJson.put(KEY_PUSHED_AT, b.pushedAt != null ? b.pushedAt : JSONObject.NULL);
		batchJson.put("purged", b.purged);
		batchJson.put(KEY_PURGED_AT, b.purgedAt != null ? b.purgedAt : JSONObject.NULL);
		return batchJson;
	}

	private JSONObject transitIndexToJson() throws JSONException {
		JSONObject indexJson = new JSONObject();
		for (Map.Entry<String, String> entry : transitIndex.entrySet()) {
			indexJson.put(entry.getKey(), entry.getValue());
		}
		return indexJson;
	}

	private JSONObject statsToJson() throws JSONException {
		JSONObject stats = new JSONObject();
		stats.put("total_received", totalReceived.get());
		stats.put("total_pushed", totalPushed.get());
		stats.put("total_purged", totalPurged.get());
		stats.put("pending_push", getPendingPushCount());
		return stats;
	}

	/**
		* Estimate the size of the transit doc in bytes.
		*/
	public long estimateSize() {
		try {
			return toJson().toString().getBytes("UTF-8").length;
		} catch (JSONException | UnsupportedEncodingException e) {
			return 0;
		}
	}

	/**
		* Check if the transit doc exceeds the 1MB limit.
		* If so, archive old purged batches to reduce size.
		*/
	public boolean isOversized() {
		return estimateSize() > MAX_LOCAL_DOC_SIZE;
	}

	/**
		* Remove purged batches to reduce doc size.
		*/
	public void archivePurgedBatches() {
		batches.entrySet().removeIf(e -> e.getValue().purged);
	}

	/** Inner class for batch tracking. */
	private static class TransitBatch {
		final String batchId;
		final String sourceDeviceId;
		final String sourceUser;
		final long receivedAt;
		int docCount;
		boolean pushedToServer;
		Long pushedAt;
		boolean purged;
		Long purgedAt;

		TransitBatch(String batchId, String sourceDeviceId, String sourceUser, long receivedAt) {
			this.batchId = batchId;
			this.sourceDeviceId = sourceDeviceId;
			this.sourceUser = sourceUser;
			this.receivedAt = receivedAt;
		}
	}
}
