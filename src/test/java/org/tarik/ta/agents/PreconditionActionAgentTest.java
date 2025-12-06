package org.tarik.ta.agents;

import dev.langchain4j.service.Result;
import org.junit.jupiter.api.Test;
import org.tarik.ta.dto.EmptyExecutionResult;
import org.tarik.ta.tools.AgentExecutionResult;

import org.mockito.MockedStatic;
import org.tarik.ta.utils.CommonUtils;

import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.tarik.ta.tools.AgentExecutionResult.ExecutionStatus.ERROR;
import static org.tarik.ta.tools.AgentExecutionResult.ExecutionStatus.SUCCESS;
import static org.mockito.ArgumentMatchers.anyBoolean;

class PreconditionActionAgentTest {

    @Test
    void shouldHandleSuccessfulExecution() {
        try (MockedStatic<CommonUtils> commonUtilsMockedStatic = mockStatic(CommonUtils.class, CALLS_REAL_METHODS)) {
            commonUtilsMockedStatic.when(CommonUtils::captureScreen).thenReturn(mock(BufferedImage.class));
            commonUtilsMockedStatic.when(() -> CommonUtils.captureScreen(anyBoolean())).thenReturn(mock(BufferedImage.class));

            PreconditionActionAgent agent = (_, _) -> null;

            AgentExecutionResult<EmptyExecutionResult> result = agent.executeAndGetResult(() -> Result.builder().content(new EmptyExecutionResult()).build());

            assertThat(result.executionStatus()).isEqualTo(SUCCESS);
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("Execution successful");
            assertThat(result.resultPayload()).isNotNull();
        }
    }

    @Test
    void shouldHandleFailedExecution() {
        try (MockedStatic<CommonUtils> commonUtilsMockedStatic = mockStatic(CommonUtils.class, CALLS_REAL_METHODS)) {
            BufferedImage mockImage = mock(BufferedImage.class);
            commonUtilsMockedStatic.when(CommonUtils::captureScreen).thenReturn(mockImage);
            commonUtilsMockedStatic.when(() -> CommonUtils.captureScreen(anyBoolean())).thenReturn(mockImage);

            PreconditionActionAgent agent = (_, _) -> null;

            AgentExecutionResult<EmptyExecutionResult> result = agent.executeAndGetResult(() -> {
                throw new RuntimeException("Simulated error");
            });

            assertThat(result.executionStatus()).isEqualTo(ERROR);
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("Simulated error");
            assertThat(result.screenshot()).isNotNull();
        }
    }
}