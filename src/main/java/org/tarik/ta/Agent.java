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
import org.tarik.ta.helper_entities.TestCase;
import org.tarik.ta.helper_entities.TestStep;
import org.tarik.ta.tools.AgentExecutionResult;
import org.tarik.ta.tools.CommonTools;
import org.tarik.ta.tools.KeyboardTools;
import org.tarik.ta.tools.MouseTools;
import org.tarik.ta.utils.ScreenRecorder;
import org.tarik.ta.services.UserInteractionServiceImpl;
import org.tarik.ta.rag.RetrieverFactory;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.service.AiServices.builder;
import static java.lang.String.join;
import static java.time.Instant.now;
import static org.tarik.ta.AgentConfig.getActionVerificationDelayMillis;
import static org.tarik.ta.dto.TestExecutionResult.TestExecutionStatus.FAILED;
import static org.tarik.ta.dto.TestExecutionResult.TestExecutionStatus.PASSED;
import static org.tarik.ta.model.ModelFactory.getInstructionModel;
import static org.tarik.ta.utils.CommonUtils.captureScreen;
import static org.tarik.ta.utils.CommonUtils.sleepMillis;

public class Agent {
    private static final Logger LOG = LoggerFactory.getLogger(Agent.class);
    protected static final int ACTION_VERIFICATION_DELAY_MILLIS = getActionVerificationDelayMillis();

    public static TestExecutionResult executeTestCase(TestCase testCase) {
        ScreenRecorder screenRecorder = new ScreenRecorder();
        try {
            screenRecorder.beginScreenCapture();
            var testExecutionStartTimestamp = now();
            List<TestStepResult> stepResults = new ArrayList<>();

            var chatModel = getInstructionModel().getChatModel();
            var userInteractionService = new UserInteractionServiceImpl(RetrieverFactory.getUiElementRetriever());
            var preconditionActionAgent = builder(PreconditionActionAgent.class)
                    .chatModel(chatModel)
                    .tools(new MouseTools(), new KeyboardTools(), new CommonTools(), userInteractionService)
                    .build();
            var preconditionVerificationAgent = builder(PreconditionVerificationAgent.class)
                    .chatModel(chatModel)
                    .build();
            var testStepActionAgent = builder(TestStepActionAgent.class)
                    .chatModel(chatModel)
                    .tools(new MouseTools(), new KeyboardTools(), new CommonTools(), userInteractionService)
                    .build();
            var testStepVerificationAgent = builder(TestStepVerificationAgent.class)
                    .chatModel(chatModel)
                    .build();

            List<String> preconditions = testCase.preconditions();
            if (preconditions != null && !preconditions.isEmpty()) {
                LOG.info("Executing and verifying preconditions for test case: {}", testCase.name());

                for (String precondition : preconditions) {
                    LOG.info("Executing precondition: {}", precondition);
                    var executionResult = preconditionActionAgent.executeAndGetResult(() -> preconditionActionAgent.execute(precondition));

                    if (!executionResult.success()) {
                        var errorMessage = "Failure while executing precondition '%s'. Root cause: %s"
                                .formatted(precondition, executionResult.message());
                        return getFailedTestExecutionResult(testCase, stepResults, testExecutionStartTimestamp, errorMessage,
                                captureScreen(), true);
                    }
                    LOG.info("Precondition execution complete.");

                    var screenshot = captureScreen();
                    var verificationResult = preconditionVerificationAgent.executeAndGetResult(
                            () -> preconditionVerificationAgent.verify(precondition, screenshot));

                    if (!verificationResult.success()) {
                        var errorMessage = "Failure while verifying precondition '%s'. Root cause: %s"
                                .formatted(precondition, verificationResult.message());
                        return getFailedTestExecutionResult(testCase, stepResults, testExecutionStartTimestamp, errorMessage, screenshot,
                                true);
                    }

                    VerificationExecutionResult resultPayload = verificationResult.resultPayload();
                    if (resultPayload != null && !resultPayload.success()) {
                        var errorMessage = "Precondition '%s' not fulfilled, although was executed. %s".formatted(precondition,
                                resultPayload.message());
                        return getFailedTestExecutionResult(testCase, stepResults, testExecutionStartTimestamp, errorMessage, screenshot,
                                true);
                    }
                    LOG.info("Precondition '{}' is met.", precondition);
                }

                LOG.info("All preconditions are met for test case: {}", testCase.name());
            }

            for (TestStep testStep : testCase.testSteps()) {
                var actionInstruction = testStep.stepDescription();
                var verificationInstruction = testStep.expectedResults();
                try {
                    var executionStartTimestamp = now();
                    LOG.info("Executing test step: {}", actionInstruction);
                    var actionResult = testStepActionAgent.executeAndGetResult(() -> testStepActionAgent.execute(actionInstruction));

                    if (!actionResult.success()) {
                        return getFailedActionResult(testCase, actionInstruction, actionResult, testStep, stepResults,
                                executionStartTimestamp, testExecutionStartTimestamp);
                    }
                    LOG.info("Action execution complete.");

                    String actualResult = null;
                    if (verificationInstruction != null && !verificationInstruction.isBlank()) {
                        sleepMillis(ACTION_VERIFICATION_DELAY_MILLIS);
                        LOG.info("Executing verification of: '{}'", verificationInstruction);
                        String testDataString = testStep.testData() == null ? null : join(", ", testStep.testData());

                        var screenshot = captureScreen();
                        var verificationResult = testStepVerificationAgent
                                .executeAndGetResult(
                                        () -> testStepVerificationAgent.verify(verificationInstruction, actionInstruction, testDataString,
                                                screenshot));

                        if (!verificationResult.success()) {
                            var errorMessage = "Failure while verifying test step '%s'. Root cause: %s".formatted(actionInstruction,
                                    verificationResult.message());
                            addFailedTestStep(testStep, stepResults, errorMessage, null, executionStartTimestamp, now(), screenshot);
                            return getFailedTestExecutionResult(testCase, stepResults, testExecutionStartTimestamp, errorMessage,
                                    screenshot, true);
                        }

                        VerificationExecutionResult resultPayload = verificationResult.resultPayload();
                        if (resultPayload != null && !resultPayload.success()) {
                            return getFailedVerificationResult(testCase, resultPayload, testStep, stepResults, executionStartTimestamp,
                                    testExecutionStartTimestamp, screenshot);
                        }
                        LOG.info("Verification execution complete.");
                        actualResult = resultPayload != null ? resultPayload.message() : "Verification successful";
                    }

                    stepResults.add(new TestStepResult(testStep, true, null, actualResult, null, executionStartTimestamp, now()));
                } catch (Exception e) {
                    LOG.error("Unexpected error while executing the test step: '{}'", testStep.stepDescription(), e);
                    addFailedTestStep(testStep, stepResults, e.getMessage(), null, now(), now(), captureScreen());
                    return getTestExecutionResultWithError(testCase, stepResults, testExecutionStartTimestamp, e.getMessage());
                }
            }
            return new TestExecutionResult(testCase.name(), PASSED, stepResults, null, testExecutionStartTimestamp, now(), null);
        } finally {
            screenRecorder.endScreenCapture();
        }
    }

    @NotNull
    private static TestExecutionResult getFailedActionResult(TestCase testCase, String actionInstruction,
                                                             AgentExecutionResult<?> actionResult, TestStep testStep,
                                                             List<TestStepResult> stepResults, Instant executionStartTimestamp,
                                                             Instant testExecutionStartTimestamp) {
        var errorMessage = "Failure while executing action '%s'. Root cause: %s"
                .formatted(actionInstruction, actionResult.message());
        addFailedTestStep(testStep, stepResults, errorMessage, null, executionStartTimestamp, now(), actionResult.screenshot());
        return getTestExecutionResultWithError(testCase, stepResults, testExecutionStartTimestamp, errorMessage);
    }

    @NotNull
    private static TestExecutionResult getFailedVerificationResult(TestCase testCase,
                                                                   VerificationExecutionResult verificationResult,
                                                                   TestStep testStep, List<TestStepResult> stepResults,
                                                                   Instant executionStartTimestamp, Instant testExecutionStartTimestamp,
                                                                   BufferedImage screenshot) {
        var errorMessage = "Verification failed. %s".formatted(verificationResult.message());
        addFailedTestStep(testStep, stepResults, errorMessage, verificationResult.message(),
                executionStartTimestamp, now(), screenshot);
        return getFailedTestExecutionResult(testCase, stepResults, testExecutionStartTimestamp, errorMessage, screenshot, true);
    }

    @NotNull
    private static TestExecutionResult getFailedTestExecutionResult(TestCase testCase,
                                                                    List<TestStepResult> stepResults,
                                                                    Instant testExecutionStartTimestamp, String errorMessage,
                                                                    BufferedImage screenshot, boolean logMessage) {
        if (logMessage) {
            LOG.error(errorMessage);
        }
        return new TestExecutionResult(testCase.name(), FAILED, stepResults, screenshot, testExecutionStartTimestamp, now(), errorMessage);
    }

    @NotNull
    private static TestExecutionResult getTestExecutionResultWithError(TestCase testCase,
                                                                       List<TestStepResult> stepResults,
                                                                       Instant testExecutionStartTimestamp, String errorMessage,
                                                                       BufferedImage screenshot, boolean logMessage) {
        if (logMessage) {
            LOG.error(errorMessage);
        }
        return new TestExecutionResult(testCase.name(), TestExecutionStatus.ERROR, stepResults, screenshot,
                testExecutionStartTimestamp, now(), errorMessage);
    }

    @NotNull
    private static TestExecutionResult getTestExecutionResultWithError(TestCase testCase,
                                                                       List<TestStepResult> stepResults,
                                                                       Instant testExecutionStartTimestamp, String errorMessage) {
        return getTestExecutionResultWithError(testCase, stepResults, testExecutionStartTimestamp, errorMessage, null, false);
    }

    private static void addFailedTestStep(TestStep testStep, List<TestStepResult> stepResults, String errorMessage,
                                          String actualResult, Instant executionStartTimestamp,
                                          Instant executionEndTimestamp, BufferedImage screenshot) {
        stepResults.add(new TestStepResult(testStep, false, errorMessage, actualResult, screenshot, executionStartTimestamp,
                executionEndTimestamp));
    }
}