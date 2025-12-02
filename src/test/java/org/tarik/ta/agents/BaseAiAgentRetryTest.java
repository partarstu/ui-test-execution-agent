package org.tarik.ta.agents;

import dev.langchain4j.service.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tarik.ta.dto.FinalResult;
import org.tarik.ta.error.RetryPolicy;
import org.tarik.ta.tools.AgentExecutionResult;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tarik.ta.tools.AgentExecutionResult.ExecutionStatus.ERROR;
import static org.tarik.ta.tools.AgentExecutionResult.ExecutionStatus.SUCCESS;

class BaseAiAgentRetryTest {

    record TestResult(String value) implements FinalResult<TestResult> {}

    // Concrete implementation for testing default methods
    static class TestAgent implements BaseAiAgent<TestResult> {
        private RetryPolicy retryPolicy;

        public void setRetryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = retryPolicy;
        }

        @Override
        public RetryPolicy getRetryPolicy() {
            return retryPolicy;
        }
    }

    private final TestAgent agent = new TestAgent();

    @Test
    @DisplayName("Should succeed on first attempt without retries")
    void shouldSucceedOnFirstAttempt() {
        // Given
        RetryPolicy policy = new RetryPolicy(3, 10, 100, 2.0, 1000);
        agent.setRetryPolicy(policy);
        Supplier<Result<String>> action = () -> Result.<TestResult>builder().content(new TestResult("Success").toString()).build();

        // When
        AgentExecutionResult<TestResult> result = agent.executeWithRetry(action);

        // Then
        assertThat(result.executionStatus()).isEqualTo(SUCCESS);
        assertThat(result.resultPayload().value()).isEqualTo("Success");
    }

    @Test
    @DisplayName("Should retry and succeed eventually")
    void shouldRetryAndSucceed() {
        // Given
        RetryPolicy policy = new RetryPolicy(3, 10, 100, 2.0, 1000);
        agent.setRetryPolicy(policy);
        AtomicInteger attempts = new AtomicInteger(0);
        Supplier<Result<String>> action = () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new RuntimeException("Transient error");
            }
            return Result.<String>builder().content("Success").build();
        };

        // When
        var result = agent.executeWithRetry(action);

        // Then
        assertThat(result.executionStatus()).isEqualTo(SUCCESS);
        assertThat(result.resultPayload().value()).isEqualTo("Success");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should fail after max retries")
    void shouldFailAfterMaxRetries() {
        // Given
        RetryPolicy policy = new RetryPolicy(2, 10, 100, 2.0, 1000);
        agent.setRetryPolicy(policy);
        AtomicInteger attempts = new AtomicInteger(0);
        Supplier<Result<TestResult>> action = () -> {
            attempts.incrementAndGet();
            throw new RuntimeException("Persistent error");
        };

        // When
        AgentExecutionResult<TestResult> result = agent.executeWithRetry(action);

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
        agent.setRetryPolicy(policy);
        Supplier<Result<TestResult>> action = () -> {
            throw new RuntimeException("Slow error");
        };

        // When
        AgentExecutionResult<TestResult> result = agent.executeWithRetry(action);

        // Then
        assertThat(result.executionStatus()).isEqualTo(ERROR);
        assertThat(result.message()).isEqualTo("Slow error");
    }

    @Test
    @DisplayName("Should not retry on NON_RETRYABLE_ERROR")
    void shouldNotRetryOnNonRetryableError() {
        // Given
        RetryPolicy policy = new RetryPolicy(3, 10, 100, 2.0, 1000);
        agent.setRetryPolicy(policy);
        AtomicInteger attempts = new AtomicInteger(0);
        Supplier<Result<TestResult>> action = () -> {
            attempts.incrementAndGet();
            throw new org.tarik.ta.exceptions.ToolExecutionException("Fatal error",
                    org.tarik.ta.error.ErrorCategory.NON_RETRYABLE_ERROR);
        };

        // When
        AgentExecutionResult<TestResult> result = agent.executeWithRetry(action);

        // Then
        assertThat(result.executionStatus()).isEqualTo(ERROR);
        assertThat(result.message()).isEqualTo("Fatal error");
        assertThat(attempts.get()).isEqualTo(1); // Should not retry
    }

    @Test
    @DisplayName("Should retry on predicate match")
    void shouldRetryOnPredicateMatch() {
        // Given
        RetryPolicy policy = new RetryPolicy(3, 100, 100, 1.0, 1000);
        agent.setRetryPolicy(policy);
        AtomicInteger attempts = new AtomicInteger(0);
        Supplier<Result<TestResult>> action = () -> {
            attempts.incrementAndGet();
            return Result.<TestResult>builder().content(new TestResult("Failed")).build();
        };

        // When
        // Retry if result is "Failed"
        AgentExecutionResult<TestResult> result = agent.executeWithRetry(action, res -> "Failed".equals(res.value()));

        // Then
        assertThat(result.executionStatus()).isEqualTo(ERROR);
        assertThat(result.message()).contains("Retry explicitly requested by the task");
        assertThat(attempts.get()).isGreaterThan(1);
    }

    @Test
    @DisplayName("Should succeed when predicate stops matching")
    void shouldSucceedWhenPredicateStopsMatching() {
        // Given
        RetryPolicy policy = new RetryPolicy(3, 100, 100, 1.0, 1000);
        agent.setRetryPolicy(policy);
        AtomicInteger attempts = new AtomicInteger(0);
        Supplier<Result<TestResult>> action = () -> {
            if (attempts.incrementAndGet() < 3) {
                return Result.<TestResult>builder().content(new TestResult("Failed")).build();
            }
            return Result.<TestResult>builder().content(new TestResult("Success")).build();
        };

        // When
        AgentExecutionResult<TestResult> result = agent.executeWithRetry(action, res -> "Failed".equals(res.value()));

        // Then
        assertThat(result.executionStatus()).isEqualTo(SUCCESS);
        assertThat(result.resultPayload().value()).isEqualTo("Success");
        assertThat(attempts.get()).isEqualTo(3);
    }
}
