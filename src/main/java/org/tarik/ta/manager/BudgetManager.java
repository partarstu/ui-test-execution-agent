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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class BudgetManager {
    private static final Logger LOG = LoggerFactory.getLogger(BudgetManager.class);
    private static final AtomicInteger tokenUsage = new AtomicInteger(0);
    private static final AtomicInteger toolCallUsage = new AtomicInteger(0);
    private static final AtomicLong startTime = new AtomicLong(System.currentTimeMillis());

    public static void reset() {
        tokenUsage.set(0);
        toolCallUsage.set(0);
        startTime.set(System.currentTimeMillis());
        LOG.info("Budget counters reset.");
    }

    public static void consumeTokens(int tokens) {
        int current = tokenUsage.addAndGet(tokens);
        int limit = AgentConfig.getAgentTokenBudget();
        if (limit > 0 && current > limit) {
             throw new RuntimeException("Token budget exceeded: " + current + " > " + limit);
        }
    }

    public static void consumeToolCalls(int count) {
        int current = toolCallUsage.addAndGet(count);
        int limit = AgentConfig.getAgentToolCallsBudget();
        if (limit > 0 && current > limit) {
             throw new RuntimeException("Tool call budget exceeded: " + current + " > " + limit);
        }
    }

    public static void checkTimeBudget() {
        long elapsedSeconds = (System.currentTimeMillis() - startTime.get()) / 1000;
        int limit = AgentConfig.getAgentExecutionTimeBudgetSeconds();
         if (limit > 0 && elapsedSeconds > limit) {
             throw new RuntimeException("Execution time budget exceeded: " + elapsedSeconds + "s > " + limit + "s");
        }
    }
}
