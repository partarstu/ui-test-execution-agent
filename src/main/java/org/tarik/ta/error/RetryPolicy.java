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
}
