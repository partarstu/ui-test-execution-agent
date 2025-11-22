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
package org.tarik.ta.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.tarik.ta.AgentConfig;
import org.tarik.ta.agents.ToolVerificationAgent;
import org.tarik.ta.dto.VerificationStatus;
import org.tarik.ta.manager.VerificationManager;
import org.tarik.ta.exceptions.ToolExecutionException;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.awt.Desktop.getDesktop;
import static java.awt.Desktop.isDesktopSupported;
import static org.tarik.ta.error.ErrorCategory.TRANSIENT_TOOL_ERROR;
import static org.tarik.ta.error.ErrorCategory.UNKNOWN;
import static org.tarik.ta.utils.CommonUtils.*;

public class CommonTools extends AbstractTools {
    private static final int BROWSER_OPEN_TIME_SECONDS = 1;
    private static final Logger LOG = LoggerFactory.getLogger(CommonTools.class);
    private static final String HTTP_PROTOCOL = "http://";
    private static final String OS_NAME_SYS_PROPERTY = "os.name";
    private static final String HTTPS_PROTOCOL = "https://";
    private static Process browserProcess;
    private static final Object LOCK = new Object();

    private final VerificationManager verificationManager;

    public CommonTools() {
        super();
        this.verificationManager = new VerificationManager();
    }

    protected CommonTools(ToolVerificationAgent toolVerificationAgent, VerificationManager verificationManager) {
        super(toolVerificationAgent);
        this.verificationManager = verificationManager;
    }

    public CommonTools(VerificationManager verificationManager) {
        super();
        this.verificationManager = verificationManager;
    }

    @Tool(value = "Waits for any running verifications to complete and returns the verification results, if any.")
    public VerificationStatus waitForVerification() {
        try {
            return verificationManager.waitForVerification(AgentConfig.getVerificationRetryTimeoutMillis() / 1000);
        } catch (Exception e) {
            throw new ToolExecutionException("Failed to wait for verification: " + e.getMessage(), UNKNOWN);
        }
    }

    @Tool(value = "Waits the specified amount of seconds. Use this tool when you need to wait after some action.")
    public void waitSeconds(@P(value = "The specific amount of seconds to wait.") int secondsAmount) {
        try {
            sleepSeconds(secondsAmount);
        } catch (Exception e) {
            throw new ToolExecutionException("Failed to wait for " + secondsAmount + " seconds: " + e.getMessage(), UNKNOWN);
        }
    }

    @Tool(value = "Opens the default browser with the specified URL. Use this tool to navigate to a web page.")
    public void openBrowser(@P(value = "The URL to open in the browser.") String url) {
        synchronized (LOCK) {
            if (isBlank(url)) {
                throw new ToolExecutionException("URL must be provided", TRANSIENT_TOOL_ERROR);
            }

            String sanitizedUrl = url;
            if (!sanitizedUrl.toLowerCase().startsWith(HTTP_PROTOCOL) && !sanitizedUrl.toLowerCase().startsWith(HTTPS_PROTOCOL)) {
                LOG.warn("Provided URL '{}' doesn't have the protocol defined, using HTTP as the default one", sanitizedUrl);
                sanitizedUrl = HTTP_PROTOCOL + sanitizedUrl;
            }

            URL finalUrl;
            try {
                finalUrl = URI.create(sanitizedUrl).toURL();
            } catch (MalformedURLException e) {
                throw new ToolExecutionException("Invalid URL format: " + e.getMessage(), TRANSIENT_TOOL_ERROR);
            }

            try {
                closeBrowser(); // Close any existing browser instance

                if (isDesktopSupported() && getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    getDesktop().browse(finalUrl.toURI());
                } else {
                    LOG.debug("Java AWT Desktop is not supported on the current OS, falling back to alternative method.");
                    String os = System.getProperty(OS_NAME_SYS_PROPERTY).toLowerCase();
                    String[] command = buildBrowserStartupCommand(os, finalUrl.toString());
                    LOG.debug("Executing command: {}", String.join(" ", command));
                    browserProcess = new ProcessBuilder(command).start();
                    if (!browserProcess.isAlive()) {
                        var errorMessage = "Failed to open browser. Error: %s\n"
                                .formatted(IOUtils.toString(browserProcess.getErrorStream(), UTF_8));
                        throw new ToolExecutionException(errorMessage, TRANSIENT_TOOL_ERROR);
                    }
                }
                sleepSeconds(BROWSER_OPEN_TIME_SECONDS);
            } catch (Exception e) {
                throw rethrowAsToolException(e, "opening browser");
            }
        }
    }

    @Tool(value = "Closes the currently open browser instance. Use this tool when you need to close the browser.")
    public void closeBrowser() {
        synchronized (LOCK) {
            try {
                if (browserProcess != null && browserProcess.isAlive()) {
                    browserProcess.destroy();
                    try {
                        browserProcess.waitFor(); // Wait for the process to terminate
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new ToolExecutionException("Interrupted while waiting for browser to close", UNKNOWN);
                    }
                }
            } catch (Exception e) {
                throw rethrowAsToolException(e, "closing browser");
            }
        }
    }

    private static String[] buildBrowserStartupCommand(String os, String url) {
        if (os.contains("win")) {
            return new String[]{"cmd.exe", "/c", "start", url};
        } else if (os.contains("mac")) {
            return new String[]{"open", url};
        } else {
            String browserCommand = System.getenv("BROWSER_COMMAND");
            if (browserCommand == null || browserCommand.trim().isEmpty()) {
                browserCommand = "chromium-browser";
            }
            return new String[]{browserCommand, "--no-sandbox", "--start-maximized", url};
        }
    }
}