package org.tarik.ta.error;

/**
 * Configuration for retry logic.
 *
 * @param maxRetries         Maximum number of retry attempts.
 * @param initialDelayMillis Initial delay before the first retry in
 *                           milliseconds.
 * @param maxDelayMillis     Maximum delay between retries in milliseconds.
 * @param backoffMultiplier  Multiplier for exponential backoff.
 * @param timeoutMillis      Total timeout for the operation including retries.
 */
public record RetryPolicy(
        int maxRetries,
        long initialDelayMillis,
        long maxDelayMillis,
        double backoffMultiplier,
        long timeoutMillis) {
    /**
     * Creates a default retry policy for transient errors.
     */
    public static RetryPolicy defaultTransientPolicy() {
        return new RetryPolicy(3, 1000, 5000, 2.0, 30000);
    }

    /**
     * Creates a policy with no retries.
     */
    public static RetryPolicy noRetry() {
        return new RetryPolicy(0, 0, 0, 1.0, 0);
    }
}
