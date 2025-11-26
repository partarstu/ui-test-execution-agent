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
package org.tarik.ta.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.AgentConfig;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static java.time.Instant.now;

public class BudgetManager {
    private static final Logger LOG = LoggerFactory.getLogger(BudgetManager.class);
    public static final int TIME_BUDGET_SECONDS = AgentConfig.getAgentExecutionTimeBudgetSeconds();
    private static final int TOKEN_BUDGET = AgentConfig.getAgentTokenBudget();
    private static final int TOOL_CALLS_BUDGET = AgentConfig.getAgentToolCallsBudget();
    private static final AtomicInteger toolCallUsage = new AtomicInteger(0);
    private static final AtomicReference<Instant> startTime = new AtomicReference<>(now());
    private static final Map<String, ModelUsage> tokenUsagePerModel = new ConcurrentHashMap<>();

    public record ModelUsage(AtomicInteger input, AtomicInteger output, AtomicInteger cached, AtomicInteger total) {
        public ModelUsage() {
            this(new AtomicInteger(0), new AtomicInteger(0), new AtomicInteger(0), new AtomicInteger(0));
        }
    }

    public static void reset() {
        toolCallUsage.set(0);
        startTime.set(now());
        tokenUsagePerModel.clear();
        LOG.info("Budget counters reset.");
    }

    public static void resetToolCallUsage() {
        toolCallUsage.set(0);
        LOG.info("Tool call usage reset.");
    }

    public static void consumeTokens(String modelName, int input, int output, int cached) {
        ModelUsage usage = tokenUsagePerModel.computeIfAbsent(modelName, _ -> new ModelUsage());
        usage.input.addAndGet(input);
        usage.output.addAndGet(output);
        usage.cached.addAndGet(cached);
        usage.total.addAndGet(input + output + cached);
    }

    public static int getAccumulatedInputTokens() {
        return tokenUsagePerModel.values().stream().mapToInt(u -> u.input.get()).sum();
    }

    public static int getAccumulatedOutputTokens() {
        return tokenUsagePerModel.values().stream().mapToInt(u -> u.output.get()).sum();
    }

    public static int getAccumulatedCachedTokens() {
        return tokenUsagePerModel.values().stream().mapToInt(u -> u.cached.get()).sum();
    }

    public static int getAccumulatedTotalTokens() {
        return getAccumulatedInputTokens() + getAccumulatedOutputTokens() + getAccumulatedCachedTokens();
    }

    public static int getAccumulatedInputTokens(String modelName) {
        ModelUsage usage = tokenUsagePerModel.get(modelName);
        return usage != null ? usage.input.get() : 0;
    }

    public static int getAccumulatedOutputTokens(String modelName) {
        ModelUsage usage = tokenUsagePerModel.get(modelName);
        return usage != null ? usage.output.get() : 0;
    }

    public static int getAccumulatedCachedTokens(String modelName) {
        ModelUsage usage = tokenUsagePerModel.get(modelName);
        return usage != null ? usage.cached.get() : 0;
    }

    public static int getAccumulatedTotalTokens(String modelName) {
        return getAccumulatedInputTokens(modelName) + getAccumulatedOutputTokens(modelName) + getAccumulatedCachedTokens(modelName);
    }

    public static void consumeToolCalls(int count) {
        toolCallUsage.addAndGet(count);
    }

    public static void checkTimeBudget() {
        long elapsedSeconds = Duration.between(now(), startTime.get()).getSeconds();
        if (TIME_BUDGET_SECONDS > 0 && elapsedSeconds > TIME_BUDGET_SECONDS) {
            throw new RuntimeException("Execution time budget exceeded: " + elapsedSeconds + "s > " + TIME_BUDGET_SECONDS + "s");
        }
    }

    public static void checkTokenBudget() {
        int current = getAccumulatedTotalTokens();
        if (TOKEN_BUDGET > 0 && current > TOKEN_BUDGET) {
            throw new RuntimeException("Token budget exceeded: " + current + " > " + TOKEN_BUDGET);
        }
    }

    public static void checkToolCallBudget() {
        int current = toolCallUsage.get();
        if (TOOL_CALLS_BUDGET > 0 && current > TOOL_CALLS_BUDGET) {
            throw new RuntimeException("Tool call budget exceeded: " + current + " > " + TOOL_CALLS_BUDGET);
        }
    }

    public static void checkAllBudgets() {
        BudgetManager.checkTimeBudget();
        BudgetManager.checkTokenBudget();
        BudgetManager.checkToolCallBudget();
    }
}
