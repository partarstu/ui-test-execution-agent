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
import org.tarik.ta.dto.VerificationExecutionResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

public class VerificationManager {
    private static final Logger LOG = LoggerFactory.getLogger(VerificationManager.class);
    private final AtomicReference<CompletableFuture<VerificationExecutionResult>> currentVerification = new AtomicReference<>();

    public void setVerificationFuture(CompletableFuture<VerificationExecutionResult> future) {
        currentVerification.set(future);
    }

    public VerificationExecutionResult waitForVerificationToFinish() {
        CompletableFuture<VerificationExecutionResult> future = currentVerification.get();
        if (future == null) {
            LOG.info("No active verification to wait for.");
            return new VerificationExecutionResult(true, "No verification was running.");
        }

        try {
            LOG.info("Waiting for verification to complete...");
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new VerificationExecutionResult(false, "Verification interrupted: " + e.getMessage());
        } catch (ExecutionException e) {
            return new VerificationExecutionResult(false, "Verification execution failed: " + e.getCause().getMessage());
        }
    }
    
    public CompletableFuture<VerificationExecutionResult> getVerificationFuture() {
        return currentVerification.get();
    }
}
