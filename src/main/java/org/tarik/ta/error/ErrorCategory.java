package org.tarik.ta.error;

/**
 * Categories of errors that can occur during agent execution.
 * These categories determine the retry strategy and logging level.
 */
public enum ErrorCategory {
    /**
     * User explicitly interrupted the execution.
     * Retry: NO
     * Severity: INFO
     */
    USER_INTERRUPTION,

    /**
     * Verification of a precondition or test step failed.
     * Retry: OPTIONAL (Limited)
     * Severity: WARN
     */
    VERIFICATION_FAILED,

    /**
     * A transient error occurred with a tool or external service (e.g., network
     * glitch).
     * Retry: YES (Exponential backoff)
     * Severity: WARN
     */
    TRANSIENT_TOOL_ERROR,

    /**
     * A fatal error that cannot be recovered from (e.g., invalid configuration).
     * Retry: NO
     * Severity: ERROR
     */
    NON_RETRYABLE_ERROR,

    /**
     * Execution timed out.
     * Retry: YES (Bounded if budget allows)
     * Severity: WARN
     */
    TIMEOUT,

    /**
     * Unknown error category.
     * Retry: NO
     * Severity: ERROR
     */
    UNKNOWN
}
