package org.tarik.ta.agents;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import org.tarik.ta.AgentConfig;
import org.tarik.ta.dto.BoundingBoxes;
import org.tarik.ta.error.RetryPolicy;

public interface ElementBoundingBoxAgent extends BaseAiAgent<BoundingBoxes> {
    RetryPolicy RETRY_POLICY = AgentConfig.getActionRetryPolicy();

    @SystemMessage(fromResource = "prompt_templates/system/element_bounding_box_prompt.txt")
    Result<BoundingBoxes> identifyBoundingBoxes(@UserMessage String prompt, @UserMessage ImageContent screenshot);

    @Override
    default String getAgentTaskDescription() {
        return "Identifying bounding boxes for the UI element";
    }

    @Override
    default RetryPolicy getRetryPolicy() {
        return RETRY_POLICY;
    }
}
