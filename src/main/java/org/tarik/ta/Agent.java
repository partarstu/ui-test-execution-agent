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

import org.tarik.ta.agents.*;
import org.tarik.ta.dto.TestExecutionResult;
import org.tarik.ta.dto.TestStepResult;
import org.tarik.ta.dto.VerificationExecutionResult;
import org.tarik.ta.error.ErrorCategory;
import org.tarik.ta.exceptions.ElementLocationException;
import org.tarik.ta.exceptions.ToolExecutionException;
import org.tarik.ta.helper_entities.TestCase;
import org.tarik.ta.helper_entities.TestStep;
import org.tarik.ta.manager.VerificationManager;
import org.tarik.ta.model.TestExecutionContext;
import org.tarik.ta.model.VisualState;
import org.tarik.ta.tools.*;
import org.tarik.ta.error.RetryPolicy;
import org.tarik.ta.error.RetryState;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolErrorContext;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static org.tarik.ta.dto.TestExecutionResult.TestExecutionStatus.*;
import static org.tarik.ta.error.ErrorCategory.*;
import static org.tarik.ta.error.ErrorCategory.TIMEOUT;
import static org.tarik.ta.model.ModelFactory.getModel;

import org.tarik.ta.manager.BudgetManager;
import org.tarik.ta.utils.ScreenRecorder;

import static dev.langchain4j.service.AiServices.builder;
import static java.lang.String.join;
import static java.time.Instant.now;
import static java.util.Optional.ofNullable;
import static org.tarik.ta.AgentConfig.getActionVerificationDelayMillis;
import static org.tarik.ta.AgentConfig.isUnattendedMode;
import static org.tarik.ta.rag.RetrieverFactory.getUiElementRetriever;
import static org.tarik.ta.utils.CommonUtils.captureScreen;
import static org.tarik.ta.utils.CommonUtils.sleepMillis;
import static org.tarik.ta.utils.PromptUtils.loadSystemPrompt;
import static org.tarik.ta.utils.PromptUtils.singleImageContent;

public class Agent {
    private static final Logger LOG = LoggerFactory.getLogger(Agent.class);
    protected static final int ACTION_VERIFICATION_DELAY_MILLIS = getActionVerificationDelayMillis();

    public static TestExecutionResult executeTestCase(TestCase testCase) {
        BudgetManager.reset();
        ScreenRecorder screenRecorder = new ScreenRecorder();
        VerificationManager verificationManager = new VerificationManager();
        AtomicReference<String> verificationMessage = new AtomicReference<>();
        try (ExecutorService verificationExecutor = newVirtualThreadPerTaskExecutor()) {
            screenRecorder.beginScreenCapture();
            var testExecutionStartTimestamp = now();
            var context = new TestExecutionContext(testCase, new VisualState(captureScreen()));
            var userInteractionTools = new UserInteractionTools(getUiElementRetriever());
            var commonTools = new CommonTools(verificationManager);

            var preconditionActionAgent =
                    getPreconditionActionAgent(commonTools, userInteractionTools, new RetryState());
            var preconditionVerificationAgent = getPreconditionVerificationAgent(new RetryState());
            var testStepActionAgent = getTestStepActionAgent(commonTools, userInteractionTools, new RetryState());
            var testStepVerificationAgent = getTestStepVerificationAgent(new RetryState());

            List<String> preconditions = testCase.preconditions();
            if (preconditions != null && !preconditions.isEmpty()) {
                LOG.info("Executing and verifying preconditions for test case: {}", testCase.name());
                for (String precondition : preconditions) {
                    LOG.info("Executing precondition: {}", precondition);
                    var preconditionExecutionResult = preconditionActionAgent.executeWithRetry(
                            () -> {
                                preconditionActionAgent.execute(precondition, context.getSharedData().toString(), !isUnattendedMode());
                                return null;
                            });

                    if (!preconditionExecutionResult.success()) {
                        var errorMessage = "Error while executing precondition '%s'. Root cause: %s"
                                .formatted(precondition, preconditionExecutionResult.message());
                        return getTestExecutionResultWithError(context, testExecutionStartTimestamp, errorMessage, captureScreen(), true);
                    }
                    LOG.info("Precondition execution complete.");

                    var verificationExecutionResult = preconditionVerificationAgent
                            .executeWithRetry(() -> {
                                var screenshot = captureScreen();
                                context.setVisualState(new VisualState(screenshot));
                                return preconditionVerificationAgent.verify(precondition, context.getSharedData().toString(),
                                        singleImageContent(screenshot));
                            }, result -> !result.success());

                    if (!verificationExecutionResult.success()) {
                        var errorMessage = "Error while verifying precondition '%s'. Root cause: %s"
                                .formatted(precondition, verificationExecutionResult.message());
                        return getTestExecutionResultWithError(context, testExecutionStartTimestamp, errorMessage,
                                context.getVisualState().screenshot(), true);
                    }

                    var verificationResult = verificationExecutionResult.resultPayload();
                    if (verificationResult != null && !verificationResult.success()) {
                        var errorMessage = "Precondition '%s' not fulfilled, although was executed. %s"
                                .formatted(precondition, verificationResult.message());
                        return getFailedTestExecutionResult(context, testExecutionStartTimestamp, errorMessage,
                                context.getVisualState().screenshot(), true);
                    }
                    LOG.info("Precondition '{}' is met.", precondition);
                }

                LOG.info("All preconditions are met for test case: {}", testCase.name());
            }

            for (TestStep testStep : testCase.testSteps()) {
                var actionInstruction = testStep.stepDescription();
                var testData = ofNullable(testStep.testData()).map(Object::toString).orElse("");
                var verificationInstruction = testStep.expectedResults();
                try {
                    var executionStartTimestamp = now();
                    LOG.info("Executing test step: {}", actionInstruction);
                    var actionResult = testStepActionAgent.executeWithRetry(() -> {
                        testStepActionAgent.execute(actionInstruction, testData, context.getSharedData().toString(),
                                !isUnattendedMode());
                        return null;
                    });
                    if (!actionResult.success()) {
                        return getActionResultWithError(context, actionInstruction, actionResult, testStep, executionStartTimestamp,
                                testExecutionStartTimestamp);
                    }
                    LOG.info("Action execution complete.");

                    if (verificationInstruction != null && !verificationInstruction.isBlank()) {
                        String testDataString = testStep.testData() == null ? null : join(", ", testStep.testData());
                        verificationManager.registerRunningVerification();
                        verificationExecutor.submit(() -> {
                            boolean verificationSuccess = false;
                            try {
                                sleepMillis(ACTION_VERIFICATION_DELAY_MILLIS);
                                LOG.info("Executing verification of: '{}'", verificationInstruction);
                                var verificationExecutionResult = testStepVerificationAgent.executeWithRetry(() -> {
                                    var screenshot = captureScreen();
                                    context.setVisualState(new VisualState(screenshot));
                                    return testStepVerificationAgent.verify(verificationInstruction, actionInstruction,
                                            testDataString, context.getSharedData().toString(), singleImageContent(screenshot));
                                }, result -> !result.success());

                                if (!verificationExecutionResult.success()) {
                                    var errorMessage = "Failure while verifying test step '%s'. Root cause: %s"
                                            .formatted(actionInstruction, verificationExecutionResult.message());
                                    verificationMessage.set(errorMessage);
                                    addFailedTestStep(context, testStep, errorMessage, null, executionStartTimestamp, now(),
                                            context.getVisualState().screenshot());
                                } else {
                                    VerificationExecutionResult verificationResult = verificationExecutionResult.resultPayload();
                                    if (verificationResult != null && !verificationResult.success()) {
                                        var errorMessage = "Verification failed. %s".formatted(verificationResult.message());
                                        verificationMessage.set(errorMessage);
                                        addFailedTestStep(context, testStep, errorMessage, verificationResult.message(),
                                                executionStartTimestamp, now(), context.getVisualState().screenshot());
                                        return;
                                    }
                                    LOG.info("Verification execution complete.");
                                    var actualResult =
                                            verificationResult != null ? verificationResult.message() : "Verification " + "successful";
                                    verificationMessage.set(actualResult);
                                    context.addStepResult(new TestStepResult(testStep, true, null, actualResult, null,
                                            executionStartTimestamp, now()));
                                    verificationSuccess = true;
                                }
                            } catch (Exception e) {
                                LOG.error("Unexpected error during async verification", e);
                                verificationMessage.set(e.getMessage());
                                addFailedTestStep(context, testStep, e.getMessage(), null, executionStartTimestamp, now(), captureScreen());
                            } finally {
                                verificationManager.registerVerificationResult(verificationSuccess);
                            }
                        });

                        if (!AgentConfig.isPrefetchingEnabled()) {
                            var status = verificationManager.waitForVerificationToFinish(AgentConfig.getVerificationRetryTimeoutMillis());
                            if (status.isRunning() || !status.success()) {
                                var message = verificationMessage.get();
                                if (message == null) {
                                    message = "Verification failed or timed out.";
                                }
                                return getFailedTestExecutionResult(context, testExecutionStartTimestamp, message,
                                        context.getVisualState().screenshot(), true);
                            }
                        }
                    } else {
                        context.addStepResult(new TestStepResult(testStep, true, null, "No verification required",
                                null, executionStartTimestamp, now()));
                    }
                } catch (Exception e) {
                    LOG.error("Unexpected error while executing the test step: '{}'", testStep.stepDescription(), e);
                    addFailedTestStep(context, testStep, e.getMessage(), null, now(), now(), captureScreen());
                    return getTestExecutionResultWithError(context, testExecutionStartTimestamp, e.getMessage());
                }
            }

            var finalVerificationResult = verificationManager.waitForVerificationToFinish(AgentConfig.getVerificationRetryTimeoutMillis());
            if (!finalVerificationResult.success()) {
                var message = verificationMessage.get();
                if (message == null) {
                    message = "Verification failed (timeout or unknown error)";
                }
                return getFailedTestExecutionResult(context, testExecutionStartTimestamp, message,
                        context.getVisualState().screenshot(), true);
            }

            return new TestExecutionResult(testCase.name(), PASSED, context.getExecutionHistory(), null, testExecutionStartTimestamp, now(),
                    null);
        } finally {
            screenRecorder.endScreenCapture();
        }
    }

    private static TestStepVerificationAgent getTestStepVerificationAgent(RetryState retryState) {
        var testStepVerificationAgentModel = getModel(AgentConfig.getTestStepVerificationAgentModelName(),
                AgentConfig.getTestStepVerificationAgentModelProvider());
        var testStepVerificationAgentPrompt = loadSystemPrompt("test_step/verifier",
                AgentConfig.getTestStepVerificationAgentPromptVersion(), "verification_execution_prompt.txt");
        return builder(TestStepVerificationAgent.class)
                .chatModel(testStepVerificationAgentModel.getChatModel())
                .systemMessageProvider(_ -> testStepVerificationAgentPrompt)
                .toolExecutionErrorHandler(new DefaultErrorHandler(TestStepVerificationAgent.RETRY_POLICY, retryState))
                .build();
    }

    private static TestStepActionAgent getTestStepActionAgent(CommonTools commonTools, UserInteractionTools userInteractionTools,
                                                              RetryState retryState) {
        var testStepActionAgentModel = getModel(AgentConfig.getTestStepActionAgentModelName(),
                AgentConfig.getTestStepActionAgentModelProvider());
        var testStepActionAgentPrompt = loadSystemPrompt("test_step/executor",
                AgentConfig.getTestStepActionAgentPromptVersion(), "test_step_action_agent_system_prompt.txt");
        return builder(TestStepActionAgent.class)
                .chatModel(testStepActionAgentModel.getChatModel())
                .systemMessageProvider(_ -> testStepActionAgentPrompt)
                .tools(new MouseTools(), new KeyboardTools(), new ElementLocatorTools(), commonTools, userInteractionTools)
                .toolExecutionErrorHandler(new DefaultErrorHandler(TestStepActionAgent.RETRY_POLICY, retryState))
                .build();
    }

    private static PreconditionVerificationAgent getPreconditionVerificationAgent(RetryState retryState) {
        var preconditionVerificationAgentModel = getModel(AgentConfig.getPreconditionVerificationAgentModelName(),
                AgentConfig.getPreconditionVerificationAgentModelProvider());
        var preconditionVerificationAgentPrompt = loadSystemPrompt("precondition/verifier",
                AgentConfig.getPreconditionVerificationAgentPromptVersion(), "precondition_verification_prompt.txt");
        return builder(PreconditionVerificationAgent.class)
                .chatModel(preconditionVerificationAgentModel.getChatModel())
                .systemMessageProvider(_ -> preconditionVerificationAgentPrompt)
                .toolExecutionErrorHandler(new DefaultErrorHandler(PreconditionVerificationAgent.RETRY_POLICY, retryState))
                .build();
    }

    private static PreconditionActionAgent getPreconditionActionAgent(CommonTools commonTools, UserInteractionTools userInteractionTools,
                                                                      RetryState retryState) {
        var preconditionAgentModel = getModel(AgentConfig.getPreconditionAgentModelName(), AgentConfig.getPreconditionAgentModelProvider());
        var preconditionAgentPrompt = loadSystemPrompt("precondition/executor",
                AgentConfig.getPreconditionAgentPromptVersion(), "precondition_action_agent_system_prompt.txt");
        return builder(PreconditionActionAgent.class)
                .chatModel(preconditionAgentModel.getChatModel())
                .systemMessageProvider(_ -> preconditionAgentPrompt)
                .toolExecutionErrorHandler(new DefaultErrorHandler(PreconditionActionAgent.RETRY_POLICY, retryState))
                .tools(new MouseTools(), new KeyboardTools(), new ElementLocatorTools(), commonTools, userInteractionTools)
                .build();
    }

    @NotNull
    private static TestExecutionResult getActionResultWithError(TestExecutionContext context,
                                                                String actionInstruction,
                                                                AgentExecutionResult<?> actionResult, TestStep testStep,
                                                                Instant executionStartTimestamp,
                                                                Instant testExecutionStartTimestamp) {
        var errorMessage = "Error while executing action '%s'. Root cause: %s"
                .formatted(actionInstruction, actionResult.message());
        addFailedTestStep(context, testStep, errorMessage, null, executionStartTimestamp, now(),
                actionResult.screenshot());
        return getTestExecutionResultWithError(context, testExecutionStartTimestamp, errorMessage);
    }

    @NotNull
    private static TestExecutionResult getFailedVerificationResult(TestExecutionContext context,
                                                                   VerificationExecutionResult verificationResult,
                                                                   TestStep testStep,
                                                                   Instant executionStartTimestamp, Instant testExecutionStartTimestamp,
                                                                   BufferedImage screenshot) {
        var errorMessage = "Verification failed. %s".formatted(verificationResult.message());
        addFailedTestStep(context, testStep, errorMessage, verificationResult.message(),
                executionStartTimestamp, now(), screenshot);
        return getFailedTestExecutionResult(context, testExecutionStartTimestamp, errorMessage, screenshot,
                true);
    }

    @NotNull
    private static TestExecutionResult getFailedTestExecutionResult(TestExecutionContext context,
                                                                    Instant testExecutionStartTimestamp, String errorMessage,
                                                                    BufferedImage screenshot, boolean logMessage) {
        if (logMessage) {
            LOG.error(errorMessage);
        }
        return new TestExecutionResult(context.getTestCase().name(), FAILED, context.getExecutionHistory(),
                screenshot,
                testExecutionStartTimestamp, now(), errorMessage);
    }

    @NotNull
    private static TestExecutionResult getTestExecutionResultWithError(TestExecutionContext context,
                                                                       Instant testExecutionStartTimestamp, String errorMessage,
                                                                       BufferedImage screenshot, boolean logMessage) {
        if (logMessage) {
            LOG.error(errorMessage);
        }
        return new TestExecutionResult(context.getTestCase().name(), ERROR, context.getExecutionHistory(), screenshot,
                testExecutionStartTimestamp, now(), errorMessage);
    }

    @NotNull
    private static TestExecutionResult getTestExecutionResultWithError(TestExecutionContext context,
                                                                       Instant testExecutionStartTimestamp, String errorMessage) {
        return getTestExecutionResultWithError(context, testExecutionStartTimestamp, errorMessage, null, false);
    }

    private static void addFailedTestStep(TestExecutionContext context, TestStep testStep, String errorMessage,
                                          String actualResult, Instant executionStartTimestamp,
                                          Instant executionEndTimestamp, BufferedImage screenshot) {
        context.addStepResult(new TestStepResult(testStep, false, errorMessage, actualResult, screenshot,
                executionStartTimestamp, executionEndTimestamp));
    }

    private static class DefaultErrorHandler implements ToolExecutionErrorHandler {
        private static final List<ErrorCategory> terminalErrors = List.of(NON_RETRYABLE_ERROR, TIMEOUT, USER_INTERRUPTION);
        private final RetryPolicy retryPolicy;
        private final RetryState retryState;

        public DefaultErrorHandler(RetryPolicy retryPolicy, RetryState retryState) {
            this.retryPolicy = retryPolicy;
            this.retryState = retryState;
        }

        @Override
        public ToolErrorHandlerResult handle(Throwable error, ToolErrorContext context) {
            if (error instanceof ElementLocationException elementLocationException) {
                return handleRetryableToolError(elementLocationException.getMessage());
            } else if (error instanceof ToolExecutionException toolExecutionException) {
                if (terminalErrors.contains(toolExecutionException.getErrorCategory())) {
                    throw toolExecutionException;
                } else {
                    return handleRetryableToolError(toolExecutionException.getMessage());
                }
            } else {
                throw new RuntimeException(error);
            }
        }

        private ToolErrorHandlerResult handleRetryableToolError(String message) throws ToolExecutionException {
            retryState.startIfNotStarted();
            int attempts = retryState.incrementAttempts();
            long elapsedTime = retryState.getElapsedTime();
            boolean isTimeout = retryPolicy.timeoutMillis() > 0 && elapsedTime > retryPolicy.timeoutMillis();
            boolean isMaxRetriesReached = attempts > retryPolicy.maxRetries();

            if (isTimeout) {
                throw new ToolExecutionException("Retry policy exceeded because of timeout. Original error: " + message, TIMEOUT);
            } else if (isMaxRetriesReached) {
                throw new ToolExecutionException("Retry policy exceeded because of max retries. Original error: " + message, TIMEOUT);
            } else {
                LOG.info("Passing the following tool execution error to the agent: '{}'", message);
                return new ToolErrorHandlerResult(message);
            }
        }
    }
}