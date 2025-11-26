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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class VerificationManager implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(VerificationManager.class);
    private final Lock lock = new ReentrantLock();
    private final Condition verificationFinished = lock.newCondition();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private int activeVerifications = 0;
    private boolean lastSuccess = true;

    public void submitVerification(Supplier<Boolean> verificationTask) {
        lock.lock();
        try {
            activeVerifications++;
            this.lastSuccess = false;
            LOG.info("Verification submitted. Active verifications: {}", activeVerifications);
        } finally {
            lock.unlock();
        }

        executor.submit(() -> {
            boolean success = false;
            try {
                success = verificationTask.get();
            } catch (Exception e) {
                LOG.error("Verification task failed unexpectedly", e);
            } finally {
                registerVerificationResult(success);
            }
        });
    }

    public VerificationStatus waitForVerificationToFinish(long timeoutMillis) {
        lock.lock();
        try {
            if (activeVerifications == 0) {
                return new VerificationStatus(false, lastSuccess);
            }

            LOG.info("Waiting for verification to finish (timeout: {} ms)...", timeoutMillis);
            long remainingNanos = MILLISECONDS.toNanos(timeoutMillis);
            while (activeVerifications > 0) {
                if (remainingNanos <= 0) {
                    return new VerificationStatus(true, false);
                }
                try {
                    remainingNanos = verificationFinished.awaitNanos(remainingNanos);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return new VerificationStatus(false, false);
                }
            }
            return new VerificationStatus(false, lastSuccess);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, SECONDS)) {
                LOG.warn("Executor did not terminate in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void registerVerificationResult(boolean success) {
        lock.lock();
        try {
            activeVerifications--;
            this.lastSuccess = success;
            LOG.debug("Verification finished. Success: {}. Active verifications: {}", success, activeVerifications);
            if (activeVerifications == 0) {
                verificationFinished.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }
}