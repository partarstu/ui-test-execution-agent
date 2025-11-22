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

import static java.util.concurrent.TimeUnit.SECONDS;

public class VerificationManager {
    private static final Logger LOG = LoggerFactory.getLogger(VerificationManager.class);
    private final Lock lock = new ReentrantLock();
    private final Condition verificationFinished = lock.newCondition();

    private boolean isRunning = false;
    private boolean lastSuccess = true;

    public void registerRunningVerification() {
        lock.lock();
        try {
            this.isRunning = true;
            this.lastSuccess = false;
            LOG.info("Verification registered as running.");
        } finally {
            lock.unlock();
        }
    }

    public void registerVerificationResult(boolean success) {
        lock.lock();
        try {
            this.isRunning = false;
            this.lastSuccess = success;
            LOG.debug("Verification finished. Success: {}", success);
            verificationFinished.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public VerificationStatus waitForVerificationToFinish(long timeoutSeconds) {
        lock.lock();
        try {
            if (!isRunning) {
                return new VerificationStatus(false, lastSuccess);
            }

            LOG.info("Waiting for verification to finish (timeout: {} s)...", timeoutSeconds);
            boolean finished = verificationFinished.await(timeoutSeconds, SECONDS);
            return finished ? new VerificationStatus(false, lastSuccess) : new VerificationStatus(true, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new VerificationStatus(false, false);
        } finally {
            lock.unlock();
        }
    }
}