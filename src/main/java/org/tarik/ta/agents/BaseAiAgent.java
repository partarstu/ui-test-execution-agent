package org.tarik.ta.agents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.error.RetryPolicy;
import org.tarik.ta.tools.AgentExecutionResult;
import org.tarik.ta.exceptions.UserInterruptedExecutionException;
import org.tarik.ta.exceptions.ToolExecutionException;

import java.util.function.Supplier;

import static java.time.Instant.now;
import static org.tarik.ta.tools.AgentExecutionResult.ExecutionStatus.ERROR;
import static org.tarik.ta.tools.AgentExecutionResult.ExecutionStatus.SUCCESS;
import static org.tarik.ta.utils.CommonUtils.captureScreen;

public interface BaseAiAgent {
    Logger LOG = LoggerFactory.getLogger(BaseAiAgent.class);

    default AgentExecutionResult<?> executeAndGetResult(Runnable action) {
        try {
            action.run();
            return new AgentExecutionResult<>(SUCCESS, "Execution successful", true, null, null, now());
        } catch (UserInterruptedExecutionException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("Error executing agent action", e);
            return new AgentExecutionResult<>(ERROR, e.getMessage(), false, captureScreen(), null, now());
        }
    }

    default <T> AgentExecutionResult<T> executeAndGetResult(Supplier<T> action) {
        try {
            T result = action.get();
            return new AgentExecutionResult<>(SUCCESS, "Execution successful", true, null, result, now());
        } catch (UserInterruptedExecutionException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("Error executing agent action", e);
            return new AgentExecutionResult<>(ERROR, e.getMessage(), false, captureScreen(), null, now());
        }
    }

    default <T> AgentExecutionResult<T> executeWithRetry(Supplier<T> action, RetryPolicy policy) {
        int attempt = 0;
        long startTime = System.currentTimeMillis();

        while (true) {
            attempt++;
            try {
                T result = action.get();
                return new AgentExecutionResult<>(SUCCESS, "Execution successful", true, null, result, now());
            } catch (UserInterruptedExecutionException e) {
                throw e;
            } catch (Exception e) {
                long elapsedTime = System.currentTimeMillis() - startTime;
                boolean isTimeout = policy.timeoutMillis() > 0 && elapsedTime > policy.timeoutMillis();
                boolean isMaxRetriesReached = attempt > policy.maxRetries();

                // Check if error is non-retryable
                if (e instanceof ToolExecutionException tee
                        && tee.getErrorCategory() == org.tarik.ta.error.ErrorCategory.NON_RETRYABLE_ERROR) {
                    LOG.error("Non-retryable error occurred: {}", e.getMessage());
                    return new AgentExecutionResult<>(ERROR, e.getMessage(), false, captureScreen(), null, now());
                }

                if (isTimeout || isMaxRetriesReached) {
                    LOG.error("Operation failed after {} attempts (elapsed: {}ms). Last error: {}", attempt,
                            elapsedTime, e.getMessage());
                    return new AgentExecutionResult<>(ERROR, e.getMessage(), false, captureScreen(), null, now());
                }

                long delay = (long) (policy.initialDelayMillis() * Math.pow(policy.backoffMultiplier(), attempt - 1));
                delay = Math.min(delay, policy.maxDelayMillis());

                LOG.warn("Attempt {} failed: {}. Retrying in {}ms...", attempt, e.getMessage(), delay);

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new UserInterruptedExecutionException();
                }
            }
        }
    }

    default AgentExecutionResult<?> executeWithRetry(Runnable action, RetryPolicy policy) {
        return executeWithRetry(() -> {
            action.run();
            return null;
        }, policy);
    }
}
