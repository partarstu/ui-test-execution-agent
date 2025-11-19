package org.tarik.ta.agents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.tools.AgentExecutionResult;
import org.tarik.ta.exceptions.UserInterruptedExecutionException;

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
}
