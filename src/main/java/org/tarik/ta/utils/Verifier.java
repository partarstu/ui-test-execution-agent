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

package org.tarik.ta.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.AgentConfig;
import org.tarik.ta.dto.VerificationExecutionResult;
import org.tarik.ta.model.GenAiModel;
import org.tarik.ta.prompts.VerificationExecutionPrompt;

import static java.time.Instant.now;
import static org.tarik.ta.model.ModelFactory.getVerificationVisionModel;
import static org.tarik.ta.utils.CommonUtils.*;
import static org.tarik.ta.utils.ImageUtils.saveImage;

public class Verifier {
    private static final Logger LOG = LoggerFactory.getLogger(Verifier.class);
    private static final int VERIFICATION_RETRY_TIMEOUT_MILLIS = AgentConfig.getVerificationRetryTimeoutMillis();
    private static final int VERIFICATION_RETRY_INTERVAL_MILLIS = AgentConfig.getTestStepExecutionRetryIntervalMillis();
    private static final boolean DEBUG_MODE = AgentConfig.isDebugMode();

    public static VerificationExecutionResult verify(String verificationInstruction, String actionInstruction, String testData) {
        var deadline = now().plusMillis(VERIFICATION_RETRY_TIMEOUT_MILLIS);
        VerificationExecutionResult verificationResult;
        do {
            verificationResult = verifyOnce(verificationInstruction, actionInstruction, testData);
            if (verificationResult.success()) {
                return verificationResult;
            }

            var nextRetryMoment = now().plusMillis(VERIFICATION_RETRY_INTERVAL_MILLIS);
            if (nextRetryMoment.isBefore(deadline)) {
                LOG.info("Verification failed, retrying within configured deadline.");
                waitUntil(nextRetryMoment);
            } else {
                LOG.info("Verification failed and all retries are exhausted.");
                return verificationResult;
            }
        } while (true);
    }

    public static VerificationExecutionResult verifyOnce(String verificationInstruction, String actionInstruction, String testData) {
        try (GenAiModel model = getVerificationVisionModel()) {
            var screenshot = captureScreen();
            if (DEBUG_MODE) {
                saveImage(screenshot, "verification");
            }
            var prompt = VerificationExecutionPrompt.builder()
                    .withVerificationDescription(verificationInstruction)
                    .withActionDescription(actionInstruction)
                    .withActionTestData(testData)
                    .screenshot(screenshot)
                    .build();
            VerificationExecutionResult result = model.generateAndGetResponseAsObject(prompt, "verification execution");
            LOG.info("Verification result: {}", result);
            return result;
        }
    }
}
