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
package org.tarik.ta.model;

import org.tarik.ta.dto.TestStepResult;
import org.tarik.ta.helper_entities.TestCase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the context and state of the current test execution.
 */
public class TestExecutionContext {
    private final TestCase testCase;
    private final List<TestStepResult> executionHistory;
    private final Map<String, Object> sharedData;
    private VisualState visualState;

    public TestExecutionContext(TestCase testCase, VisualState visualState) {
        this.testCase = testCase;
        this.executionHistory = new ArrayList<>();
        this.sharedData = new HashMap<>();
        this.visualState = visualState;
    }

    public synchronized TestCase getTestCase() {
        return testCase;
    }

    public synchronized List<TestStepResult> getExecutionHistory() {
        return executionHistory;
    }

    public synchronized Map<String, Object> getSharedData() {
        return sharedData;
    }

    public synchronized VisualState getVisualState() {
        return visualState;
    }

    public synchronized void setVisualState(VisualState visualState) {
        this.visualState = visualState;
    }

    public synchronized void addStepResult(TestStepResult result) {
        this.executionHistory.add(result);
    }
}
