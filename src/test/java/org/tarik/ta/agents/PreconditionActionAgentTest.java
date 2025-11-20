package org.tarik.ta.agents;

import dev.langchain4j.service.SystemMessage;
import org.junit.jupiter.api.Test;
import org.tarik.ta.tools.AgentExecutionResult;

import java.io.InputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;
import org.tarik.ta.utils.CommonUtils;

import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.tarik.ta.tools.AgentExecutionResult.ExecutionStatus.ERROR;
import static org.tarik.ta.tools.AgentExecutionResult.ExecutionStatus.SUCCESS;

class PreconditionActionAgentTest {

    private MockedStatic<CommonUtils> commonUtilsMockedStatic;

    @BeforeEach
    void setUp() {
        commonUtilsMockedStatic = mockStatic(CommonUtils.class);
        commonUtilsMockedStatic.when(CommonUtils::captureScreen).thenReturn(mock(BufferedImage.class));
    }

    @AfterEach
    void tearDown() {
        commonUtilsMockedStatic.close();
    }

    @Test
    void shouldHaveValidSystemPromptPath() {
        SystemMessage annotation = PreconditionActionAgent.class.getAnnotation(SystemMessage.class);
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
    void shouldHandleSuccessfulExecution() {
        PreconditionActionAgent agent = mock(PreconditionActionAgent.class);
        doCallRealMethod().when(agent).executeAndGetResult(any(Runnable.class));

        AgentExecutionResult<?> result = agent.executeAndGetResult(() -> {
            // successful execution
        });

        assertThat(result.executionStatus()).isEqualTo(SUCCESS);
        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Execution successful");
    }

    @Test
    void shouldHandleFailedExecution() {
        PreconditionActionAgent agent = mock(PreconditionActionAgent.class);
        doCallRealMethod().when(agent).executeAndGetResult(any(Runnable.class));

        AgentExecutionResult<?> result = agent.executeAndGetResult(() -> {
            throw new RuntimeException("Simulated error");
        });

        assertThat(result.executionStatus()).isEqualTo(ERROR);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Simulated error");
        assertThat(result.screenshot()).isNotNull(); // BaseAiAgent captures screen on error
    }
}
