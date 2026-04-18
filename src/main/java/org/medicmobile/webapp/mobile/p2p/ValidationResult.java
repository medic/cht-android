package org.medicmobile.webapp.mobile.p2p;

/**
	* Immutable result of ScopeGuard.classify() for a single document.
	*
	* Use the static factory methods:
	*   ValidationResult.accept(DocScope.IN_SCOPE)
	*   ValidationResult.reject("reason string")
	*/
public final class ValidationResult {

	private final boolean accepted;
	private final DocScope scope;
	private final String reason;

	private ValidationResult(boolean accepted, DocScope scope, String reason) {
		this.accepted = accepted;
		this.scope = scope;
		this.reason = reason;
	}

	/** Create an accepted result with the given scope (IN_SCOPE or TRANSIT). */
	public static ValidationResult accept(DocScope scope) {
		if (scope == null || scope == DocScope.REJECTED) {
			throw new IllegalArgumentException("accept() requires IN_SCOPE or TRANSIT, got: " + scope);
		}
		return new ValidationResult(true, scope, null);
	}

	/** Create a rejected result with a human-readable reason. */
	public static ValidationResult reject(String reason) {
		if (reason == null || reason.isEmpty()) {
			throw new IllegalArgumentException("reject() requires a non-empty reason");
		}
		return new ValidationResult(false, DocScope.REJECTED, reason);
	}

	public boolean isAccepted() {
		return accepted;
	}

	public DocScope getScope() {
		return scope;
	}

	/** Null when accepted, non-null when rejected. */
	public String getReason() {
		return reason;
	}

	@Override
	public String toString() {
		if (accepted) {
			return "ValidationResult{accepted=true, scope=" + scope + "}";
		}
		return "ValidationResult{accepted=false, reason=\"" + reason + "\"}";
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ValidationResult that = (ValidationResult) o;
		return accepted == that.accepted
				&& scope == that.scope
				&& (reason == null ? that.reason == null : reason.equals(that.reason));
	}

	@Override
	public int hashCode() {
		int result = Boolean.hashCode(accepted);
		result = 31 * result + (scope != null ? scope.hashCode() : 0);
		result = 31 * result + (reason != null ? reason.hashCode() : 0);
		return result;
	}
}
