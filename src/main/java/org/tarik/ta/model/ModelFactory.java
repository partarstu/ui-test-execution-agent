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

import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.googleai.GeminiMode;
import dev.langchain4j.model.googleai.GeminiThinkingConfig;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.vertexai.gemini.VertexAiGeminiChatModel;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.vertexai.anthropic.VertexAiAnthropicChatModel;

import java.util.List;
import static org.tarik.ta.AgentConfig.*;

public class ModelFactory {
    private static final int MAX_RETRIES = getMaxRetries();
    private static final int MAX_OUTPUT_TOKENS = getMaxOutputTokens();
    private static final double TEMPERATURE = getTemperature();
    private static final double TOP_P = getTopP();
    private static final boolean LOG_MODEL_OUTPUTS = isModelLoggingEnabled();
    private static final boolean OUTPUT_THOUGHTS = isThinkingOutputEnabled();
    private static final int GEMINI_THINKING_BUDGET = getGeminiThinkingBudget();

    public static GenAiModel getModel(String modelName, ModelProvider modelProvider) {
        return switch (modelProvider) {
            case GOOGLE -> new GenAiModel(getGeminiModel(modelName));
            case OPENAI -> new GenAiModel(getOpenAiModel(modelName));
            case GROQ -> new GenAiModel(getGroqModel(modelName));
            case ANTHROPIC -> new GenAiModel(getAnthropicModel(modelName));
        };
    }

    private static ChatModel getGeminiModel(String modelName) {
        var provider = getGoogleApiProvider();
        return switch (provider) {
            case STUDIO_AI -> GoogleAiGeminiChatModel.builder()
                    .apiKey(getGoogleApiToken())
                    .modelName(modelName)
                    .maxRetries(MAX_RETRIES)
                    .maxOutputTokens(MAX_OUTPUT_TOKENS)
                    .temperature(TEMPERATURE)
                    .topP(TOP_P)
                    .toolConfig(GeminiMode.ANY )
                    .logRequestsAndResponses(LOG_MODEL_OUTPUTS)
                    .thinkingConfig(GeminiThinkingConfig.builder()
                            .includeThoughts(OUTPUT_THOUGHTS)
                            .thinkingBudget(GEMINI_THINKING_BUDGET)
                            .build())
                    .returnThinking(OUTPUT_THOUGHTS)
                    .listeners(List.of(new ChatModelEventListener()))
                    .build();

            case VERTEX_AI -> VertexAiGeminiChatModel.builder()
                    .project(getGoogleProject())
                    .location(getGoogleLocation())
                    .modelName(modelName)
                    .maxRetries(MAX_RETRIES)
                    .maxOutputTokens(MAX_OUTPUT_TOKENS)
                    .temperature((float) TEMPERATURE)
                    .topP((float) TOP_P)
                    .logResponses(LOG_MODEL_OUTPUTS)
                    .listeners(List.of(new ChatModelEventListener()))
                    .build();
        };
    }

    private static ChatModel getOpenAiModel(String modelName) {
        return AzureOpenAiChatModel.builder()
                .maxRetries(MAX_RETRIES)
                .apiKey(getOpenAiApiKey())
                .deploymentName(modelName)
                .maxTokens(MAX_OUTPUT_TOKENS)
                .endpoint(getOpenAiEndpoint())
                .temperature(TEMPERATURE)
                .topP(TOP_P)
                .listeners(List.of(new ChatModelEventListener()))
                .build();
    }

    private static ChatModel getGroqModel(String modelName) {
        return OpenAiChatModel.builder()
                .baseUrl(getGroqEndpoint())
                .modelName(modelName)
                .maxRetries(MAX_RETRIES)
                .apiKey(getGroqApiKey())
                .maxTokens(MAX_OUTPUT_TOKENS)
                .temperature(TEMPERATURE)
                .topP(TOP_P)
                .listeners(List.of(new ChatModelEventListener()))
                .build();
    }

    private static ChatModel getAnthropicModel(String modelName) {
        var provider = getAnthropicApiProvider();
        return switch (provider) {
            case ANTHROPIC_API -> {
                String apiKey = getAnthropicApiKey();
                if (apiKey.isBlank()) {
                    throw new IllegalArgumentException("Anthropic API Key is missing for ANTHROPIC_API provider");
                }
                yield AnthropicChatModel.builder()
                        .baseUrl(getAnthropicEndpoint())
                        .thinkingType("disabled")
                        .returnThinking(false)
                        .sendThinking(false)
                        .apiKey(apiKey)
                        .modelName(modelName)
                        .maxRetries(MAX_RETRIES)
                        .maxTokens(MAX_OUTPUT_TOKENS)
                        .temperature(TEMPERATURE)
                        .toolChoice(ToolChoice.REQUIRED)
                        //.topP(TOP_P)
                        .listeners(List.of(new ChatModelEventListener()))
                        .build();
            }
            case VERTEX_AI -> VertexAiAnthropicChatModel.builder()
                    .project(getGoogleProject())
                    .location(getGoogleLocation())
                    .modelName(modelName)
                    .maxTokens(MAX_OUTPUT_TOKENS)
                    .temperature(TEMPERATURE)
                    .topP(TOP_P)
                    .logResponses(LOG_MODEL_OUTPUTS)
                    .listeners(List.of(new ChatModelEventListener()))
                    .build();
        };
    }
}