package org.tarik.ta.agents;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.tarik.ta.AgentConfig;
import org.tarik.ta.dto.UiElementDescriptionResult;
import org.tarik.ta.error.RetryPolicy;

public interface UiElementDescriptionAgent extends BaseAiAgent<UiElementDescriptionResult> {
    RetryPolicy RETRY_POLICY = AgentConfig.getActionRetryPolicy();

    @UserMessage("""
            The target UI element info: {{original_element_description}}.
            
            The screenshot is attached.
            """)
    Result<String> describeUiElement(
            @V("original_element_description") String originalElementDescription,
            @V("bounding_box_color") String boundingBoxColor,
            @UserMessage ImageContent screenshot);

    @Override
    default String getAgentTaskDescription() {
        return "Generating the description of selected UI element";
    }

    @Override
    default RetryPolicy getRetryPolicy() {
        return RETRY_POLICY;
    }
}
