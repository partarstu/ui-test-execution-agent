package org.tarik.ta.agents;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tarik.ta.error.RetryPolicy;
import org.tarik.ta.exceptions.UserInterruptedExecutionException;
import org.tarik.ta.tools.AgentExecutionResult;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.tarik.ta.tools.AgentExecutionResult.ExecutionStatus.ERROR;
import static org.tarik.ta.tools.AgentExecutionResult.ExecutionStatus.SUCCESS;

class BaseAiAgentRetryTest {

    // Concrete implementation for testing default methods
    static class TestAgent implements BaseAiAgent {
    }

    private final TestAgent agent = new TestAgent();

    @Test
    @DisplayName("Should succeed on first attempt without retries")
    void shouldSucceedOnFirstAttempt() {
        // Given
        RetryPolicy policy = new RetryPolicy(3, 10, 100, 2.0, 1000);
        Supplier<String> action = () -> "Success";

        // When
        AgentExecutionResult<String> result = agent.executeWithRetry(action, policy);

        // Then
        assertThat(result.executionStatus()).isEqualTo(SUCCESS);
        assertThat(result.resultPayload()).isEqualTo("Success");
    }

    @Test
    @DisplayName("Should retry and succeed eventually")
    void shouldRetryAndSucceed() {
        // Given
        RetryPolicy policy = new RetryPolicy(3, 10, 100, 2.0, 1000);
        AtomicInteger attempts = new AtomicInteger(0);
        Supplier<String> action = () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new RuntimeException("Transient error");
            }
            return "Success";
        };

        // When
        AgentExecutionResult<String> result = agent.executeWithRetry(action, policy);

        // Then
        assertThat(result.executionStatus()).isEqualTo(SUCCESS);
        assertThat(result.resultPayload()).isEqualTo("Success");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should fail after max retries")
    void shouldFailAfterMaxRetries() {
        // Given
        RetryPolicy policy = new RetryPolicy(2, 10, 100, 2.0, 1000);
        AtomicInteger attempts = new AtomicInteger(0);
        Supplier<String> action = () -> {
            attempts.incrementAndGet();
            throw new RuntimeException("Persistent error");
        };

        // When
        AgentExecutionResult<String> result = agent.executeWithRetry(action, policy);

        // Then
        assertThat(result.executionStatus()).isEqualTo(ERROR);
        assertThat(result.message()).isEqualTo("Persistent error");
        assertThat(attempts.get()).isGreaterThan(2); // Initial + 2 retries = 3 attempts
    }

    @Test
    @DisplayName("Should fail on timeout")
    void shouldFailOnTimeout() {
        // Given
        // Short timeout, long delay
        RetryPolicy policy = new RetryPolicy(10, 100, 100, 1.0, 50);
        Supplier<String> action = () -> {
            throw new RuntimeException("Slow error");
        };

        // When
        AgentExecutionResult<String> result = agent.executeWithRetry(action, policy);

        // Then
        assertThat(result.executionStatus()).isEqualTo(ERROR);
        assertThat(result.message()).isEqualTo("Slow error");
    }

    @Test
    @DisplayName("Should propagate UserInterruptedExecutionException immediately")
    void shouldPropagateInterruption() {
        // Given
        RetryPolicy policy = new RetryPolicy(3, 10, 100, 2.0, 1000);
        Supplier<String> action = () -> {
            throw new UserInterruptedExecutionException();
        };

        // When/Then
        assertThatThrownBy(() -> agent.executeWithRetry(action, policy))
                .isInstanceOf(UserInterruptedExecutionException.class);
    }

    @Test
    @DisplayName("Should not retry on NON_RETRYABLE_ERROR")
    void shouldNotRetryOnNonRetryableError() {
        // Given
        RetryPolicy policy = new RetryPolicy(3, 10, 100, 2.0, 1000);
        AtomicInteger attempts = new AtomicInteger(0);
        Supplier<String> action = () -> {
            attempts.incrementAndGet();
            throw new org.tarik.ta.exceptions.ToolExecutionException("Fatal error",
                    org.tarik.ta.error.ErrorCategory.NON_RETRYABLE_ERROR);
        };

        // When
        AgentExecutionResult<String> result = agent.executeWithRetry(action, policy);

        // Then
        assertThat(result.executionStatus()).isEqualTo(ERROR);
        assertThat(result.message()).isEqualTo("Fatal error");
        assertThat(attempts.get()).isEqualTo(1); // Should not retry
    }
}
