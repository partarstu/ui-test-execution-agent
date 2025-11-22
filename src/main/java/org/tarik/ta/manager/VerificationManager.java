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
import org.tarik.ta.dto.VerificationStatus;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class VerificationManager {
    private static final Logger LOG = LoggerFactory.getLogger(VerificationManager.class);
    private final Lock lock = new ReentrantLock();
    private final Condition verificationFinished = lock.newCondition();

    private boolean isRunning = false;
    private boolean lastSuccess = true;
    private String lastMessage = "";

    public void registerRunningVerification() {
        lock.lock();
        try {
            this.isRunning = true;
            this.lastSuccess = false;
            this.lastMessage = "Verification in progress";
            LOG.info("Verification registered as running.");
        } finally {
            lock.unlock();
        }
    }

    public void registerVerificationResult(boolean success, String message) {
        lock.lock();
        try {
            this.isRunning = false;
            this.lastSuccess = success;
            this.lastMessage = message;
            LOG.info("Verification finished. Success: {}, Message: {}", success, message);
            verificationFinished.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public VerificationStatus waitForVerification(long timeoutSeconds) {
        lock.lock();
        try {
            if (!isRunning) {
                LOG.debug("No verification running, returning immediately.");
                return new VerificationStatus(false, lastSuccess,
                        lastMessage.isEmpty() ? "No verification was running" : lastMessage);
            }

            LOG.info("Waiting for verification to finish (timeout: {} s)...", timeoutSeconds);
            boolean finished = verificationFinished.await(timeoutSeconds, TimeUnit.SECONDS);

            if (!finished) {
                return new VerificationStatus(true, false, "Timed out waiting for verification to finish.");
            }

            return new VerificationStatus(false, lastSuccess, lastMessage);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new VerificationStatus(false, false, "Interrupted while waiting for verification.");
        } finally {
            lock.unlock();
        }
    }
}