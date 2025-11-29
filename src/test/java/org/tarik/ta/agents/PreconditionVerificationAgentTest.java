package org.tarik.ta.agents;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import org.junit.jupiter.api.Test;
import org.tarik.ta.dto.VerificationExecutionResult;
import org.tarik.ta.tools.AgentExecutionResult;

import java.io.InputStream;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.tarik.ta.tools.AgentExecutionResult.ExecutionStatus.ERROR;
import static org.tarik.ta.tools.AgentExecutionResult.ExecutionStatus.SUCCESS;

class PreconditionVerificationAgentTest {

    @Test
    void shouldHaveValidSystemPromptPath() {
        SystemMessage annotation = PreconditionVerificationAgent.class.getAnnotation(SystemMessage.class);
        assertThat(annotation).isNotNull();
        String resourcePath = annotation.fromResource();
        assertThat(resourcePath).isNotEmpty();

        try (InputStream stream = getClass().getResourceAsStream(resourcePath)) {
            assertThat(stream).as("System prompt file should exist at " + resourcePath).isNotNull();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void shouldHandleSuccessfulVerification() {
        PreconditionVerificationAgent agent = mock(PreconditionVerificationAgent.class);
        doCallRealMethod().when(agent).executeAndGetResult(any(Supplier.class));

        VerificationExecutionResult verificationResult = new VerificationExecutionResult(true, "Verified");

        AgentExecutionResult<VerificationExecutionResult> result = agent.executeAndGetResult(() -> Result.builder().content(verificationResult).build());

        assertThat(result.executionStatus()).isEqualTo(SUCCESS);
        assertThat(result.success()).isTrue();
        assertThat(result.resultPayload()).isEqualTo(verificationResult);
    }

    @Test
    void shouldHandleFailedVerificationExecution() {
        PreconditionVerificationAgent agent = mock(PreconditionVerificationAgent.class);
        doCallRealMethod().when(agent).executeAndGetResult(any(Supplier.class));

        AgentExecutionResult<VerificationExecutionResult> result = agent.executeAndGetResult(() -> {
            throw new RuntimeException("Verification error");
        });

        assertThat(result.executionStatus()).isEqualTo(ERROR);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Verification error");
        assertThat(result.screenshot()).isNotNull();
    }
}