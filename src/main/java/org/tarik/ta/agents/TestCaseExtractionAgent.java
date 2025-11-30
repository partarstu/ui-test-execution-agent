package org.tarik.ta.agents;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.tarik.ta.AgentConfig;
import org.tarik.ta.error.RetryPolicy;
import org.tarik.ta.helper_entities.TestCase;

public interface TestCaseExtractionAgent extends BaseAiAgent<TestCase> {
    RetryPolicy RETRY_POLICY = AgentConfig.getActionRetryPolicy();

    @SystemMessage(fromResource = "prompt_templates/system/test_case_extraction_prompt.txt")
    @UserMessage("{{user_request}}")
    Result<TestCase> extractTestCase(@V("user_request") String userRequest);

    @Override
    default String getAgentTaskDescription() {
        return "Extracting test case from user request";
    }

    @Override
    default RetryPolicy getRetryPolicy() {
        return RETRY_POLICY;
    }
}
