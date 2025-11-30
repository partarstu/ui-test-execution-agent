package org.tarik.ta.agents;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import org.tarik.ta.AgentConfig;
import org.tarik.ta.dto.PageDescriptionResult;
import org.tarik.ta.error.RetryPolicy;

public interface PageDescriptionAgent extends BaseAiAgent<PageDescriptionResult> {
    RetryPolicy RETRY_POLICY = AgentConfig.getActionRetryPolicy();

    @SystemMessage(fromResource = "prompt_templates/system/page_description_prompt.txt")
    @UserMessage("Screenshot is attached.")
    Result<PageDescriptionResult> describePage(@UserMessage ImageContent screenshot);

    @Override
    default String getAgentTaskDescription() {
        return "Generating the description of the page";
    }

    @Override
    default RetryPolicy getRetryPolicy() {
        return RETRY_POLICY;
    }
}
