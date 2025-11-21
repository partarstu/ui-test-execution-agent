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
package org.tarik.ta.agents;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.tarik.ta.dto.VerificationExecutionResult;

import java.awt.image.BufferedImage;

/**
 * Agent responsible for verifying test step expected results for UI tests.
 * Uses LangChain4j's high-level AiServices API.
 */
public interface TestStepVerificationAgent extends BaseAiAgent {
        @SystemMessage(fromResource = "/prompt_templates/system/agents/test_step/verifyer/verification_execution_prompt.txt")
        @UserMessage("""
                        Verify that {{verificationDescription}}.

                        The test case action executed before this verification: {{actionDescription}}.
                        The test data for this action was: {{actionTestData}}

                        Shared data: {{sharedData}}

                        The screenshot of the application under test follows.
                        {{screenshot}}
                        """)
        VerificationExecutionResult verify(@V("verificationDescription") String verificationDescription,
                        @V("actionDescription") String actionDescription,
                        @V("actionTestData") String actionTestData,
                        @V("sharedData") String sharedData,
                        @V("screenshot") BufferedImage screenshot);
}
