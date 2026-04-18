package org.medicmobile.webapp.mobile.p2p;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/**
	* Data model for an active P2P sync session.
	*
	* Tracks session state, counters, and timing. Serializable to JSON
	* for storage in _local/p2p-sync-log.
	*
	*   Session timeout after idle period (checked via isTimedOut)
	*/
public class P2pSession {

	public enum State {
		AUTHENTICATING,
		ACTIVE,
		COMPLETING,
		COMPLETED,
		FAILED
	}

	private static final int DEFAULT_IDLE_TIMEOUT_SEC = 30 * 60; // 30 minutes

	private final String sessionId;
	private final String peerDeviceId;
	private final String peerUserId;
	private final String peerRole;
	private final ScopeManifest peerScope;
	private final long startedAt;

	private volatile long lastActivityAt;
	private volatile State state;

	// Counters
	private int docsPushed;
	private int docsPulled;
	private int docsRejected;
	private int transitDocs;
	private long bytesTransferred;
	private String error;
	private long completedAt;

	public P2pSession(String peerDeviceId, String peerUserId, String peerRole, ScopeManifest peerScope) {
		if (peerDeviceId == null || peerDeviceId.isEmpty()) {
			throw new IllegalArgumentException("peerDeviceId must not be null or empty");
		}
		if (peerUserId == null || peerUserId.isEmpty()) {
			throw new IllegalArgumentException("peerUserId must not be null or empty");
		}

		this.sessionId = UUID.randomUUID().toString();
		this.peerDeviceId = peerDeviceId;
		this.peerUserId = peerUserId;
		this.peerRole = peerRole;
		this.peerScope = peerScope;
		this.startedAt = System.currentTimeMillis();
		this.lastActivityAt = this.startedAt;
		this.state = State.AUTHENTICATING;

		this.docsPushed = 0;
		this.docsPulled = 0;
		this.docsRejected = 0;
		this.transitDocs = 0;
		this.bytesTransferred = 0;
		this.error = null;
		this.completedAt = 0;
	}

	// --- Getters ---

	public String getSessionId() {
		return sessionId;
	}

	public String getPeerDeviceId() {
		return peerDeviceId;
	}

	public String getPeerUserId() {
		return peerUserId;
	}

	public String getPeerRole() {
		return peerRole;
	}

	public ScopeManifest getPeerScope() {
		return peerScope;
	}

	public long getStartedAt() {
		return startedAt;
	}

	public long getLastActivityAt() {
		return lastActivityAt;
	}

	public synchronized State getState() {
		return state;
	}

	public int getDocsPushed() {
		return docsPushed;
	}

	public int getDocsPulled() {
		return docsPulled;
	}

	public int getDocsRejected() {
		return docsRejected;
	}

	public int getTransitDocs() {
		return transitDocs;
	}

	public long getBytesTransferred() {
		return bytesTransferred;
	}

	public String getError() {
		return error;
	}

	public long getCompletedAt() {
		return completedAt;
	}

	// --- Increment methods ---

	public synchronized void incrementDocsPushed(int count) {
		this.docsPushed += count;
		updateLastActivity();
	}

	public synchronized void incrementDocsPulled(int count) {
		this.docsPulled += count;
		updateLastActivity();
	}

	public synchronized void incrementDocsRejected(int count) {
		this.docsRejected += count;
		updateLastActivity();
	}

	public synchronized void incrementTransitDocs(int count) {
		this.transitDocs += count;
		updateLastActivity();
	}

	public synchronized void addBytesTransferred(long bytes) {
		this.bytesTransferred += bytes;
		updateLastActivity();
	}

	/**
		* Record activity to prevent timeout.
		*/
	public void updateLastActivity() {
		this.lastActivityAt = System.currentTimeMillis();
	}

	// --- Timeout check ---

	/**
		* Check if this session has timed out due to inactivity.
		*
		* @param timeoutSec idle timeout in seconds (use P2pConfig.getWifiHotspotIdleTimeoutSec()
		*				   or DEFAULT_IDLE_TIMEOUT_SEC for the 30-min guard)
		* @return true if the session has been idle longer than the timeout
		*/
	public boolean isTimedOut(int timeoutSec) {
		long elapsed = System.currentTimeMillis() - lastActivityAt;
		return elapsed > (long) timeoutSec * 1000;
	}

	/**
		* Check timeout using the default 30-minute guard.
		*/
	public boolean isTimedOut() {
		return isTimedOut(DEFAULT_IDLE_TIMEOUT_SEC);
	}

	// --- State transitions ---

	public synchronized void setState(State state) {
		if (state == null) {
			throw new IllegalArgumentException("State must not be null");
		}
		this.state = state;
		updateLastActivity();
	}

	/**
		* Mark session as failed with an error message.
		*/
	public synchronized void fail(String error) {
		this.state = State.FAILED;
		this.error = error;
		this.completedAt = System.currentTimeMillis();
	}

	/**
		* Mark session as successfully completed.
		*/
	public synchronized void complete() {
		this.state = State.COMPLETED;
		this.completedAt = System.currentTimeMillis();
	}

	// --- Serialization ---

	/**
		* Convert to JSON for _local/p2p-sync-log.
		*
		* Output matches the session entry format:
		* {
		*   "session_id": "uuid",
		*   "peer_device_id": "...",
		*   "peer_user": "...",
		*   "peer_role": "...",
		*   "started_at": 1711152000000,
		*   "completed_at": 1711152300000,
		*   "docs_pushed": 47,
		*   "docs_pulled": 3,
		*   "docs_rejected": 0,
		*   "transit_docs": 42,
		*   "bytes_transferred": 245000,
		*   "status": "completed",
		*   "error": null
		* }
		*/
	public JSONObject toJson() throws JSONException {
		JSONObject json = new JSONObject();
		json.put("session_id", sessionId);
		json.put("peer_device_id", peerDeviceId);
		json.put("peer_user", peerUserId);
		json.put("peer_role", peerRole);
		json.put("started_at", startedAt);
		json.put("completed_at", completedAt > 0 ? completedAt : JSONObject.NULL);
		json.put("docs_pushed", docsPushed);
		json.put("docs_pulled", docsPulled);
		json.put("docs_rejected", docsRejected);
		json.put("transit_docs", transitDocs);
		json.put("bytes_transferred", bytesTransferred);
		json.put("status", mapStateToStatus());
		json.put("error", error != null ? error : JSONObject.NULL);
		return json;
	}

	/**
		* Duration in milliseconds from start to now (if active) or start to completion.
		*/
	public long getDurationMs() {
		long end = completedAt > 0 ? completedAt : System.currentTimeMillis();
		return end - startedAt;
	}

	@Override
	public String toString() {
		return "P2pSession{" +
				"id=" + sessionId +
				", peer=" + peerUserId +
				", state=" + state +
				", pushed=" + docsPushed +
				", pulled=" + docsPulled +
				", transit=" + transitDocs +
				", rejected=" + docsRejected +
				'}';
	}

	// --- Private helpers ---

	private String mapStateToStatus() {
		switch (state) {
			case COMPLETED:
				return "completed";
			case FAILED:
				return "failed";
			default:
				return "in_progress";
		}
	}
}
