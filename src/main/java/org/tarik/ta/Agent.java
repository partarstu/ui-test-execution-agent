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

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.agents.PreconditionActionAgent;
import org.tarik.ta.agents.PreconditionVerificationAgent;
import org.tarik.ta.agents.TestStepActionAgent;
import org.tarik.ta.agents.TestStepVerificationAgent;
import org.tarik.ta.dto.TestExecutionResult;
import org.tarik.ta.dto.TestExecutionResult.TestExecutionStatus;
import org.tarik.ta.dto.TestStepResult;
import org.tarik.ta.dto.VerificationExecutionResult;
import org.tarik.ta.error.RetryPolicy;
import org.tarik.ta.helper_entities.TestCase;
import org.tarik.ta.helper_entities.TestStep;
import org.tarik.ta.model.TestExecutionContext;
import org.tarik.ta.model.VisualState;
import org.tarik.ta.tools.AgentExecutionResult;
import org.tarik.ta.tools.CommonTools;
import org.tarik.ta.tools.KeyboardTools;
import org.tarik.ta.tools.MouseTools;
import org.tarik.ta.utils.ScreenRecorder;
import org.tarik.ta.tools.UserInteractionTools;
import org.tarik.ta.rag.RetrieverFactory;
import org.tarik.ta.manager.VerificationManager;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static dev.langchain4j.service.AiServices.builder;
import static java.lang.String.join;
import static java.time.Instant.now;
import static java.util.Optional.ofNullable;
import static org.tarik.ta.AgentConfig.getActionVerificationDelayMillis;
import static org.tarik.ta.AgentConfig.isUnattendedMode;
import static org.tarik.ta.dto.TestExecutionResult.TestExecutionStatus.FAILED;
import static org.tarik.ta.dto.TestExecutionResult.TestExecutionStatus.PASSED;
import static org.tarik.ta.model.ModelFactory.getInstructionModel;
import static org.tarik.ta.utils.CommonUtils.captureScreen;
import static org.tarik.ta.utils.CommonUtils.sleepMillis;

public class Agent {
    private static final Logger LOG = LoggerFactory.getLogger(Agent.class);
    protected static final int ACTION_VERIFICATION_DELAY_MILLIS = getActionVerificationDelayMillis();

    // Retry Policies
    private static final RetryPolicy ACTION_RETRY_POLICY = new RetryPolicy(3, 1000, 5000, 2.0, 30000);
    // Verification policy: fixed interval retry (every 2s, up to 30s)
    private static final RetryPolicy VERIFICATION_RETRY_POLICY = new RetryPolicy(15, 2000, 2000, 1.0, 30000);

    public static TestExecutionResult executeTestCase(TestCase testCase) {
        ScreenRecorder screenRecorder = new ScreenRecorder();
        VerificationManager verificationManager = new VerificationManager();
        try (ExecutorService verificationExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            screenRecorder.beginScreenCapture();
            var testExecutionStartTimestamp = now();
            var context = new TestExecutionContext(testCase, new VisualState(captureScreen()));
            var chatModel = getInstructionModel().getChatModel();
            var userInteractionTools = new UserInteractionTools(RetrieverFactory.getUiElementRetriever());
            var commonTools = new CommonTools(verificationManager);
            var preconditionActionAgent = builder(PreconditionActionAgent.class)
                    .chatModel(chatModel)
                    .tools(new MouseTools(), new KeyboardTools(), commonTools, userInteractionTools)
                    .build();
            var preconditionVerificationAgent = builder(PreconditionVerificationAgent.class)
                    .chatModel(chatModel)
                    .build();
            var testStepActionAgent = builder(TestStepActionAgent.class)
                    .chatModel(chatModel)
                    .tools(new MouseTools(), new KeyboardTools(), commonTools, userInteractionTools)
                    .build();
            var testStepVerificationAgent = builder(TestStepVerificationAgent.class)
                    .chatModel(chatModel)
                    .build();

            List<String> preconditions = testCase.preconditions();
            if (preconditions != null && !preconditions.isEmpty()) {
                LOG.info("Executing and verifying preconditions for test case: {}", testCase.name());

                for (String precondition : preconditions) {
                    LOG.info("Executing precondition: {}", precondition);
                    var preconditionExecutionResult = preconditionActionAgent.executeWithRetry(
                            () -> preconditionActionAgent.execute(precondition, context.getSharedData().toString(), !isUnattendedMode()),
                            ACTION_RETRY_POLICY);

                    if (!preconditionExecutionResult.success()) {
                        var errorMessage = "Error while executing precondition '%s'. Root cause: %s"
                                .formatted(precondition, preconditionExecutionResult.message());
                        return getTestExecutionResultWithError(context,
                                testExecutionStartTimestamp, errorMessage,
                                captureScreen(), true);
                    }
                    LOG.info("Precondition execution complete.");

                    var verificationExecutionResult = preconditionVerificationAgent
                            .executeWithRetry(() -> {
                                var screenshot = captureScreen();
                                context.setVisualState(new VisualState(screenshot));
                                return preconditionVerificationAgent.verify(
                                        precondition,
                                        context.getSharedData().toString(),
                                        screenshot);
                            }, VERIFICATION_RETRY_POLICY, result -> !result.success());

                    if (!verificationExecutionResult.success()) {
                        var errorMessage = "Error while verifying precondition '%s'. Root cause: %s"
                                .formatted(precondition,
                                        verificationExecutionResult.message());
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
                    var actionResult = testStepActionAgent.executeWithRetry(
                            () -> testStepActionAgent.execute(actionInstruction, testData, context.getSharedData().toString(),
                                    !isUnattendedMode()), ACTION_RETRY_POLICY);
                    if (!actionResult.success()) {
                        return getActionResultWithError(context, actionInstruction, actionResult, testStep, executionStartTimestamp,
                                testExecutionStartTimestamp);
                    }
                    LOG.info("Action execution complete.");

                    if (verificationInstruction != null && !verificationInstruction.isBlank()) {
                        String testDataString = testStep.testData() == null ? null
                                : join(", ", testStep.testData());
                        CompletableFuture<VerificationExecutionResult> future = CompletableFuture.supplyAsync(() -> {
                            try {
                                sleepMillis(ACTION_VERIFICATION_DELAY_MILLIS);
                                LOG.info("Executing verification of: '{}'", verificationInstruction);
                                var verificationExecutionResult = testStepVerificationAgent
                                        .executeWithRetry(() -> {
                                                    var screenshot = captureScreen();
                                                    context.setVisualState(new VisualState(screenshot));
                                                    return testStepVerificationAgent.verify(verificationInstruction, actionInstruction, testDataString,
                                                            context.getSharedData().toString(), screenshot);
                                                }, VERIFICATION_RETRY_POLICY,
                                                result -> !result.success());

                                if (!verificationExecutionResult.success()) {
                                    var errorMessage = "Failure while verifying test step '%s'. Root cause: %s"
                                            .formatted(actionInstruction, verificationExecutionResult.message());
                                    addFailedTestStep(context, testStep, errorMessage, null, executionStartTimestamp, now(),
                                            context.getVisualState().screenshot());
                                    return new VerificationExecutionResult(false, errorMessage);
                                }

                                VerificationExecutionResult verificationResult = verificationExecutionResult.resultPayload();
                                if (verificationResult != null && !verificationResult.success()) {
                                    var errorMessage = "Verification failed. %s".formatted(verificationResult.message());
                                    addFailedTestStep(context, testStep, errorMessage, verificationResult.message(),
                                            executionStartTimestamp, now(), context.getVisualState().screenshot());
                                    return verificationResult;
                                }
                                LOG.info("Verification execution complete.");
                                String actualResult = verificationResult != null ? verificationResult.message() : "Verification successful";
                                context.addStepResult(new TestStepResult(testStep, true, null, actualResult, null, executionStartTimestamp, now()));
                                return verificationResult != null ? verificationResult : new VerificationExecutionResult(true, actualResult);
                            } catch (Exception e) {
                                LOG.error("Unexpected error during async verification", e);
                                addFailedTestStep(context, testStep, e.getMessage(), null, executionStartTimestamp, now(), captureScreen());
                                return new VerificationExecutionResult(false, e.getMessage());
                            }
                        }, verificationExecutor);
                        verificationManager.setVerificationFuture(future);
                    } else {
                        context.addStepResult(new TestStepResult(testStep, true, null, "No verification required", null, executionStartTimestamp, now()));
                    }
                } catch (Exception e) {
                    LOG.error("Unexpected error while executing the test step: '{}'", testStep.stepDescription(), e);
                    addFailedTestStep(context, testStep, e.getMessage(), null, now(), now(), captureScreen());
                    return getTestExecutionResultWithError(context, testExecutionStartTimestamp, e.getMessage());
                }
            }

            var finalVerificationResult = verificationManager.waitForVerificationToFinish();
            if (!finalVerificationResult.success()) {
                return getFailedTestExecutionResult(context, testExecutionStartTimestamp, finalVerificationResult.message(),
                        context.getVisualState().screenshot(), true);
            }

            return new TestExecutionResult(testCase.name(), PASSED, context.getExecutionHistory(), null, testExecutionStartTimestamp, now(),
                    null);
        } finally {
            screenRecorder.endScreenCapture();
        }
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
        return new TestExecutionResult(context.getTestCase().name(), TestExecutionStatus.ERROR,
                context.getExecutionHistory(), screenshot,
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
                executionStartTimestamp,
                executionEndTimestamp));
    }
}