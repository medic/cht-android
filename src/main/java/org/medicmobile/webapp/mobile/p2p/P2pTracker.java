package org.medicmobile.webapp.mobile.p2p;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
	* Tracks P2P sync sessions and accumulates metrics.
	*
	* Session data is stored in:
	* - _local/p2p-sync-log  (CHW side)  —
	* - _local/p2p-relay-log (Supervisor side) —
	*
	* This class is the single source of truth for session history during
	* the app lifecycle. Completed sessions are serialized to _local/ docs
	* via the WebView bridge for persistence across app restarts.
	*/
public class P2pTracker {

	private static final String TAG = "P2pTracker";
	private static final String KEY_SESSIONS = "sessions";

	private final List<P2pSession> completedSessions = new ArrayList<>();
	private P2pSession currentSession;

	/**
		* Start tracking a new P2P session.
		*
		* @param peerDeviceId the peer's device ID
		* @param peerUserId   the peer's user ID
		* @param peerRole	 the peer's role (chw, chw_supervisor)
		* @param peerScope	the peer's scope manifest
		* @return the new session object for counter updates
		*/
	public synchronized P2pSession startSession(String peerDeviceId, String peerUserId,
									String peerRole, ScopeManifest peerScope) {
		if (currentSession != null) {
			Log.w(TAG, "Starting new session while previous session is still active. " +
					"Marking previous session as failed.");
			failSession("Replaced by new session");
		}
		currentSession = new P2pSession(peerDeviceId, peerUserId, peerRole, peerScope);
		currentSession.setState(P2pSession.State.ACTIVE);
		Log.i(TAG, "Started P2P session: " + currentSession.getSessionId() +
				" with peer " + peerUserId);
		return currentSession;
	}

	/**
		* Complete the current session successfully.
		*/
	public synchronized void completeSession() {
		if (currentSession == null) {
			Log.w(TAG, "completeSession() called with no active session");
			return;
		}
		currentSession.complete();
		completedSessions.add(currentSession);
		Log.i(TAG, "Completed P2P session: " + currentSession);
		currentSession = null;
	}

	/**
		* Fail the current session with an error.
		*
		* @param error human-readable error description
		*/
	public synchronized void failSession(String error) {
		if (currentSession == null) {
			Log.w(TAG, "failSession() called with no active session");
			return;
		}
		currentSession.fail(error);
		completedSessions.add(currentSession);
		Log.w(TAG, "Failed P2P session: " + currentSession + " error=" + error);
		currentSession = null;
	}

	/**
		* Get current active session (may be null if no session in progress).
		*/
	public P2pSession getCurrentSession() {
		return currentSession;
	}

	/**
		* Check if there is an active session in progress.
		*/
	public boolean hasActiveSession() {
		return currentSession != null;
	}

	/**
		* Build the _local/p2p-sync-log JSON (CHW side).
		*
		* FORMAT from :
		* {
		*   "_id": "_local/p2p-sync-log",
		*   KEY_SESSIONS: [
		*	 {
		*	   "session_id": "uuid",
		*	   "peer_device_id": "supervisor-device-uuid",
		*	   "peer_user": "supervisor-jane",
		*	   "started_at": 1711152000000,
		*	   "completed_at": 1711152300000,
		*	   "docs_pushed": 47,
		*	   "docs_pulled": 3,
		*	   "bytes_transferred": 245000,
		*	   "status": "completed | failed | interrupted",
		*	   "error": null
		*	 }
		*   ]
		* }
		*/
	public JSONObject buildSyncLog() throws JSONException {
		JSONObject log = new JSONObject();
		log.put("_id", "_local/p2p-sync-log");
		log.put(KEY_SESSIONS, buildSessionsArray());
		return log;
	}

	/**
		* Build the _local/p2p-relay-log JSON (Supervisor side).
		*
		* FORMAT from :
		* {
		*   "_id": "_local/p2p-relay-log",
		*   KEY_SESSIONS: [
		*	 {
		*	   "session_id": "uuid",
		*	   "source_device_id": "chw-device-uuid",
		*	   "source_user": "chw-mary",
		*	   "started_at": 1711152000000,
		*	   "completed_at": 1711152300000,
		*	   "docs_received": 47,
		*	   "in_scope_count": 5,
		*	   "transit_count": 42,
		*	   "rejected_count": 0,
		*	   "bytes_received": 245000,
		*	   "status": "completed | failed | interrupted"
		*	 }
		*   ]
		* }
		*/
	public JSONObject buildRelayLog() throws JSONException {
		JSONObject log = new JSONObject();
		log.put("_id", "_local/p2p-relay-log");
		JSONArray sessions = new JSONArray();
		for (P2pSession session : completedSessions) {
			sessions.put(buildRelaySessionEntry(session));
		}
		log.put(KEY_SESSIONS, sessions);
		return log;
	}

	/**
		* Load existing sessions from a previously saved sync log JSON.
		* This restores state after app restart.
		*
		* @param logJson the _local/p2p-sync-log or _local/p2p-relay-log JSON
		*/
	public void loadFromSyncLog(JSONObject logJson) throws JSONException {
		// We only load completed session count for telemetry purposes.
		// Actual session objects are not reconstructed — they are historical data.
		// The JSON is passed through directly when building telemetry.
		if (logJson.has(KEY_SESSIONS)) {
			Log.i(TAG, "Loaded sync log with " +
					logJson.getJSONArray(KEY_SESSIONS).length() + " historical sessions");
		}
	}

	/**
		* Record an externally-completed session (e.g., from LocalHttpServer).
		* Used when the session lifecycle is managed outside the tracker
		* (host-side sessions are managed by LocalHttpServer, not tracker).
		*/
	public synchronized void recordCompletedSession(P2pSession session) {
		if (session == null) {
			return;
		}
		completedSessions.add(session);
		Log.i(TAG, "Recorded externally-completed session: " + session.getSessionId());
	}

	/**
		* Get count of completed sessions tracked in memory.
		*/
	public int getSessionCount() {
		return completedSessions.size();
	}

	/**
		* Get all completed sessions (for telemetry reporting).
		*/
	public synchronized List<P2pSession> getCompletedSessions() {
		return new ArrayList<>(completedSessions);
	}

	/**
		* Clear completed sessions after successful telemetry upload.
		*/
	public synchronized void clearCompletedSessions() {
		completedSessions.clear();
		Log.i(TAG, "Cleared completed sessions after telemetry upload");
	}

	// --- Private helpers ---

	private JSONArray buildSessionsArray() throws JSONException {
		JSONArray sessions = new JSONArray();
		for (P2pSession session : completedSessions) {
			sessions.put(session.toJson());
		}
		return sessions;
	}

	/**
		* Build a relay log session entry — uses Supervisor-side field names
		* from  (_local/p2p-relay-log format).
		*/
	private JSONObject buildRelaySessionEntry(P2pSession session) throws JSONException {
		JSONObject entry = new JSONObject();
		entry.put("session_id", session.getSessionId());
		entry.put("source_device_id", session.getPeerDeviceId());
		entry.put("source_user", session.getPeerUserId());
		entry.put("started_at", session.getStartedAt());
		entry.put("completed_at", session.getCompletedAt() > 0
				? session.getCompletedAt() : JSONObject.NULL);
		// Supervisor receives docs (pushed by CHW), so docs_pushed = docs_received from relay perspective
		entry.put("docs_received", session.getDocsPushed() + session.getTransitDocs());
		entry.put("in_scope_count", session.getDocsPushed());
		entry.put("transit_count", session.getTransitDocs());
		entry.put("rejected_count", session.getDocsRejected());
		entry.put("bytes_received", session.getBytesTransferred());
		entry.put("status", mapSessionStatus(session));
		return entry;
	}

	private String mapSessionStatus(P2pSession session) {
		switch (session.getState()) {
			case COMPLETED:
				return "completed";
			case FAILED:
				return "failed";
			default:
				return "interrupted";
		}
	}
}
