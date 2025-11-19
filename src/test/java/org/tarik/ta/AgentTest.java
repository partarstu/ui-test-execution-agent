/*
 * Copyright © 2025 Taras Paruta (partarstu@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tarik.ta;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.agents.PreconditionActionAgent;
import org.tarik.ta.agents.PreconditionVerificationAgent;
import org.tarik.ta.agents.TestStepActionAgent;
import org.tarik.ta.agents.TestStepVerificationAgent;
import org.tarik.ta.dto.TestExecutionResult;
import org.tarik.ta.dto.TestExecutionResult.TestExecutionStatus;
import org.tarik.ta.dto.VerificationExecutionResult;
import org.tarik.ta.helper_entities.TestCase;
import org.tarik.ta.helper_entities.TestStep;
import org.tarik.ta.model.GenAiModel;
import org.tarik.ta.model.ModelFactory;
import org.tarik.ta.tools.AgentExecutionResult;
import org.tarik.ta.utils.CommonUtils;
import org.tarik.ta.utils.ScreenRecorder;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.tarik.ta.dto.TestExecutionResult.TestExecutionStatus.FAILED;
import static org.tarik.ta.dto.TestExecutionResult.TestExecutionStatus.PASSED;
import static org.tarik.ta.tools.AgentExecutionResult.ExecutionStatus.ERROR;
import static org.tarik.ta.tools.AgentExecutionResult.ExecutionStatus.SUCCESS;
import static org.tarik.ta.utils.CommonUtils.sleepMillis;

@ExtendWith(MockitoExtension.class)
class AgentTest {

        @Mock
        private GenAiModel mockModel;
        @Mock
        private ChatModel mockChatModel;
        @Mock
        private BufferedImage mockScreenshot;

        @Mock
        private PreconditionActionAgent preconditionActionAgentMock;
        @Mock
        private PreconditionVerificationAgent preconditionVerificationAgentMock;
        @Mock
        private TestStepActionAgent testStepActionAgentMock;
        @Mock
        private TestStepVerificationAgent testStepVerificationAgentMock;

        @Mock
        private AiServices<PreconditionActionAgent> preconditionActionAgentBuilder;
        @Mock
        private AiServices<PreconditionVerificationAgent> preconditionVerificationAgentBuilder;
        @Mock
        private AiServices<TestStepActionAgent> testStepActionAgentBuilder;
        @Mock
        private AiServices<TestStepVerificationAgent> testStepVerificationAgentBuilder;

        // Static mocks
        private MockedStatic<ModelFactory> modelFactoryMockedStatic;
        private MockedStatic<CommonUtils> commonUtilsMockedStatic;
        private MockedStatic<AgentConfig> agentConfigMockedStatic;
        private MockedStatic<AiServices> aiServicesMockedStatic;
        private MockedConstruction<ScreenRecorder> screenRecorderMockedConstruction;

        private static final int ACTION_VERIFICATION_DELAY_MILLIS = 5;

        @BeforeEach
        void setUp() {
                modelFactoryMockedStatic = mockStatic(ModelFactory.class);
                commonUtilsMockedStatic = mockStatic(CommonUtils.class);
                agentConfigMockedStatic = mockStatic(AgentConfig.class);
                aiServicesMockedStatic = mockStatic(AiServices.class);
                screenRecorderMockedConstruction = mockConstruction(ScreenRecorder.class);

                // Agent Config
                agentConfigMockedStatic.when(AgentConfig::getActionVerificationDelayMillis)
                                .thenReturn(ACTION_VERIFICATION_DELAY_MILLIS);

                // Model Factory
                modelFactoryMockedStatic.when(ModelFactory::getInstructionModel).thenReturn(mockModel);
                when(mockModel.getChatModel()).thenReturn(mockChatModel);

                // Common Utils
                commonUtilsMockedStatic.when(() -> CommonUtils.isNotBlank(anyString())).thenCallRealMethod();
                commonUtilsMockedStatic.when(() -> CommonUtils.isNotBlank(null)).thenReturn(false);
                commonUtilsMockedStatic.when(CommonUtils::captureScreen).thenReturn(mockScreenshot);
                commonUtilsMockedStatic.when(() -> sleepMillis(anyInt())).thenAnswer(invocation -> null);

                // AiServices Mocking
                aiServicesMockedStatic.when(() -> AiServices.builder(PreconditionActionAgent.class))
                                .thenReturn(preconditionActionAgentBuilder);
                aiServicesMockedStatic.when(() -> AiServices.builder(PreconditionVerificationAgent.class))
                                .thenReturn(preconditionVerificationAgentBuilder);
                aiServicesMockedStatic.when(() -> AiServices.builder(TestStepActionAgent.class))
                                .thenReturn(testStepActionAgentBuilder);
                aiServicesMockedStatic.when(() -> AiServices.builder(TestStepVerificationAgent.class))
                                .thenReturn(testStepVerificationAgentBuilder);

                // Builder chains
                configureBuilder(preconditionActionAgentBuilder, preconditionActionAgentMock);
                configureBuilder(preconditionVerificationAgentBuilder, preconditionVerificationAgentMock);
                configureBuilder(testStepActionAgentBuilder, testStepActionAgentMock);
                configureBuilder(testStepVerificationAgentBuilder, testStepVerificationAgentMock);
        }

        private <T> void configureBuilder(AiServices<T> builder, T agent) {
                lenient().when(builder.chatModel(any())).thenReturn(builder);
                lenient().when(builder.tools(any(Object[].class))).thenReturn(builder);
                lenient().when(builder.build()).thenReturn(agent);
        }

        @AfterEach
        void tearDown() {
                modelFactoryMockedStatic.close();
                commonUtilsMockedStatic.close();
                agentConfigMockedStatic.close();
                aiServicesMockedStatic.close();
                screenRecorderMockedConstruction.close();
        }

        @Test
        @DisplayName("Single test step with action and successful verification")
        void singleStepActionAndVerificationSuccess() {
                // Given
                TestStep step = new TestStep("Perform Action", null, "Verify Result");
                TestCase testCase = new TestCase("Single Step Success", null, List.of(step));

                doReturn(new AgentExecutionResult<>(SUCCESS, "Action executed", true, mockScreenshot, "Action executed",
                                Instant.now()))
                                .when(testStepActionAgentMock).executeAndGetResult(any(Runnable.class));

                doReturn(new AgentExecutionResult<>(SUCCESS, "Verification executed", true, mockScreenshot,
                                new VerificationExecutionResult(true, "Verified"), Instant.now()))
                                .when(testStepVerificationAgentMock).executeAndGetResult(any(Supplier.class));

                // When
                TestExecutionResult result = Agent.executeTestCase(testCase);

                // Then
                assertThat(result.testExecutionStatus()).isEqualTo(PASSED);
                assertThat(result.stepResults()).hasSize(1);
                assertThat(result.stepResults().getFirst().success()).isTrue();

                verify(testStepActionAgentMock).executeAndGetResult(any(Runnable.class));
                verify(testStepVerificationAgentMock).executeAndGetResult(any(Supplier.class));
        }

        @Test
        @DisplayName("Single step with action only (no verification)")
        void singleStepActionOnlySuccess() {
                // Given
                TestStep step = new TestStep("Perform Action Only", null, null);
                TestCase testCase = new TestCase("Single Action Only", null, List.of(step));

                doReturn(new AgentExecutionResult<>(SUCCESS, "Action executed", true, mockScreenshot, "Action executed",
                                Instant.now()))
                                .when(testStepActionAgentMock).executeAndGetResult(any(Runnable.class));

                // When
                TestExecutionResult result = Agent.executeTestCase(testCase);

                // Then
                assertThat(result.testExecutionStatus()).isEqualTo(PASSED);
                verify(testStepActionAgentMock).executeAndGetResult(any(Runnable.class));
                verifyNoInteractions(testStepVerificationAgentMock);
        }

        @Test
        @DisplayName("Preconditions execution and verification success")
        void preconditionsSuccess() {
                // Given
                String precondition = "Precondition 1";
                TestStep step = new TestStep("Action", null, null);
                TestCase testCase = new TestCase("Precondition Success", List.of(precondition), List.of(step));

                doReturn(new AgentExecutionResult<>(SUCCESS, "Precondition executed", true, mockScreenshot,
                                "Precondition executed", Instant.now()))
                                .when(preconditionActionAgentMock).executeAndGetResult(any(Runnable.class));

                doReturn(new AgentExecutionResult<>(SUCCESS, "Precondition verified", true, mockScreenshot,
                                new VerificationExecutionResult(true, "Verified"), Instant.now()))
                                .when(preconditionVerificationAgentMock).executeAndGetResult(any(Supplier.class));

                doReturn(new AgentExecutionResult<>(SUCCESS, "Action executed", true, mockScreenshot, "Action executed",
                                Instant.now()))
                                .when(testStepActionAgentMock).executeAndGetResult(any(Runnable.class));

                // When
                TestExecutionResult result = Agent.executeTestCase(testCase);

                // Then
                assertThat(result.testExecutionStatus()).isEqualTo(PASSED);
                verify(preconditionActionAgentMock).executeAndGetResult(any(Runnable.class));
                verify(preconditionVerificationAgentMock).executeAndGetResult(any(Supplier.class));
                verify(testStepActionAgentMock).executeAndGetResult(any(Runnable.class));
        }

        @Test
        @DisplayName("Precondition execution fails")
        void preconditionExecutionFails() {
                // Given
                String precondition = "Precondition 1";
                TestCase testCase = new TestCase("Precondition Fail", List.of(precondition), List.of());

                doReturn(new AgentExecutionResult<>(ERROR, "Precondition failed", false, mockScreenshot, null,
                                Instant.now()))
                                .when(preconditionActionAgentMock).executeAndGetResult(any(Runnable.class));

                // When
                TestExecutionResult result = Agent.executeTestCase(testCase);

                // Then
                assertThat(result.testExecutionStatus()).isEqualTo(FAILED);
                assertThat(result.generalErrorMessage()).contains("Failure while executing precondition");
                verify(preconditionActionAgentMock).executeAndGetResult(any(Runnable.class));
                verifyNoInteractions(preconditionVerificationAgentMock);
                verifyNoInteractions(testStepActionAgentMock);
        }

        @Test
        @DisplayName("Precondition verification fails")
        void preconditionVerificationFails() {
                // Given
                String precondition = "Precondition 1";
                TestCase testCase = new TestCase("Precondition Verify Fail", List.of(precondition), List.of());

                doReturn(new AgentExecutionResult<>(SUCCESS, "Precondition executed", true, mockScreenshot,
                                "Precondition executed", Instant.now()))
                                .when(preconditionActionAgentMock).executeAndGetResult(any(Runnable.class));

                doReturn(new AgentExecutionResult<>(SUCCESS, "Precondition verified", true, mockScreenshot,
                                new VerificationExecutionResult(false, "Not Verified"), Instant.now()))
                                .when(preconditionVerificationAgentMock).executeAndGetResult(any(Supplier.class));

                // When
                TestExecutionResult result = Agent.executeTestCase(testCase);

                // Then
                assertThat(result.testExecutionStatus()).isEqualTo(FAILED);
                assertThat(result.generalErrorMessage()).contains("Precondition 'Precondition 1' not fulfilled");
                verify(preconditionActionAgentMock).executeAndGetResult(any(Runnable.class));
                verify(preconditionVerificationAgentMock).executeAndGetResult(any(Supplier.class));
                verifyNoInteractions(testStepActionAgentMock);
        }

        @Test
        @DisplayName("Test step action fails")
        void testStepActionFails() {
                // Given
                TestStep step = new TestStep("Action", null, "Verify");
                TestCase testCase = new TestCase("Action Fail", null, List.of(step));

                doReturn(new AgentExecutionResult<>(ERROR, "Action failed", false, mockScreenshot, null, Instant.now()))
                                .when(testStepActionAgentMock).executeAndGetResult(any(Runnable.class));

                // When
                TestExecutionResult result = Agent.executeTestCase(testCase);

                // Then
                assertThat(result.testExecutionStatus()).isEqualTo(TestExecutionStatus.ERROR);
                assertThat(result.stepResults().getFirst().success()).isFalse();
                verify(testStepActionAgentMock).executeAndGetResult(any(Runnable.class));
                verifyNoInteractions(testStepVerificationAgentMock);
        }

        @Test
        @DisplayName("Test step verification fails")
        void testStepVerificationFails() {
                // Given
                TestStep step = new TestStep("Action", null, "Verify");
                TestCase testCase = new TestCase("Verification Fail", null, List.of(step));

                doReturn(new AgentExecutionResult<>(SUCCESS, "Action executed", true, mockScreenshot, "Action executed",
                                Instant.now()))
                                .when(testStepActionAgentMock).executeAndGetResult(any(Runnable.class));

                doReturn(new AgentExecutionResult<>(SUCCESS, "Verification executed", true, mockScreenshot,
                                new VerificationExecutionResult(false, "Verification failed"), Instant.now()))
                                .when(testStepVerificationAgentMock).executeAndGetResult(any(Supplier.class));

                // When
                TestExecutionResult result = Agent.executeTestCase(testCase);

                // Then
                assertThat(result.testExecutionStatus()).isEqualTo(FAILED);
                assertThat(result.stepResults().getFirst().success()).isFalse();
                verify(testStepActionAgentMock).executeAndGetResult(any(Runnable.class));
                verify(testStepVerificationAgentMock).executeAndGetResult(any(Supplier.class));
        }
}