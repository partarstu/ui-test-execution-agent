package org.tarik.ta.agents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.dto.TestStepExecutionPlan;
import org.tarik.ta.model.GenAiModel;
import org.tarik.ta.tools.CommonTools;
import org.tarik.ta.tools.KeyboardTools;
import org.tarik.ta.tools.MouseTools;
import org.tarik.ta.tools.AgentExecutionResult;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

import static java.time.Instant.now;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.tarik.ta.AgentConfig.getTestStepExecutionRetryIntervalMillis;
import static org.tarik.ta.AgentConfig.getTestStepExecutionRetryTimeoutMillis;
import static org.tarik.ta.tools.AgentExecutionResult.ExecutionStatus.SUCCESS;
import static org.tarik.ta.utils.CommonUtils.captureScreen;
import static org.tarik.ta.utils.CommonUtils.waitUntil;

/**
 * Abstract base class for all agents.
 */
public abstract class BaseAgent {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final GenAiModel genAiModel;
    protected final Map<String, Tool> allToolsByName;

    protected BaseAgent(GenAiModel genAiModel) {
        this.genAiModel = genAiModel;
        this.allToolsByName = getToolsByName();
    }

    protected AgentExecutionResult<String> processToolExecutionRequest(TestStepExecutionPlan testStepExecutionPlan) {
        var startTime = Instant.now();
        var deadline = now().plusMillis(getTestStepExecutionRetryTimeoutMillis());

        log.info("Executing tool execution request '{}'", testStepExecutionPlan);
        while (true) {
            try {
                var toolExecutionResult = executeRequestedTool(testStepExecutionPlan.toolName(),
                        testStepExecutionPlan.arguments());
                if (toolExecutionResult.executionStatus() == SUCCESS) {
                    return new AgentExecutionResult<>(SUCCESS, toolExecutionResult.message(), false,
                            toolExecutionResult.screenshot(),
                            toolExecutionResult.resultPayload() != null ? toolExecutionResult.resultPayload().toString()
                                    : null,
                            now());
                } else if (!toolExecutionResult.retryMakesSense()) {
                    log.info("Tool execution failed and retry doesn't make sense.");
                    return new AgentExecutionResult<>(toolExecutionResult.executionStatus(),
                            toolExecutionResult.message(), false, toolExecutionResult.screenshot(), null, now());
                } else {
                    var nextRetryMoment = now().plusMillis(getTestStepExecutionRetryIntervalMillis());
                    if (nextRetryMoment.isBefore(deadline)) {
                        log.info("Tool execution wasn't successful, retrying. Root cause: {}",
                                toolExecutionResult.message());
                        waitUntil(nextRetryMoment);
                    } else {
                        log.warn("Tool execution retries exhausted.");
                        return new AgentExecutionResult<>(toolExecutionResult.executionStatus(),
                                toolExecutionResult.message(), false, toolExecutionResult.screenshot(), null, now());
                    }
                }
            } catch (Exception e) {
                log.error("Error executing tool", e);
                return new AgentExecutionResult<>(AgentExecutionResult.ExecutionStatus.ERROR, e.getMessage(), false,
                        captureScreen(), null, now());
            }
        }
    }

    protected AgentExecutionResult executeRequestedTool(String toolName, List<String> args)
            throws InvocationTargetException, IllegalAccessException {
        if (!allToolsByName.containsKey(toolName)) {
            throw new IllegalArgumentException("Tool not found: " + toolName);
        }
        Tool tool = allToolsByName.get(toolName);
        Method method = getToolClassMethod(tool.instance.getClass(), toolName);
        Object[] convertedArgs = convertArguments(args, method);
        return (AgentExecutionResult) method.invoke(tool.instance, convertedArgs);
    }

    private Method getToolClassMethod(Class<?> toolClass, String toolName) {
        return Arrays.stream(toolClass.getMethods())
                .filter(method -> method.getName().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Method not found: " + toolName));
    }

    private Object[] convertArguments(List<String> args, Method method) {
        var parameterTypes = method.getParameterTypes();
        Object[] convertedArgs = new Object[args.size()];
        for (int i = 0; i < args.size(); i++) {
            convertedArgs[i] = convertArgument(args.get(i), parameterTypes[i]);
        }
        return convertedArgs;
    }

    private Object convertArgument(String value, Class<?> targetType) {
        if (targetType == String.class)
            return value;
        if (targetType == int.class || targetType == Integer.class)
            return Integer.parseInt(value);
        if (targetType == long.class || targetType == Long.class)
            return Long.parseLong(value);
        if (targetType == double.class || targetType == Double.class)
            return Double.parseDouble(value);
        if (targetType == float.class || targetType == Float.class)
            return Float.parseFloat(value);
        if (targetType == boolean.class || targetType == Boolean.class)
            return Boolean.parseBoolean(value);
        throw new IllegalArgumentException("Unsupported parameter type: " + targetType);
    }

    private Map<String, Tool> getToolsByName() {
        MouseTools mouseTools = new MouseTools();
        KeyboardTools keyboardTools = new KeyboardTools();
        CommonTools commonTools = new CommonTools();

        return Stream.of(keyboardTools, mouseTools, commonTools)
                .map(this::getTools)
                .flatMap(Collection::stream)
                .collect(toMap(Tool::name, identity()));
    }

    private List<Tool> getTools(Object instance) {
        return ToolSpecifications.toolSpecificationsFrom(instance.getClass()).stream()
                .map(spec -> new Tool(spec.name(), spec, instance))
                .toList();
    }

    protected record Tool(String name, ToolSpecification toolSpecification, Object instance) {
    }
}
