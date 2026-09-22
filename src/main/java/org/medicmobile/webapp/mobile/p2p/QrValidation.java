package org.medicmobile.webapp.mobile.p2p;

/**
	* Outcome of validating a scanned QR payload: accepted, or rejected with a reason.
	*
	* Deliberately local to the QR pairing code. An earlier iteration reused the document-scope
	* classification result for this, which tied pairing to a data model it has nothing to do with.
	*/
public final class QrValidation {

	private static final QrValidation ACCEPTED_RESULT = new QrValidation(true, null);

	private final boolean accepted;
	private final String reason;

	private QrValidation(boolean accepted, String reason) {
		this.accepted = accepted;
		this.reason = reason;
	}

	public static QrValidation accept() {
		return ACCEPTED_RESULT;
	}

	public static QrValidation reject(String reason) {
		return new QrValidation(false, reason);
	}

	public boolean isAccepted() {
		return accepted;
	}

	/** The failure reason, or {@code null} when accepted. */
	public String getReason() {
		return reason;
	}

	@Override public String toString() {
		return accepted ? "QrValidation{accepted}" : "QrValidation{rejected: " + reason + "}";
	}
}
