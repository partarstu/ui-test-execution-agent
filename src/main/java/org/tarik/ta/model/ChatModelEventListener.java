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

import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.manager.BudgetManager;

import java.util.Map;

import static java.util.Optional.ofNullable;
import static org.tarik.ta.manager.BudgetManager.*;
import static org.tarik.ta.manager.BudgetManager.getAccumulatedTotalTokens;
import static org.tarik.ta.utils.CommonUtils.isNotBlank;

public class ChatModelEventListener implements ChatModelListener {
    private static final Logger log = LoggerFactory.getLogger(ChatModelEventListener.class);
    private static final String MESSAGE_SEPARATOR = "---------------------------------------------------------------------";

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        var chatResponse = responseContext.chatResponse();
        var aiMessage = chatResponse.aiMessage();
        if (isNotBlank(aiMessage.text())) {
            logWithSeparator("Received model text response", aiMessage.text());
        }
        if (isNotBlank(aiMessage.thinking())) {
            logWithSeparator("Received model thoughts", aiMessage.thinking());
        }
        if (!aiMessage.toolExecutionRequests().isEmpty()) {
            BudgetManager.consumeToolCalls(aiMessage.toolExecutionRequests().size());
            logWithSeparator("Received tool execution requests", aiMessage.toolExecutionRequests().toString());
        }

        ChatResponseMetadata metadata = chatResponse.metadata();
        if (metadata != null) {
            var metadataInfo = "Model response meta: model name = '%s'".formatted(metadata.modelName());
            TokenUsage tokenUsage = metadata.tokenUsage();
            if (tokenUsage != null) {
                int input = ofNullable(tokenUsage.inputTokenCount()).orElse(0);
                int output = ofNullable(tokenUsage.outputTokenCount()).orElse(0);
                int total = ofNullable(tokenUsage.totalTokenCount()).orElse(0);
                int cached = 0;
                String modelName = metadata.modelName() != null ? metadata.modelName() : "Unknown";
                consumeTokens(modelName, input, output, cached);
                metadataInfo = ("%s, input tokens = %d, output tokens = %d, total tokens = %d. " +
                        "Accumulated: input = %d, output = %d, cached = %d, total = %d")
                        .formatted(metadataInfo, input, output, total,
                                getAccumulatedInputTokens(modelName), getAccumulatedOutputTokens(modelName),
                                getAccumulatedCachedTokens(modelName), getAccumulatedTotalTokens(modelName));
            }
            log.debug(metadataInfo);
        }
    }

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        var chatRequest = requestContext.chatRequest();
        var messages = chatRequest.messages();
        if (messages.size() > 2) {
            // The system and user messages have already been through, i.e. already logged - let's log the latest message
            logMessage(messages.getLast());
        } else {
            // That's the first request to the model, we log both user and system messages
            messages.forEach(ChatModelEventListener::logMessage);
        }
    }

    private static void logWithSeparator(String typeOfMessage, String content) {
        log.debug("{}:\n{}\n{}\n{}", typeOfMessage, MESSAGE_SEPARATOR, content, MESSAGE_SEPARATOR);
    }

    private static void logMessage(ChatMessage chatMessage) {
        switch (chatMessage) {
            case SystemMessage systemMessage -> logWithSeparator("Sending a System Message", systemMessage.text());
            case UserMessage userMessage -> logUserMessage(userMessage);
            case ToolExecutionResultMessage toolResult ->
                    logWithSeparator("Sending results of '%s' tool execution".formatted(toolResult.toolName()), toolResult.text());
            case CustomMessage customMessage -> logWithSeparator("Sending custom message", customMessage.toString());
            default -> {
                // Not logging other message types
            }
        }
    }

    private static void logUserMessage(UserMessage userMessage) {
        userMessage.contents().forEach(content -> {
            if (content instanceof TextContent textContent) {
                logWithSeparator("Sending a User Message with Text", textContent.text());
            } else {
                log.debug("Sending a User Message with <{}> content type", content.type());
            }
        });
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        Throwable error = errorContext.error();
        log.error("Error: ", error);
        Map<Object, Object> attributes = errorContext.attributes();
        log.info("Attributes on Error: {}", attributes.get("my-attribute"));
    }
}