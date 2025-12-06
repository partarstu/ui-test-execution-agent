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
import org.tarik.ta.agents.TestCaseExtractionAgent;
import org.tarik.ta.agents.TestStepActionAgent;
import org.tarik.ta.agents.TestStepVerificationAgent;
import org.tarik.ta.agents.UiElementDescriptionAgent;
import org.tarik.ta.agents.UiStateCheckAgent;
import org.tarik.ta.agents.PageDescriptionAgent;
import org.tarik.ta.agents.ElementBoundingBoxAgent;
import org.tarik.ta.agents.ElementSelectionAgent;
import org.tarik.ta.dto.EmptyExecutionResult;
import org.tarik.ta.dto.TestExecutionResult;
import org.tarik.ta.dto.TestExecutionResult.TestExecutionStatus;
import org.tarik.ta.dto.TestStepResult.TestStepResultStatus;
import org.tarik.ta.dto.VerificationExecutionResult;
import org.tarik.ta.dto.TestCase;
import org.tarik.ta.dto.TestStep;
import org.tarik.ta.model.GenAiModel;
import org.tarik.ta.model.ModelFactory;
import org.tarik.ta.tools.AgentExecutionResult;
import org.tarik.ta.utils.CommonUtils;
import org.tarik.ta.utils.PromptUtils;
import org.tarik.ta.utils.ScreenRecorder;
import org.tarik.ta.rag.RetrieverFactory;
import org.tarik.ta.rag.UiElementRetriever;
import org.tarik.ta.error.RetryPolicy;

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
import static org.tarik.ta.utils.PromptUtils.loadSystemPrompt;

@ExtendWith(MockitoExtension.class)
class AgentTest {

        private GenAiModel mockModel;
        @Mock
        private ChatModel mockChatModel;
        @Mock
        private BufferedImage mockScreenshot;
        @Mock
        private UiElementRetriever mockUiElementRetriever;

        @Mock
        private TestCaseExtractionAgent testCaseExtractionAgentMock;
        @Mock
        private PreconditionActionAgent preconditionActionAgentMock;
        @Mock
        private PreconditionVerificationAgent preconditionVerificationAgentMock;
        @Mock
        private TestStepActionAgent testStepActionAgentMock;
        @Mock
        private TestStepVerificationAgent testStepVerificationAgentMock;
        @Mock
        private UiStateCheckAgent uiStateCheckAgentMock;
        @Mock
        private UiElementDescriptionAgent uiElementDescriptionAgentMock;
        @Mock
        private PageDescriptionAgent pageDescriptionAgentMock;
        @Mock
        private ElementBoundingBoxAgent elementBoundingBoxAgentMock;
        @Mock
        private ElementSelectionAgent elementSelectionAgentMock;

        @Mock
        private AiServices<TestCaseExtractionAgent> testCaseExtractionAgentBuilder;
        @Mock
        private AiServices<PreconditionActionAgent> preconditionActionAgentBuilder;
        @Mock
        private AiServices<PreconditionVerificationAgent> preconditionVerificationAgentBuilder;
        @Mock
        private AiServices<TestStepActionAgent> testStepActionAgentBuilder;
        @Mock
        private AiServices<TestStepVerificationAgent> testStepVerificationAgentBuilder;
        @Mock
        private AiServices<UiStateCheckAgent> toolVerificationAgentBuilder;
        @Mock
        private AiServices<UiElementDescriptionAgent> uiElementDescriptionAgentBuilder;
        @Mock
        private AiServices<PageDescriptionAgent> pageDescriptionAgentBuilder;
        @Mock
        private AiServices<ElementBoundingBoxAgent> elementBoundingBoxAgentBuilder;
        @Mock
        private AiServices<ElementSelectionAgent> elementSelectionAgentBuilder;

        // Static mocks
        private MockedStatic<ModelFactory> modelFactoryMockedStatic;
        private MockedStatic<CommonUtils> commonUtilsMockedStatic;
        private MockedStatic<AgentConfig> agentConfigMockedStatic;
        private MockedStatic<AiServices> aiServicesMockedStatic;
        private MockedStatic<RetrieverFactory> retrieverFactoryMockedStatic;
        private MockedStatic<PromptUtils> promptUtilsMockedStatic;
        private MockedConstruction<ScreenRecorder> screenRecorderMockedConstruction;

        private static final int ACTION_VERIFICATION_DELAY_MILLIS = 5;

        @BeforeEach
        void setUp() {
                modelFactoryMockedStatic = mockStatic(ModelFactory.class);
                commonUtilsMockedStatic = mockStatic(CommonUtils.class);
                agentConfigMockedStatic = mockStatic(AgentConfig.class);
                aiServicesMockedStatic = mockStatic(AiServices.class);
                retrieverFactoryMockedStatic = mockStatic(RetrieverFactory.class);
                screenRecorderMockedConstruction = mockConstruction(ScreenRecorder.class);
                promptUtilsMockedStatic = mockStatic(PromptUtils.class);

                // Agent Config
                agentConfigMockedStatic.when(AgentConfig::getActionVerificationDelayMillis)
                                .thenReturn(ACTION_VERIFICATION_DELAY_MILLIS);
                agentConfigMockedStatic.when(AgentConfig::getVerificationRetryTimeoutMillis).thenReturn(1000);
                agentConfigMockedStatic.when(AgentConfig::getActionRetryPolicy).thenReturn(mock(RetryPolicy.class));
                agentConfigMockedStatic.when(AgentConfig::getVerificationRetryPolicy).thenReturn(mock(RetryPolicy.class));
                agentConfigMockedStatic.when(AgentConfig::isElementLocationPrefetchingEnabled).thenReturn(false);
                agentConfigMockedStatic.when(AgentConfig::isUnattendedMode).thenReturn(false);
                agentConfigMockedStatic.when(AgentConfig::getTestCaseExtractionAgentModelProvider).thenReturn(AgentConfig.ModelProvider.GOOGLE);
                agentConfigMockedStatic.when(AgentConfig::getPreconditionActionAgentModelProvider).thenReturn(AgentConfig.ModelProvider.GOOGLE);
                agentConfigMockedStatic.when(AgentConfig::getTestStepActionAgentModelProvider).thenReturn(AgentConfig.ModelProvider.GOOGLE);
                agentConfigMockedStatic.when(AgentConfig::getPreconditionVerificationAgentModelProvider).thenReturn(AgentConfig.ModelProvider.GOOGLE);
                agentConfigMockedStatic.when(AgentConfig::getTestStepVerificationAgentModelProvider).thenReturn(AgentConfig.ModelProvider.GOOGLE);
                agentConfigMockedStatic.when(AgentConfig::getTestCaseExtractionAgentModelName).thenReturn("test-model");
                agentConfigMockedStatic.when(AgentConfig::getPreconditionActionAgentModelName).thenReturn("test-model");
                agentConfigMockedStatic.when(AgentConfig::getTestStepActionAgentModelName).thenReturn("test-model");
                agentConfigMockedStatic.when(AgentConfig::getPreconditionVerificationAgentModelName).thenReturn("test-model");
                agentConfigMockedStatic.when(AgentConfig::getTestStepVerificationAgentModelName).thenReturn("test-model");
                agentConfigMockedStatic.when(AgentConfig::getPreconditionAgentPromptVersion).thenReturn("v1");
                agentConfigMockedStatic.when(AgentConfig::getTestStepActionAgentPromptVersion).thenReturn("v1");
                agentConfigMockedStatic.when(AgentConfig::getPageDescriptionAgentModelName).thenReturn("test-model");
                agentConfigMockedStatic.when(AgentConfig::getElementBoundingBoxAgentModelName).thenReturn("test-model");
                agentConfigMockedStatic.when(AgentConfig::getElementSelectionAgentModelName).thenReturn("test-model");
                agentConfigMockedStatic.when(AgentConfig::getPageDescriptionAgentModelProvider).thenReturn(AgentConfig.ModelProvider.GOOGLE);
                agentConfigMockedStatic.when(AgentConfig::getElementBoundingBoxAgentModelProvider).thenReturn(AgentConfig.ModelProvider.GOOGLE);
                agentConfigMockedStatic.when(AgentConfig::getElementSelectionAgentModelProvider).thenReturn(AgentConfig.ModelProvider.GOOGLE);
                agentConfigMockedStatic.when(AgentConfig::getPageDescriptionAgentPromptVersion).thenReturn("v1");
                agentConfigMockedStatic.when(AgentConfig::getElementBoundingBoxAgentPromptVersion).thenReturn("v1");
                agentConfigMockedStatic.when(AgentConfig::getElementSelectionAgentPromptVersion).thenReturn("v1");
                agentConfigMockedStatic.when(AgentConfig::getPreconditionVerificationAgentPromptVersion).thenReturn("v1");
                agentConfigMockedStatic.when(AgentConfig::getTestStepVerificationAgentPromptVersion).thenReturn("v1");

                mockModel = new GenAiModel(mockChatModel);

                modelFactoryMockedStatic.when(() -> ModelFactory.getModel(any(), any())).thenReturn(mockModel);

                // Common Utils
                commonUtilsMockedStatic.when(() -> CommonUtils.isNotBlank(anyString())).thenCallRealMethod();
                commonUtilsMockedStatic.when(() -> CommonUtils.isNotBlank(null)).thenReturn(false);
                commonUtilsMockedStatic.when(CommonUtils::captureScreen).thenReturn(mockScreenshot);
                commonUtilsMockedStatic.when(() -> sleepMillis(anyInt())).thenAnswer(invocation -> null);
                
                promptUtilsMockedStatic.when(() -> loadSystemPrompt(any(), any(), any())).thenReturn("System Prompt");

                // AiServices Mocking
                aiServicesMockedStatic.when(() -> AiServices.builder(any(Class.class)))
                                .thenAnswer(invocation -> {
                                    Class<?> argument = invocation.getArgument(0);
                                    if (TestCaseExtractionAgent.class.equals(argument)) return testCaseExtractionAgentBuilder;
                                    if (PreconditionActionAgent.class.equals(argument)) return preconditionActionAgentBuilder;
                                    if (PreconditionVerificationAgent.class.equals(argument)) return preconditionVerificationAgentBuilder;
                                    if (TestStepActionAgent.class.equals(argument)) return testStepActionAgentBuilder;
                                    if (TestStepVerificationAgent.class.equals(argument)) return testStepVerificationAgentBuilder;
                                    if (UiStateCheckAgent.class.equals(argument)) return toolVerificationAgentBuilder;
                                    if (UiElementDescriptionAgent.class.equals(argument)) return uiElementDescriptionAgentBuilder;
                                    if (PageDescriptionAgent.class.equals(argument)) return pageDescriptionAgentBuilder;
                                    if (ElementBoundingBoxAgent.class.equals(argument)) return elementBoundingBoxAgentBuilder;
                                    if (ElementSelectionAgent.class.equals(argument)) return elementSelectionAgentBuilder;
                                    throw new RuntimeException("Unexpected agent class requested: " + argument.getName());
                                });

                // Retriever Factory
                retrieverFactoryMockedStatic.when(RetrieverFactory::getUiElementRetriever)
                                .thenReturn(mockUiElementRetriever);

                // Builder chains
                configureBuilder(testCaseExtractionAgentBuilder, testCaseExtractionAgentMock);
                configureBuilder(preconditionActionAgentBuilder, preconditionActionAgentMock);
                configureBuilder(preconditionVerificationAgentBuilder, preconditionVerificationAgentMock);
                configureBuilder(testStepActionAgentBuilder, testStepActionAgentMock);
                configureBuilder(testStepVerificationAgentBuilder, testStepVerificationAgentMock);
                configureBuilder(toolVerificationAgentBuilder, uiStateCheckAgentMock);
                configureBuilder(uiElementDescriptionAgentBuilder, uiElementDescriptionAgentMock);
                configureBuilder(pageDescriptionAgentBuilder, pageDescriptionAgentMock);
                configureBuilder(elementBoundingBoxAgentBuilder, elementBoundingBoxAgentMock);
                configureBuilder(elementSelectionAgentBuilder, elementSelectionAgentMock);
        }

        private <T> void configureBuilder(AiServices<T> builder, T agent) {
                lenient().when(builder.chatModel(any())).thenReturn(builder);
                lenient().when(builder.tools(any(Object[].class))).thenReturn(builder);
                lenient().when(builder.toolExecutionErrorHandler(any())).thenReturn(builder);
                lenient().when(builder.systemMessageProvider(any())).thenReturn(builder);
                lenient().when(builder.maxSequentialToolsInvocations(anyInt())).thenReturn(builder);
                lenient().when(builder.build()).thenReturn(agent);
        }

        @AfterEach
        void tearDown() {
                modelFactoryMockedStatic.close();
                commonUtilsMockedStatic.close();
                agentConfigMockedStatic.close();
                aiServicesMockedStatic.close();
                retrieverFactoryMockedStatic.close();
                screenRecorderMockedConstruction.close();
                promptUtilsMockedStatic.close();
        }

        @Test
        @DisplayName("Single test step with action and successful verification")
        void singleStepActionAndVerificationSuccess() {
                // Given
                TestStep step = new TestStep("Perform Action", null, "Verify Result");
                TestCase testCase = new TestCase("Single Step Success", null, List.of(step));

                mockTestCaseExtraction(testCase);

                doReturn(new AgentExecutionResult<>(SUCCESS, "Action executed", true, mockScreenshot, new EmptyExecutionResult(),
                                Instant.now()))
                                .when(testStepActionAgentMock).executeWithRetry(any(Supplier.class));

                doReturn(new AgentExecutionResult<>(SUCCESS, "Verification executed", true, mockScreenshot,
                                new VerificationExecutionResult(true, "Verified"), Instant.now()))
                                .when(testStepVerificationAgentMock).executeWithRetry(any(Supplier.class), any());

                // When
                TestExecutionResult result = Agent.executeTestCase("test case message");

                // Then
                assertThat(result.testExecutionStatus()).isEqualTo(PASSED);
                assertThat(result.stepResults()).hasSize(1);
                assertThat(result.stepResults().getFirst().executionStatus()).isEqualTo(TestStepResultStatus.SUCCESS);

                verify(testStepActionAgentMock).executeWithRetry(any(Supplier.class));
                verify(testStepVerificationAgentMock).executeWithRetry(any(Supplier.class), any());
        }

        @Test
        @DisplayName("Single step with action only (no verification)")
        void singleStepActionOnlySuccess() {
                // Given
                TestStep step = new TestStep("Perform Action Only", null, null);
                TestCase testCase = new TestCase("Single Action Only", null, List.of(step));

                mockTestCaseExtraction(testCase);

                doReturn(new AgentExecutionResult<>(SUCCESS, "Action executed", true, mockScreenshot, new EmptyExecutionResult(),
                                Instant.now()))
                                .when(testStepActionAgentMock).executeWithRetry(any(Supplier.class));

                // When
                TestExecutionResult result = Agent.executeTestCase("test case message");

                // Then
                assertThat(result.testExecutionStatus()).isEqualTo(PASSED);
                verify(testStepActionAgentMock).executeWithRetry(any(Supplier.class));
                verifyNoInteractions(testStepVerificationAgentMock);
        }

        @Test
        @DisplayName("Preconditions execution and verification success")
        void preconditionsSuccess() {
                // Given
                String precondition = "Precondition 1";
                TestStep step = new TestStep("Action", null, null);
                TestCase testCase = new TestCase("Precondition Success", List.of(precondition), List.of(step));

                mockTestCaseExtraction(testCase);

                doReturn(new AgentExecutionResult<>(SUCCESS, "Precondition executed", true, mockScreenshot,
                                new EmptyExecutionResult(), Instant.now()))
                                .when(preconditionActionAgentMock).executeWithRetry(any(Supplier.class));

                doReturn(new AgentExecutionResult<>(SUCCESS, "Precondition verified", true, mockScreenshot,
                                new VerificationExecutionResult(true, "Verified"), Instant.now()))
                                .when(preconditionVerificationAgentMock).executeWithRetry(any(Supplier.class), any());

                doReturn(new AgentExecutionResult<>(SUCCESS, "Action executed", true, mockScreenshot, new EmptyExecutionResult(),
                                Instant.now()))
                                .when(testStepActionAgentMock).executeWithRetry(any(Supplier.class));

                // When
                TestExecutionResult result = Agent.executeTestCase("test case message");

                // Then
                assertThat(result.testExecutionStatus()).isEqualTo(PASSED);
                verify(preconditionActionAgentMock).executeWithRetry(any(Supplier.class));
                verify(preconditionVerificationAgentMock).executeWithRetry(any(Supplier.class), any());
                verify(testStepActionAgentMock).executeWithRetry(any(Supplier.class));
        }

        @Test
        @DisplayName("Precondition execution fails")
        void preconditionExecutionFails() {
                // Given
                String precondition = "Precondition 1";
                TestCase testCase = new TestCase("Precondition Fail", List.of(precondition), List.of(new TestStep("Dummy Step", null, null)));

                mockTestCaseExtraction(testCase);

                doReturn(new AgentExecutionResult<>(ERROR, "Precondition failed", false, mockScreenshot, null,
                                Instant.now()))
                                .when(preconditionActionAgentMock).executeWithRetry(any(Supplier.class));

                // When
                TestExecutionResult result = Agent.executeTestCase("test case message");

                // Then
                assertThat(result.testExecutionStatus()).isEqualTo(FAILED);
                assertThat(result.generalErrorMessage()).contains("Failure while executing precondition");
                verify(preconditionActionAgentMock).executeWithRetry(any(Supplier.class));
                verifyNoInteractions(preconditionVerificationAgentMock);
                verifyNoInteractions(testStepActionAgentMock);
        }

        @Test
        @DisplayName("Precondition verification fails")
        void preconditionVerificationFails() {
                // Given
                String precondition = "Precondition 1";
                TestCase testCase = new TestCase("Precondition Verify Fail", List.of(precondition), List.of(new TestStep("Dummy Step", null, null)));

                mockTestCaseExtraction(testCase);

                doReturn(new AgentExecutionResult<>(SUCCESS, "Precondition executed", true, mockScreenshot,
                                new EmptyExecutionResult(), Instant.now()))
                                .when(preconditionActionAgentMock).executeWithRetry(any(Supplier.class));

                doReturn(new AgentExecutionResult<>(SUCCESS, "Precondition verified", true, mockScreenshot,
                                new VerificationExecutionResult(false, "Not Verified"), Instant.now()))
                                .when(preconditionVerificationAgentMock).executeWithRetry(any(Supplier.class), any());

                // When
                TestExecutionResult result = Agent.executeTestCase("test case message");

                // Then
                assertThat(result.testExecutionStatus()).isEqualTo(FAILED);
                assertThat(result.generalErrorMessage()).contains("Precondition verification failed. Not Verified");
                verify(preconditionActionAgentMock).executeWithRetry(any(Supplier.class));
                verify(preconditionVerificationAgentMock).executeWithRetry(any(Supplier.class), any());
                verifyNoInteractions(testStepActionAgentMock);
        }

        @Test
        @DisplayName("Test step action fails")
        void testStepActionFails() {
                // Given
                TestStep step = new TestStep("Action", null, "Verify");
                TestCase testCase = new TestCase("Action Fail", null, List.of(step));

                mockTestCaseExtraction(testCase);

                doReturn(new AgentExecutionResult<>(ERROR, "Action failed", false, mockScreenshot, null, Instant.now()))
                                .when(testStepActionAgentMock).executeWithRetry(any(Supplier.class));

                // When
                TestExecutionResult result = Agent.executeTestCase("test case message");

                // Then
                assertThat(result.testExecutionStatus()).isEqualTo(TestExecutionStatus.ERROR);
                assertThat(result.stepResults().getFirst().executionStatus()).isEqualTo(TestStepResultStatus.ERROR);
                verify(testStepActionAgentMock).executeWithRetry(any(Supplier.class));
                verifyNoInteractions(testStepVerificationAgentMock);
        }

        @Test
        @DisplayName("Test step verification fails")
        void testStepVerificationFails() {
                // Given
                TestStep step = new TestStep("Action", null, "Verify");
                TestCase testCase = new TestCase("Verification Fail", null, List.of(step));

                mockTestCaseExtraction(testCase);

                doReturn(new AgentExecutionResult<>(SUCCESS, "Action executed", true, mockScreenshot, new EmptyExecutionResult(),
                                Instant.now()))
                                .when(testStepActionAgentMock).executeWithRetry(any(Supplier.class));

                doReturn(new AgentExecutionResult<>(SUCCESS, "Verification executed", true, mockScreenshot,
                                new VerificationExecutionResult(false, "Verification failed"), Instant.now()))
                                .when(testStepVerificationAgentMock).executeWithRetry(any(Supplier.class), any());

                // When
                TestExecutionResult result = Agent.executeTestCase("test case message");

                // Then
                assertThat(result.testExecutionStatus()).isEqualTo(FAILED);
                assertThat(result.stepResults().getFirst().executionStatus()).isEqualTo(TestStepResultStatus.FAILURE);
                verify(testStepActionAgentMock).executeWithRetry(any(Supplier.class));
                verify(testStepVerificationAgentMock).executeWithRetry(any(Supplier.class), any());
        }

        private void mockTestCaseExtraction(TestCase testCase) {
                doReturn(new AgentExecutionResult<>(SUCCESS, "Test case extracted", true, mockScreenshot, testCase, Instant.now()))
                                .when(testCaseExtractionAgentMock).executeAndGetResult(any(Supplier.class));
        }
}