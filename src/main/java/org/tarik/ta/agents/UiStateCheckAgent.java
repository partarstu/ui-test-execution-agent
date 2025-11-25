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
package org.tarik.ta.agents;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.tarik.ta.dto.VerificationExecutionResult;

public interface UiStateCheckAgent extends BaseAiAgent {
    @SystemMessage(fromResource = "/prompt_templates/system/agents/tool/verifier/v1.0.0/tool_verification_prompt.txt")
    @UserMessage("""             
            The expected state of the screen: {{expectedStateDescription}}
            
            The action performed was: {{actionDescription}}
            
            Any related to the expected state data: {{relevantData}}
            
            Screenshot attached.
            """)
    VerificationExecutionResult verify(
            @V("expectedStateDescription") String expectedStateDescription,
            @V("actionDescription") String actionDescription,
            @V("relevantData") String relevantData,
            @UserMessage ImageContent screenshot);
}
