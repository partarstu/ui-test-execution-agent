package org.tarik.ta.agents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.error.RetryPolicy;
import org.tarik.ta.tools.AgentExecutionResult;
import org.tarik.ta.exceptions.ToolExecutionException;

import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.lang.System.currentTimeMillis;
import static java.time.Instant.now;
import static org.tarik.ta.error.ErrorCategory.*;
import static org.tarik.ta.manager.BudgetManager.checkAllBudgets;
import static org.tarik.ta.tools.AgentExecutionResult.ExecutionStatus.*;
import static org.tarik.ta.utils.CommonUtils.captureScreen;
import static org.tarik.ta.utils.CommonUtils.sleepMillis;

public interface BaseAiAgent {
    Logger LOG = LoggerFactory.getLogger(BaseAiAgent.class);

    default AgentExecutionResult<?> executeAndGetResult(Runnable action) {
        checkAllBudgets();
        try {
            action.run();
            return new AgentExecutionResult<>(SUCCESS, "Execution successful", true, null, null, now());
        } catch (Throwable e) {
            LOG.error("Error executing agent action", e);
            return new AgentExecutionResult<>(ERROR, e.getMessage(), false, captureScreen(), null, now());
        }
    }

    default <T> AgentExecutionResult<T> executeAndGetResult(Supplier<T> action) {
        checkAllBudgets();
        try {
            T result = action.get();
            return new AgentExecutionResult<>(SUCCESS, "Execution successful", true, null, result, now());
        } catch (Throwable e) {
            LOG.error("Error executing agent action", e);
            return new AgentExecutionResult<>(ERROR, e.getMessage(), false, captureScreen(), null, now());
        }
    }

    RetryPolicy getRetryPolicy();

    default String getAgentTaskDescription() {
        return "Executing agent task";
    }

    default <T> AgentExecutionResult<T> executeWithRetry(Supplier<T> action, Predicate<T> retryCondition) {
        RetryPolicy policy = getRetryPolicy();
        int attempt = 0;
        long startTime = currentTimeMillis();
        String taskDescription = getAgentTaskDescription();

        while (true) {
            attempt++;
            checkAllBudgets();
            try {
                T result = action.get();
                if (retryCondition != null && retryCondition.test(result)) {
                    String message = "Retry explicitly requested by the task because it has the following result: " + result;
                    AgentExecutionResult<T> errorResult = handleRetry(attempt, startTime, policy, message, taskDescription);
                    if (errorResult != null) {
                        return errorResult;
                    }
                    continue;
                }
                return new AgentExecutionResult<>(SUCCESS, "Execution successful", true, null, result, now());
            } catch (Throwable e) {
                switch (e) {
                    case ToolExecutionException tee when tee.getErrorCategory() == USER_INTERRUPTION -> {
                        LOG.error("User decided to interrupt execution");
                        return new AgentExecutionResult<>(INTERRUPTED_BY_USER, e.getMessage(), false, captureScreen(), null, now());
                    }
                    case ToolExecutionException tee when tee.getErrorCategory() == VERIFICATION_FAILED -> {
                        return new AgentExecutionResult<>(VERIFICATION_FAILURE, e.getMessage(), false, captureScreen(), null, now());
                    }
                    case ToolExecutionException _ -> {
                        String message = "Non-retryable error occurred: %s".formatted(e.getMessage());
                        LOG.error(message, e);
                        return new AgentExecutionResult<>(ERROR, e.getMessage(), false, captureScreen(), null, now());
                    }
                    default -> {
                    }
                }

                LOG.error("Got error while executing action for task: {}. Retrying...", taskDescription, e);
                AgentExecutionResult<T> errorResult = handleRetry(attempt, startTime, policy, e.getMessage(), taskDescription);
                if (errorResult != null) {
                    return errorResult;
                }
            }
        }
    }

    private <T> AgentExecutionResult<T> handleRetry(int attempt, long startTime, RetryPolicy policy, String message, String taskDescription) {
        long elapsedTime = currentTimeMillis() - startTime;
        boolean isTimeout = policy.timeoutMillis() > 0 && elapsedTime > policy.timeoutMillis();
        boolean isMaxRetriesReached = attempt > policy.maxRetries();

        if (isTimeout || isMaxRetriesReached) {
            LOG.error("Operation for task '{}' failed after {} attempts (elapsed: {}ms). Last error: {}", taskDescription, attempt, elapsedTime, message);
            return new AgentExecutionResult<>(ERROR, message, false, captureScreen(), null, now());
        }

        long delayMillis = (long) (policy.initialDelayMillis() * Math.pow(policy.backoffMultiplier(), attempt - 1));
        delayMillis = Math.min(delayMillis, policy.maxDelayMillis());
        LOG.warn("Attempt {} for task '{}' failed: {}. Retrying in {}ms...", attempt, taskDescription, message, delayMillis);
        sleepMillis((int) delayMillis);
        return null;
    }

    default <T> AgentExecutionResult<T> executeWithRetry(Supplier<T> action) {
        return executeWithRetry(action, null);
    }

    default AgentExecutionResult<?> executeWithRetry(Runnable action) {
        return executeWithRetry(() -> {
            action.run();
            return null;
        }, null);
    }
}