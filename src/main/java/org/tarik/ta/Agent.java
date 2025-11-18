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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.dto.*;
import org.tarik.ta.dto.TestExecutionResult.TestExecutionStatus;
import org.tarik.ta.helper_entities.ActionExecutionResult;
import org.tarik.ta.helper_entities.TestCase;
import org.tarik.ta.helper_entities.TestStep;
import org.tarik.ta.model.GenAiModel;
import org.tarik.ta.prompts.ActionExecutionPlanPrompt.Builder.ActionInfo;
import org.tarik.ta.prompts.PreconditionVerificationPrompt;
import org.tarik.ta.prompts.ActionExecutionPlanPrompt;
import org.tarik.ta.prompts.VerificationExecutionPrompt;
import org.tarik.ta.tools.ToolExecutionResult;
import org.tarik.ta.tools.CommonTools;
import org.tarik.ta.tools.KeyboardTools;
import org.tarik.ta.tools.MouseTools;
import org.tarik.ta.utils.ScreenRecorder;

import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationsFrom;
import static java.lang.String.join;
import static java.time.Instant.now;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.tarik.ta.AgentConfig.*;
import static org.tarik.ta.dto.TestExecutionResult.TestExecutionStatus.*;
import static org.tarik.ta.model.ModelFactory.getInstructionModel;
import static org.tarik.ta.tools.AbstractTools.ToolExecutionStatus.SUCCESS;
import static org.tarik.ta.utils.CommonUtils.*;
import static org.tarik.ta.utils.Verifier.verify;

public class Agent {
    private static final Logger LOG = LoggerFactory.getLogger(Agent.class);
    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    protected static final Map<String, Tool> allToolsByName = getToolsByName();
    protected static final int TEST_STEP_EXECUTION_RETRY_TIMEOUT_MILLIS = getTestStepExecutionRetryTimeoutMillis();
    protected static final int TEST_STEP_RETRY_INTERVAL_MILLIS = getTestStepExecutionRetryIntervalMillis();
    protected static final int ACTION_VERIFICATION_DELAY_MILLIS = getActionVerificationDelayMillis();
    private static final String ACTION_EXECUTION_PLAN = "action execution plan generation";

    private record TestStepById(String id, TestStep testStep) {
    }

    private record PreconditionById(String id, String precondition) {
    }

    public static TestExecutionResult executeTestCase(TestCase testCase) {
        ScreenRecorder screenRecorder = new ScreenRecorder();
        try {
            screenRecorder.beginScreenCapture();
            var testExecutionStartTimestamp = now();
            List<TestStepResult> stepResults = new ArrayList<>();

            List<String> preconditions = testCase.preconditions();
            if (preconditions != null && !preconditions.isEmpty()) {
                LOG.info("Executing and verifying preconditions for test case: {}", testCase.name());
                AtomicInteger counter = new AtomicInteger();
                List<PreconditionById> preconditionByIds = preconditions.stream()
                        .map(precondition -> new PreconditionById("" + counter.incrementAndGet(), precondition))
                        .toList();
                Map<String, String> preconditionByIdMap = preconditionByIds.stream()
                        .collect(toMap(PreconditionById::id, PreconditionById::precondition));
                var preconditionExecutionPlans = getPreconditionExecutionPlans(preconditionByIdMap);
                if (preconditionExecutionPlans == null || preconditionExecutionPlans.isEmpty()) {
                    var errorMessage = "Could not generate execution plan for preconditions";
                    return getTestExecutionResultWithError(testCase, stepResults, testExecutionStartTimestamp, errorMessage,
                            captureScreen(), true);
                }
                if (preconditionExecutionPlans.size() != preconditions.size()) {
                    var errorMessage = ("Not all preconditions were included into the execution plan. Expected to have %d of them," +
                            " but got %d.").formatted(preconditions.size(), preconditionExecutionPlans.size());
                    return getTestExecutionResultWithError(testCase, stepResults, testExecutionStartTimestamp, errorMessage,
                            captureScreen(), true);
                }

                for (var preconditionExecutionPlan : preconditionExecutionPlans) {
                    var precondition = preconditionByIdMap.get(preconditionExecutionPlan.actionUniqueId());
                    checkArgument(isNotBlank(preconditionExecutionPlan.toolName()),
                            "No suitable tool has been found for executing the the precondition '%s'.", precondition);
                    LOG.info("Executing precondition: {}", precondition);
                    var preconditionExecutionResult = processToolExecutionRequest(preconditionExecutionPlan);
                    if (!preconditionExecutionResult.success()) {
                        var errorMessage = "Failure while executing precondition '%s'. Root cause: %s"
                                .formatted(preconditionExecutionPlan, preconditionExecutionResult.message());
                        return getFailedTestExecutionResult(testCase, stepResults, testExecutionStartTimestamp, errorMessage,
                                captureScreen(), true);
                    }
                    LOG.info("Precondition execution complete.");

                    var preconditionValidationResult = processPreconditionValidationRequest(precondition);
                    if (!preconditionValidationResult.success()) {
                        var errorMessage = "Precondition '%s' not fulfilled, although was executed. %s"
                                .formatted(precondition, preconditionValidationResult.message());
                        return getFailedTestExecutionResult(testCase, stepResults, testExecutionStartTimestamp, errorMessage,
                                preconditionValidationResult.screenshot(), true);
                    }
                    LOG.info("Precondition '{}' is met.", precondition);
                }

                LOG.info("All preconditions are met for test case: {}", testCase.name());
            }

            AtomicInteger counter = new AtomicInteger();
            List<TestStepById> testStepByIds = testCase.testSteps().stream()
                    .map(testStep -> new TestStepById("" + counter.incrementAndGet(), testStep))
                    .toList();
            Map<String, TestStep> testStepByUuidMap = testStepByIds.stream()
                    .collect(toMap(TestStepById::id, TestStepById::testStep));
            List<ActionInfo> stepInfos = testStepByIds.stream()
                    .map(testStepById -> new ActionInfo(testStepById.id(), testStepById.testStep().stepDescription(),
                            testStepById.testStep().testData()))
                    .toList();
            var testCaseExecutionPlan = getActionExecutionPlan(stepInfos);
            for (var testStepExecutionPlan : testCaseExecutionPlan.testStepExecutionPlans()) {
                var testStep = testStepByUuidMap.get(testStepExecutionPlan.actionUniqueId());
                checkArgument(testStep != null, "Test step with ID '%s' not found inside the execution plan.",
                        testStepExecutionPlan.actionUniqueId());
                checkArgument(isNotBlank(testStepExecutionPlan.toolName()),
                        "No suitable tool has been found for executing the action '%s'.", testStep.stepDescription());
                var actionInstruction = testStep.stepDescription();
                var verificationInstruction = testStep.expectedResults();
                try {
                    var executionStartTimestamp = now();
                    var actionResult = processToolExecutionRequest(testStepExecutionPlan);
                    if (!actionResult.success()) {
                        return getFailedActionResult(testCase, actionInstruction, actionResult, testStep, stepResults,
                                executionStartTimestamp,
                                testExecutionStartTimestamp);
                    }
                    LOG.info("Action execution complete.");

                    String actualResult = null;
                    if (isNotBlank(verificationInstruction)) {
                        sleepMillis(ACTION_VERIFICATION_DELAY_MILLIS);
                        LOG.info("Executing verification of: '{}'", verificationInstruction);
                        String testDataString = testStep.testData() == null ? null : join(", ", testStep.testData());
                        AtomicReference<BufferedImage> screenshotRef = new AtomicReference<>();
                        var promptBuilder = VerificationExecutionPrompt.builder()
                                .withVerificationDescription(verificationInstruction)
                                .withActionDescription(actionInstruction)
                                .withActionTestData(testDataString);
                        var verificationResult =
                                verify(() -> promptBuilder.screenshot(screenshotRef.updateAndGet(_ -> captureScreen())).build());
                        if (!verificationResult.success()) {
                            return getFailedVerificationResult(testCase, verificationResult, testStep, stepResults, executionStartTimestamp,
                                    testExecutionStartTimestamp, screenshotRef.get());
                        }
                        LOG.info("Verification execution complete.");
                        actualResult = verificationResult.message();
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
                                                             ActionExecutionResult actionResult, TestStep testStep,
                                                             List<TestStepResult> stepResults, Instant executionStartTimestamp,
                                                             Instant testExecutionStartTimestamp) {
        var errorMessage = "Failure while executing action '%s'. Root cause: %s"
                .formatted(actionInstruction, actionResult.message());
        addFailedTestStep(testStep, stepResults, errorMessage, null, executionStartTimestamp, now(), actionResult.screenshot());
        return getTestExecutionResultWithError(testCase, stepResults, testExecutionStartTimestamp, errorMessage);
    }

    @NotNull
    private static TestExecutionResult getFailedVerificationResult(TestCase testCase, VerificationExecutionResult verificationResult,
                                                                   TestStep testStep, List<TestStepResult> stepResults,
                                                                   Instant executionStartTimestamp, Instant testExecutionStartTimestamp,
                                                                   BufferedImage screenshot) {
        var errorMessage = "Verification failed. %s".formatted(verificationResult.message());
        addFailedTestStep(testStep, stepResults, errorMessage, verificationResult.message(),
                executionStartTimestamp, now(), screenshot);
        return getFailedTestExecutionResult(testCase, stepResults, testExecutionStartTimestamp, errorMessage,
                screenshot, true);
    }

    @NotNull
    private static TestExecutionResult getFailedTestExecutionResult(TestCase testCase, List<TestStepResult> stepResults,
                                                                    Instant testExecutionStartTimestamp, String errorMessage,
                                                                    BufferedImage screenshot, boolean logMessage) {
        if (logMessage) {
            LOG.error(errorMessage);
        }
        return new TestExecutionResult(testCase.name(), FAILED, stepResults, screenshot, testExecutionStartTimestamp,
                now(), errorMessage);
    }

    private static List<TestStepExecutionPlan> getPreconditionExecutionPlans(Map<String, String> preconditionsById) {
        List<ActionInfo> stepInfos = preconditionsById.entrySet().stream()
                .map(preconditionById -> new ActionInfo(preconditionById.getKey(), preconditionById.getValue(), List.of()))
                .toList();
        var preconditionExecutionPlan = getActionExecutionPlan(stepInfos);
        return preconditionExecutionPlan.testStepExecutionPlans();
    }

    @NotNull
    private static TestExecutionResult getTestExecutionResultWithError(TestCase testCase, List<TestStepResult> stepResults,
                                                                       Instant testExecutionStartTimestamp, String errorMessage,
                                                                       BufferedImage screenshot, boolean logMessage) {
        if (logMessage) {
            LOG.error(errorMessage);
        }
        return new TestExecutionResult(testCase.name(), TestExecutionStatus.ERROR, stepResults, screenshot,
                testExecutionStartTimestamp, now(), errorMessage);
    }

    @NotNull
    private static TestExecutionResult getTestExecutionResultWithError(TestCase testCase, List<TestStepResult> stepResults,
                                                                       Instant testExecutionStartTimestamp, String errorMessage) {
        return getTestExecutionResultWithError(testCase, stepResults, testExecutionStartTimestamp, errorMessage, null, false);
    }

    private static void addFailedTestStep(TestStep testStep, List<TestStepResult> stepResults, String errorMessage,
                                          String actualResult, Instant executionStartTimestamp,
                                          Instant executionEndTimestamp, BufferedImage screenshot) {
        stepResults.add(new TestStepResult(testStep, false, errorMessage, actualResult, screenshot, executionStartTimestamp,
                executionEndTimestamp));
    }

    private static ActionExecutionResult processToolExecutionRequest(TestStepExecutionPlan testStepExecutionPlan) {
        var startTime = Instant.now();
        var deadline = now().plusMillis(TEST_STEP_EXECUTION_RETRY_TIMEOUT_MILLIS);
        Map<String, String> toolExecutionInfoByToolName = new HashMap<>();

        LOG.info("Executing tool execution request '{}'", testStepExecutionPlan);
        while (true) {
            try {
                var toolExecutionResult = executeRequestedTool(testStepExecutionPlan.toolName(), testStepExecutionPlan.arguments());
                if (toolExecutionResult.executionStatus() == SUCCESS) {
                    toolExecutionInfoByToolName.put(testStepExecutionPlan.toolName(), toolExecutionResult.message());
                    break;
                } else if (!toolExecutionResult.retryMakesSense()) {
                    LOG.info("Tool execution failed and retry doesn't make sense. Interrupting the execution.");
                    toolExecutionInfoByToolName.put(testStepExecutionPlan.toolName(), toolExecutionResult.message());
                    var screenshot = toolExecutionResult.screenshot() != null ? toolExecutionResult.screenshot() : captureScreen();
                    return getFailedActionExecutionResult(toolExecutionInfoByToolName, screenshot, startTime);
                } else {
                    var nextRetryMoment = now().plusMillis(TEST_STEP_RETRY_INTERVAL_MILLIS);
                    if (nextRetryMoment.isBefore(deadline)) {
                        LOG.info("Tool execution wasn't successful, retrying. Root cause: {}", toolExecutionResult.message());
                        waitUntil(nextRetryMoment);
                    } else {
                        LOG.warn("Tool execution retries exhausted, interrupting the execution.");
                        toolExecutionInfoByToolName.put(testStepExecutionPlan.toolName(), toolExecutionResult.message());
                        var screenshot = toolExecutionResult.screenshot() != null ? toolExecutionResult.screenshot() : captureScreen();
                        return getFailedActionExecutionResult(toolExecutionInfoByToolName, screenshot, startTime);
                    }
                }
            } catch (InvocationTargetException e) {
                var cause = e.getTargetException();
                LOG.error("Got exception while invoking requested tools:", cause);
                String errorMessage;
                if (cause instanceof IllegalArgumentException) {
                    errorMessage = "Invalid arguments for tool '%s': %s".formatted(testStepExecutionPlan.toolName(), cause.getMessage());
                } else {
                    errorMessage = cause.getLocalizedMessage();
                }
                toolExecutionInfoByToolName.put(testStepExecutionPlan.toolName(), errorMessage);
                return getFailedActionExecutionResult(toolExecutionInfoByToolName, captureScreen(), startTime);
            } catch (NumberFormatException e) {
                LOG.error("Got exception while invoking requested tools:", e);
                String errorMessage = "Invalid arguments for tool '%s': %s".formatted(testStepExecutionPlan.toolName(), e.getMessage());
                toolExecutionInfoByToolName.put(testStepExecutionPlan.toolName(), errorMessage);
                return getFailedActionExecutionResult(toolExecutionInfoByToolName, captureScreen(), startTime);
            } catch (Exception e) {
                LOG.error("Got exception while invoking requested tools:", e);
                toolExecutionInfoByToolName.put(testStepExecutionPlan.toolName(), e.getLocalizedMessage());
                return getFailedActionExecutionResult(toolExecutionInfoByToolName, captureScreen(), startTime);
            }
        }

        return new ActionExecutionResult(true, getToolExecutionDetails(toolExecutionInfoByToolName), null, startTime, Instant.now());
    }

    private static @NotNull TestCaseExecutionPlan getActionExecutionPlan(List<ActionInfo> actions) {
        var toolSpecs = allToolsByName.values().stream().map(Tool::toolSpecification).toList();
        try (var model = getActionProcessingModel()) {
            var prompt = ActionExecutionPlanPrompt.builder()
                    .withActions(actions)
                    .withToolSpecs(toolSpecs)
                    .build();
            return model.generateAndGetResponseAsObject(prompt, ACTION_EXECUTION_PLAN);
        }
    }

    @NotNull
    private static ActionExecutionResult getFailedActionExecutionResult(Map<String, String> toolExecutionInfoByToolName,
                                                                        BufferedImage screenshot, Instant startTime) {
        return new ActionExecutionResult(false, getToolExecutionDetails(toolExecutionInfoByToolName), screenshot, 
                startTime, Instant.now());
    }

    @NotNull
    private static String getToolExecutionDetails(Map<String, String> toolExecutionInfoByToolName) {
        return getObjectPrettyPrinted(OBJECT_MAPPER, toolExecutionInfoByToolName).orElse("None");
    }

    private static PreconditionValidationResult processPreconditionValidationRequest(String precondition) {
        AtomicReference<BufferedImage> screenshotRef = new AtomicReference<>();
        var promptBuilder = PreconditionVerificationPrompt.builder()
                .withPreconditionDescription(precondition);
        var result =
                verify(() -> promptBuilder.screenshot(screenshotRef.updateAndGet(_ -> captureScreen())).build());
        return new PreconditionValidationResult(result.success(), result.message(), screenshotRef.get());
    }

    private static ToolExecutionResult executeRequestedTool(String toolName, List<String> args)
            throws InvocationTargetException, IllegalAccessException {
        LOG.info("Model requested an execution of the tool '{}' with the following arguments: <{}>", toolName, args);
        var tool = getTool(toolName);
        Object toolInstance = tool.instance();
        Class<?> toolClass = toolInstance.getClass();
        int paramsAmount = tool.toolSpecification().parameters().properties().size();
        checkArgument(paramsAmount == args.size(),
                "Model requested tool '%s' with %s arguments, but the tool requires %s arguments.",
                toolName, args.size(), paramsAmount);
        var method = getToolClassMethod(toolClass, toolName);
        var convertedArguments = convertArguments(args, method);
        var result = getToolExecutionResult(convertedArguments, method, toolInstance);
        LOG.info("Tool execution completed '{}' using arguments: <{}>", toolName, Arrays.toString(convertedArguments));
        return result;
    }

    private static Object[] convertArguments(List<String> args, Method method) {
        var parameterTypes = method.getParameterTypes();
        Object[] convertedArgs = new Object[args.size()];
        for (int i = 0; i < args.size(); i++) {
            convertedArgs[i] = convertArgument(args.get(i), parameterTypes[i]);
        }
        return convertedArgs;
    }

    private static Object convertArgument(String value, Class<?> targetType) {
        if (targetType == String.class) {
            return value;
        } else if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(value);
        } else if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(value);
        } else if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(value);
        } else if (targetType == float.class || targetType == Float.class) {
            return Float.parseFloat(value);
        } else if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(value);
        } else {
            throw new IllegalArgumentException("Unsupported parameter type: " + targetType);
        }
    }

    private static ToolExecutionResult getToolExecutionResult(Object[] arguments, Method method,
                                                              Object toolInstance) throws IllegalAccessException, InvocationTargetException {
        return (ToolExecutionResult) method.invoke(toolInstance, arguments);
    }

    private static Tool getTool(String toolName) {
        if (!allToolsByName.containsKey(toolName)) {
            throw new IllegalArgumentException(
                    "The requested tool '%s' is not registered, please fix the prompt".formatted(toolName));
        }
        return allToolsByName.get(toolName);
    }

    @NotNull
    private static Method getToolClassMethod(Class<?> toolClass, String toolName) {
        // Find the method by name (there should be only one with @Tool annotation)
        return Arrays.stream(toolClass.getMethods())
                .filter(method -> method.getName().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Method not found: " + toolName));
    }

    private static List<Tool> getTools(Object instance) {
        return toolSpecificationsFrom(instance.getClass()).stream()
                .map(toolSpecification -> new Tool(toolSpecification.name(), toolSpecification, instance))
                .toList();
    }

    private static GenAiModel getActionProcessingModel() {
        return getInstructionModel();
    }

    private static Map<String, Tool> getToolsByName() {
        MouseTools mouseTools = new MouseTools();
        KeyboardTools keyboardTools = new KeyboardTools();
        CommonTools commonTools = new CommonTools();
        
        return Stream.of(keyboardTools, mouseTools, commonTools)
                .map(Agent::getTools)
                .flatMap(Collection::stream)
                .collect(toMap(Tool::name, identity()));
    }

    protected record Tool(String name, ToolSpecification toolSpecification, Object instance) {
    }

    public record PreconditionValidationResult(boolean success, String message, BufferedImage screenshot) {
    }
}