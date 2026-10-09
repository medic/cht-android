package org.medicmobile.webapp.mobile.offlinesync;

/**
	* Outcome of validating a scanned QR payload: accepted, or rejected with a code and a detail.
	*
	* The code is what crosses the bridge, so it is a stable token the webapp turns into a
	* translated message. The detail is the prose behind it and is for the log only: a sentence
	* built here cannot be translated, and would reach the user as raw text.
	*/
public final class QrValidation {

	private static final QrValidation ACCEPTED_RESULT = new QrValidation(true, null, null);

	private final boolean accepted;
	private final String code;
	private final String detail;

	private QrValidation(boolean accepted, String code, String detail) {
		this.accepted = accepted;
		this.code = code;
		this.detail = detail;
	}

	public static QrValidation accept() {
		return ACCEPTED_RESULT;
	}

	public static QrValidation reject(String code, String detail) {
		return new QrValidation(false, code, detail);
	}

	public boolean isAccepted() {
		return accepted;
	}

	/** The stable failure code for the webapp, or {@code null} when accepted. */
	public String getCode() {
		return code;
	}

	/** Why it failed, for the log. Not shown to a user, because it cannot be translated. */
	public String getDetail() {
		return detail;
	}

	@Override public String toString() {
		return accepted ? "QrValidation{accepted}" : "QrValidation{rejected: " + code + ": " + detail + "}";
	}
}
