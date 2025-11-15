# Detailed Implementation Plan for Agentic Architecture Migration

## Overview

This document outlines the detailed implementation plan for migrating the current procedural test automation architecture to an agentic
architecture using LangChain4j's agent framework. The transformation involves converting hardcoded workflows into autonomous sub-agents that
make decisions based on execution state.

## 1. Core Architectural Components

### 1.1 Main Orchestrator Agent (Agent.java)

**Purpose**: Transform the current `Agent.java` into a main orchestrator/controller agent that coordinates test case execution through
delegation to sub-agents.

**Key Changes**:

- Retain the test case execution orchestration logic at the top level
- Maintain the test execution context initialization and state management
- Replace the direct procedural workflow with delegation to `TestExecutionAgent`
- Remove all direct tool invocation logic (this will move to sub-agents)
- The orchestrator will iterate through preconditions and test steps, delegating each to the `TestExecutionAgent`
- Maintain the retry logic and timeout handling at the orchestration level
- Keep screen recording functionality at this level
- Preserve error handling and test result aggregation logic

### 1.2 TestExecutionAgent (New Sub-Agent)

**Purpose**: A new sub-agent responsible for executing individual preconditions and test steps.

**Key Responsibilities**:

- Execute a single precondition or test step action having access to the current execution context
- Verify precondition fulfillment or test step expected results
- Coordinate with ElementLocatorAgent for UI element location, if any precondition or test step action presume using specific UI element
  (inside the corresponding tools)
- Coordinate with UserInteractionAgent for user dialog interactions, if the whole system runs in attended mode (inside the corresponding
  tools)
- Make autonomous decisions on which tools to invoke based on the current task and execution context
- Return structured `ToolExecutionResult` instances to the orchestrator

**Required Tools**:

- Tool for executing preconditions (original logic already present in `Agent.java`)
- Tool for verifying precondition fulfillment (original logic already present in `Agent.java`)
- Tool for executing test step actions (original logic already present in `Agent.java`)
- Tool for verifying test step expected results (original logic already present in `Agent.java`)

**Decision-Making Capability**:

- Analyze the precondition or test step and determine which sequence of tools to invoke
- Handle failures and determine if retry is appropriate
- Decide when to request element location from ElementLocatorAgent
- Decide when to interact with the user via UserInteractionAgent

**Return Type**:

- Returns an `ToolExecutionResult` record containing:
    - Success/failure status
    - Execution message/details
    - Screenshot if applicable
    - Any structured result data needed for subsequent steps

### 1.3 Per-Agent Execution Policies and Capability Scopes

- Execution policies per agent type:
    - Max tool calls per delegated task (configurable, defaults per agent role)
    - Max depth of delegation (to prevent unbounded agent fan-out)
    - Circuit breakers on repeated failures (trip after N consecutive errors of the same type; configurable cool-off)
- Capability scopes:
    - Explicit tool allowlists per agent (TestExecutionAgent, ElementLocatorAgent, UserInteractionAgent)
    - Parameter limits and side-effect constraints (e.g., bounded mouse movement distance, rate limits on clicks/keys)
    - Sensitive operations require explicit elevation or human confirmation in attended mode
- Cancellation and budget awareness:
    - Agents observe parent cancellation and budget limits; must stop spawning new work when limits are hit
    - Agents surface partial context/results when cancelled

---

## 2. Tool System Refactoring

### 2.1 ToolExecutionResult Enhancement

**Current State**: `ToolExecutionResult` is a record with a generic type parameter but doesn't fully utilize it for structured results.

**Required Changes**:

- Make `ToolExecutionResult<T>` a truly generic record where `T` represents the specific execution result type
- Ensure the `resultPayload` field of type `T` can carry strongly-typed execution results
- Add JSON schema annotations (similar to DTOs in `org.tarik.ta.dto`) to the generic type parameter when used
- Update all factory methods in `AbstractTools` to properly support generic types
- Ensure serialization/deserialization works correctly for passing results between agents

### 2.2 Tool Classes Refactoring

**Affected Classes**: All classes in `org.tarik.ta.tools` package

**Required Changes**:

- Ensure all tools can be properly registered with the LangChain4j agent framework
- Update tool descriptions to be more agent-friendly (clear about inputs, outputs, and when to use them)

---

## 3. ElementLocatorAgent (Sub-Agent Conversion)

### 3.4 Data Governance for Vector Store and Embeddings

- Log embedding model/provider and semantic version for every ingested document and query
- Maintain migration scripts and backfill procedures when embeddings change (model or parameters)
- Store embedding metadata (model ID, dimensions, tokenizer) alongside vectors for reproducibility
- Provide a dual-index strategy during migrations with progressive re-embed and switch-over
- Establish retention and deletion policies aligned with privacy requirements

### 3.1 Conversion from ElementLocatorTools to Agent

**Current State**: `ElementLocatorTools` is a static tool class with the `locateElementOnTheScreen` method.

**Required Changes**:

- Convert `ElementLocatorTools` into `ElementLocatorAgent` - a LangChain4j sub-agent
- The agent will be responsible for finding UI elements and returning their coordinates
- Main functionality: locate UI element center point coordinates and bounding box

**Return Type - New Record**:

- Create a new Java record for the return result (e.g., `ElementLocationResult`)
- Contains:
    - Center point X coordinate (int)
    - Center point Y coordinate (int)
    - Original bounding box as Rectangle object
    - Success/failure status
    - Any additional metadata about the location process
- This record should have appropriate JSON schema annotations for agent communication

### 3.2 Agent Tools for ElementLocatorAgent

**Required Tools**:

- Primary tool: `locateElement` - finds an element based on description and test data
- Tool for retrieving elements from vector database
- Tool for visual grounding (identifying bounding boxes using vision models)
- Tool for algorithmic matching (template matching, feature matching)
- Tool for validating and selecting best match from multiple candidates
- Tool for handling zoom-in scenarios for small elements
- Each tool should be independently invocable by the agent based on its decision-making

**Decision-Making Capability**:

- Decide which location strategy to use (vision-based, algorithmic, or hybrid)
- Determine when zoom-in is needed for small elements
- Choose between multiple candidate matches
- Decide when to invoke UserInteractionAgent for user assistance
- Handle cases where element is not found and determine next steps

### 3.3 Integration with UserInteractionAgent

**Required Changes**:

- Remove all direct user dialog invocations from ElementLocatorAgent
- Replace with requests to UserInteractionAgent sub-agent
- ElementLocatorAgent makes decisions on when user input is needed
- Communication happens through agent-to-agent interaction, not direct method calls

---

## 4. UserInteractionAgent (New Sub-Agent)

### 4.1 Purpose and Responsibilities

**Purpose**: A specialized sub-agent responsible for all user dialog interactions.

**Responsibilities**:

- Create and display user dialogs based on requests from other agents
- Collect user responses and return structured results
- Handle all dialog lifecycle management
- Make decisions on which type of dialog to show based on the situation

### 4.2 Agent Tools - User Dialog Operations

**Required Tools** (each corresponding to a user dialog type):

**Tool: PromptUserToCreateNewElement**

- Opens dialogs for capturing new UI element information
- Coordinates: `BoundingBoxCaptureNeededPopup`, `UiElementScreenshotCaptureWindow`, `ElementDescriptionPrompt`, `UiElementInfoPopup`
- Returns structured result with the created element information

**Tool: PromptUserToRefineExistingElements**

- Opens `UiElementRefinementPopup` for updating or deleting elements
- Accepts list of candidate elements
- Returns updated elements or deletion confirmations

**Tool: ConfirmLocatedElement**

- Opens `LocatedElementConfirmationDialog`
- Shows screenshot with bounding box overlay
- Returns user choice (CORRECT, INCORRECT, INTERRUPTED)

**Tool: PromptUserForNoElementFound**

- Opens `NoElementFoundPopup`
- Handles cases where element cannot be found
- Returns user decision (CONTINUE, TERMINATE)

**Tool: PromptUserForNextAction**

- Opens `NextActionPopup`
- Presents options for next steps
- Returns user choice (RETRY_SEARCH, CREATE_NEW_ELEMENT, TERMINATE)

**Tool: DisplayInformationalPopup**

- Shows information popups like `NewElementInfoNeededPopup`, `TargetElementToGetLocated`
- Non-blocking informational displays

**Decision-Making Capability**:

- Determine which dialog is most appropriate for the current situation
- Handle dialog sequencing (e.g., multiple dialogs in a workflow)
- Manage unattended mode where dialogs should be skipped
- Handle user interruptions and termination requests gracefully

### 4.3 Dialog Result Records

**Required New Records**:

- Create strongly-typed result records for each dialog type
- Each record should have JSON schema annotations
- Records encapsulate all user choices and input data
- Enable clear communication between UserInteractionAgent and requesting agents

---

## 5. MouseTools and KeyboardTools Refactoring

### 5.1 MouseTools Changes

**Current State**: Methods accept element descriptions or complex parameters

**Required Changes**:

- Refactor all mouse action methods to accept simple `int` coordinates (x, y) for the element's center point
- Remove any logic that tries to locate elements
- Methods become purely mechanical - perform action at specified coordinates
- Update method signatures:
    - `rightMouseClick(int x, int y)` - already correct
    - `leftMouseClick(int x, int y)` - already correct
    - `leftMouseDoubleClick(int x, int y)` - already correct
    - `moveMouseTo(int x, int y)` - already correct
    - `clickAndDrag(int xOffset, int yOffset)` - already correct
    - `clickElementUntilStateAchieved(int x, int y, String expectedStateDescription)` - already correct

**Impact on Agent Workflow**:

- Any sub-agent needing to perform mouse actions must first invoke ElementLocatorAgent
- Get the coordinates from ElementLocationResult
- Pass coordinates to MouseTools methods
- This creates a clear separation of concerns and explicit agent coordination

### 5.2 KeyboardTools Changes

**Current State**: Some methods like `typeText` accept coordinates and element descriptions

**Required Changes**:

- Update `typeText` method signature to accept only coordinates (int x, int y) for where to click before typing
- Remove element description parameters
- Focus purely on keyboard actions
- Update method signatures:
    - `pressKey(String keyboardKey)` - no change needed
    - `pressKeys(String... keyboardKeys)` - no change needed
    - `typeText(String text, int x, int y, String wipeOutOldContent)` - already accepts coordinates
    - `clearData(int x, int y)` - already accepts coordinates

**Impact on Agent Workflow**:

- Sub-agents using keyboard tools must first obtain coordinates from ElementLocatorAgent
- Clear separation between "where" (element location) and "what" (keyboard action)

---

## 6. Agent Communication and Workflow

### 6.1 Main Orchestrator → TestExecutionAgent Flow

**Workflow**:

1. Main orchestrator receives test case with preconditions and test steps
2. For each precondition:
    - Delegate to TestExecutionAgent with precondition details
    - TestExecutionAgent executes and returns ToolExecutionResult
    - Orchestrator evaluates result and decides whether to continue
3. For each test step:
    - Delegate to TestExecutionAgent with step details
    - TestExecutionAgent executes action and verification
    - Returns ToolExecutionResult with success/failure and any verification results
    - Orchestrator aggregates results and determines overall test status

### 6.2 TestExecutionAgent → ElementLocatorAgent Flow

**Workflow**:

1. TestExecutionAgent analyzes action requiring UI interaction
2. Determines that element location is needed
3. Invokes ElementLocatorAgent with element description and test data
4. ElementLocatorAgent performs location logic (may involve UserInteractionAgent)
5. Returns ElementLocationResult with coordinates and bounding box
6. TestExecutionAgent extracts coordinates and passes to MouseTools/KeyboardTools

### 6.3 ElementLocatorAgent → UserInteractionAgent Flow

**Workflow**:

1. ElementLocatorAgent encounters situation requiring user input
2. Determines which type of user interaction is needed
3. Invokes appropriate UserInteractionAgent tool
4. UserInteractionAgent creates and displays dialog
5. Collects user response and returns structured result
6. ElementLocatorAgent processes response and continues execution

### 6.4 Agent Decision Points

**Each agent must make autonomous decisions**:

**Main Orchestrator**:

- Continue or abort test execution based on precondition results
- Whether to proceed to next step after failure
- Overall test status determination

**TestExecutionAgent**:

- Which tools to invoke for a given action
- When to request element location
- Whether verification is needed
- Retry logic for failed actions

**ElementLocatorAgent**:

- Which location strategy to employ
- When to request user assistance
- How to handle multiple candidate matches
- Whether zoom-in is needed

**UserInteractionAgent**:

- Which dialog type is appropriate
- Dialog sequencing in complex workflows
- Handling unattended mode gracefully

### 6.5 Cancellation Propagation and Cooperative Interrupts

- Parent agents propagate cancellation tokens to all children; children periodically check and stop gracefully
- User interruptions (attended mode) map to cooperative interrupts that unwind the agent tree with informative partial results
- Timeouts and budget exhaustion also trigger cancellation propagation
- Ensure clean-up hooks for each agent to release resources (screen recorder, file handles, temp artifacts)

### 6.6 Lightweight Planner for TestExecutionAgent

- Introduce a plan-then-act loop:
    1) Plan: produce a short structured action plan for the step (tools, expected observations, stop conditions)
    2) Act: execute tools following the plan, updating state and checking for cancellations
    3) Reflect: evaluate outcomes, decide retry/adjustments or termination
- Planner outputs are logged for transparency and debuggability and attached to the trace
- Planner respects per-agent execution policies (max tool calls, max delegation depth, budgets)

---

## 7. Data Transfer Objects and Records

### 7.1 New Records to Create

**ElementLocationResult Record**:

- Already exists as `ElementLocation` DTO but may need enhancement
- Current fields are sufficient:
    - Center X coordinate (int)
    - Center Y coordinate (int)
    - Bounding box (BoundingBox)
- Ensure proper JSON schema annotations

**UserDialogResult Records** (one for each dialog type):

- `NewElementCreationResult` - for new element creation workflow
- `ElementRefinementResult` - for element refinement operations
- `LocationConfirmationResult` - for location confirmation
- `NoElementFoundResult` - for no element found scenario
- `NextActionResult` - for next action selection
- Each with appropriate fields and JSON schema annotations

### 7.2 Existing Records to Enhance

**ToolExecutionResult Record**:

- Used by TestExecutionAgent to return results to orchestrator
- Fields:
    - Success status (boolean)
    - Execution message (String)
    - Screenshot (BufferedImage, nullable)
    - Structured result data (generic type)
    - Timestamp information
- JSON schema annotations for agent communication

**TestStepExecutionPlan**:

- May need additional fields to support agent-based execution
- Possible additions:
    - Context information for agents
    - Dependency information between steps
    - Execution metadata

**ActionExecutionResult**:

- Currently simple record with success, message, screenshot
- May need to be made generic or extended for richer result data
- Consider adding execution timing information

---

## 8. Tool Registration and Configuration

### 8.1 Tool Registration with LangChain4j

**Requirements**:

- All tool classes must be properly registered with the LangChain4j framework
- Use `@Tool` annotation on methods (already present)
- Ensure tool specifications are generated correctly
- Register tools with appropriate agents

**Implementation Steps**:

1. Create agent instances using LangChain4j's agent builder
2. Register appropriate tool classes with each agent
3. Configure agent with appropriate model (instruction model, vision model, etc.)
4. Set up proper error handling and retry logic at agent level
5. Configure agent execution parameters (timeouts, max iterations, etc.)

### 8.2 Agent Configuration

**Main Orchestrator Agent**:

- Registered sub-agents: TestExecutionAgent
- Tools: None directly (delegates to sub-agents)
- Model: Instruction model for decision-making

**TestExecutionAgent**:

- Registered sub-agents: ElementLocatorAgent, UserInteractionAgent
- Tools: All tools from MouseTools, KeyboardTools, CommonTools packages
- Additional tools for precondition/verification logic
- Model: Instruction model for action planning

**ElementLocatorAgent**:

- Registered sub-agents: UserInteractionAgent
- Tools:
    - Element retrieval from vector DB
    - Visual grounding tools
    - Algorithmic matching tools
    - Validation tools
- Model: GUI grounding model for visual understanding

**UserInteractionAgent**:

- Registered sub-agents: None (leaf agent)
- Tools: All user dialog tools
- Model: Instruction model for dialog management

---

## 9. Error Handling and Recovery

### 9.4 Error Taxonomy and Retry Policy

- Categories:
    - USER_INTERRUPTION: no retry; severity INFO; propagate cancellation
    - VALIDATION_FAILED: optional limited retry if non-deterministic; severity WARN
    - TRANSIENT_TOOL_ERROR: exponential backoff retry; severity WARN/ERROR based on frequency
    - NON_RETRYABLE_ERROR: no retry; severity ERROR
    - SAFETY_BLOCKED: no retry; severity WARN; surface reason
    - TIMEOUT: bounded retries if budget allows; severity WARN
- Each category maps to a default retry policy and logging level; configurable per agent
- Circuit breakers trip on repeated failures for the same category and input signature

### 9.5 Global Timeouts and Budget Guards

- Define per-step and per-test global timeouts and budgets (tokens, tool calls, wall-clock)
- Cancellation propagates to all child agents when budget is exhausted or timeout reached
- Agents report partial results and context on timeout/cancellation

### 9.1 Agent-Level Error Handling

**Requirements**:

- Each agent must handle errors within its domain
- Errors should be converted to structured results, not exceptions where possible
- Agents should decide autonomously whether retry makes sense
- Clear error propagation through agent hierarchy

**Implementation Considerations**:

- Wrap tool execution in try-catch blocks within agents
- Convert exceptions to ToolExecutionResult with ERROR status
- Include error details in result messages
- Preserve screenshots on errors for debugging

### 9.2 User Interruption Handling

**Current State**: Uses custom exceptions (`UserChoseTerminationException`, `UserInterruptedExecutionException`)

**Required Changes**:

- Ensure user interruptions are properly propagated through agent chain
- Each agent must handle interruption gracefully
- Clean up resources when user terminates
- Return appropriate status to parent agent

### 9.3 Retry Logic

**Current State**: Retry logic hardcoded in `processToolExecutionRequest` method

**Required Changes**:

- Move retry logic into agent decision-making
- TestExecutionAgent decides when to retry based on tool results
- Configurable retry parameters
- Different retry strategies for different failure types

---

## 10. State Management and Context

### 10.3 Bounded Element Cache with TTL

- Implement a bounded LRU cache for element locations/bounding boxes with TTL
- Invalidation triggers: window resize, navigation/url change, theme or DPI changes, viewport scroll beyond threshold
- Cache entries include context keys (page signature, viewport, theme) to reduce false reuse
- Provide explicit invalidation hooks that agents can call after impactful tool actions

### 10.1 Test Execution Context

**Purpose**: Maintain state throughout test execution

**Required Context Information**:

- Current test case details
- Test step execution history
- Current screenshot
- Execution timestamps
- Tool execution results history
- Element location cache (optional optimization)

**Implementation**:

- Context object passed between orchestrator and TestExecutionAgent
- Each agent maintains its own internal state for its execution scope
- Context is updated after each step execution
- Context includes information needed for agent decision-making

### 10.2 Inter-Agent Communication State

**Requirements**:

- Clear interfaces for agent-to-agent communication
- Structured data exchange using records with JSON schema
- No shared mutable state between agents
- All communication through return values and parameters

---

## 11. Prompt Engineering for Agents

### 11.3 Prompt Versioning and Governance

- Use semantic versioning (e.g., v1.2.0) for all agent system prompts and templates
- Log the prompt version used in each run/trace and in outputs stored in artifacts
- Maintain changelogs per prompt; breaking changes increment MAJOR
- Prompt files reside under resources/prompt_templates/system/{agent}/ and include version identifiers in-file

### 11.1 Agent System Prompts

**Required for Each Agent**:

- Clear instructions on agent's role and responsibilities
- Decision-making guidelines
- Tool usage instructions
- Error handling guidelines
- Examples of proper tool sequencing

**Main Orchestrator Agent Prompt**:

- Role: Test execution coordinator
- Responsibilities: Delegate to TestExecutionAgent, aggregate results, determine overall success
- Decision points: When to stop execution, handling precondition failures

**TestExecutionAgent Prompt**:

- Role: Execute individual test actions and verifications
- Responsibilities: Choose appropriate tools, coordinate with ElementLocatorAgent, perform verifications
- Decision points: Which tools to invoke, when to request element location, retry logic

**ElementLocatorAgent Prompt**:

- Role: Locate UI elements on screen
- Responsibilities: Use multiple strategies to find elements, coordinate with UserInteractionAgent when needed
- Decision points: Location strategy selection, handling multiple matches, when to ask user

**UserInteractionAgent Prompt**:

- Role: Manage user interactions through dialogs
- Responsibilities: Display appropriate dialogs, collect responses, handle unattended mode
- Decision points: Dialog type selection, dialog sequencing

### 11.2 Prompt Template Updates

**Existing Prompts to Adapt**:

- `ActionExecutionPlanPrompt` - may need to be split or adapted for agent-based execution
- `VerificationExecutionPrompt` - integrate into TestExecutionAgent
- `PreconditionVerificationPrompt` - integrate into TestExecutionAgent
- Other prompts in the prompts package - evaluate for agent integration

---

## 12. Testing Strategy for Agentic Architecture

### 12.1 Unit Testing

**Focus Areas**:

- Individual agent behavior testing
- Tool execution result serialization/deserialization
- Record creation and JSON schema generation
- Agent decision-making logic (mock tool results)

### 12.2 Integration Testing

**Focus Areas**:

- Agent-to-agent communication
- Tool invocation from agents
- Error propagation through agent hierarchy
- Context passing and state management

### 12.3 End-to-End Testing

**Focus Areas**:

- Complete test case execution through agent system
- Multiple preconditions and steps
- Various failure scenarios
- User interaction flows (with mocked dialogs in automated tests)

---

## 13. Migration Strategy

### 13.1 Phased Approach

This migration will proceed in three strictly ordered groups: (A) Additive (create only), (B) Modifying (switch-over and refactors), and (C) Decommissioning (removals). Each group completes before the next begins.

A) Additive – create new code without breaking existing flows

- Create new package structure for agents (e.g., org.tarik.ta.agents) and agent scaffolding
- Create/extend records and DTOs required by agents (ExecutionResult/ToolExecutionResult<T> generic usage, ElementLocationResult, all UserDialogResult records) with JSON annotations
- Add system prompts and templates for each agent under resources/prompt_templates/system/{agent}/ with version headers
- Implement UserInteractionAgent and all dialog tools as a new, standalone capability (not yet wired into old flows)
- Implement ElementLocatorAgent with its internal tools (retrieval, visual grounding, algorithmic matching, validation); expose a parallel API while keeping ElementLocatorTools intact
- Implement TestExecutionAgent skeleton with plan-then-act loop; support returning ToolExecutionResult<T>; keep unused in production path
- Register new agents and tools in configuration behind a feature flag (agentic.execution.enabled=false by default)
- Add unit tests for new records, tools, and agents in isolation; add basic integration tests for agent-to-agent calls

B) Modifying – switch callers to new capabilities and refactor existing code

- Mouse/Keyboard switch-over
  - Introduce coordinate-only overloads where needed; migrate callers to use ElementLocatorAgent for coordinates
  - Refactor MouseTools and KeyboardTools to coordinate-only usage; keep temporary overloads for backward compatibility until all callers migrate
- Element location and dialogs
  - Update calling code to route all element location requests through ElementLocatorAgent
  - Replace direct dialog invocations with calls to UserInteractionAgent
- TestExecution integration
  - Complete TestExecutionAgent implementation (execution + verification) and wire it for preconditions and steps
  - Move retry logic and error taxonomy handling into agents
- Orchestrator migration
  - Refactor Agent.java into an orchestrator delegating to TestExecutionAgent; keep feature-flag toggle to revert to legacy path
- Prompts and configuration
  - Point runtime to new per-agent prompts; record prompt versions in logs/traces
- Testing and stabilization
  - Expand integration and E2E tests to cover the agentic path; run side-by-side with legacy using the flag

C) Decommissioning – remove unused code and finalize

- Remove legacy hardcoded workflow logic from Agent.java
- Remove element location logic from any tool classes; keep only pure mechanics in MouseTools/KeyboardTools
- Remove direct user dialog calls from non-agent code; delete obsolete dialog wiring
- Delete deprecated prompts/templates superseded by per-agent prompts
- Remove temporary method overloads and any compatibility shims
- Clean configuration: remove legacy feature flags and unused settings; finalize agent defaults
- Final test pass and documentation updates (architecture, developer, and operational docs)

Acceptance gates per group

- A) All new agents compile, unit tests for DTOs/tools/agents pass, feature flag off keeps legacy path fully green
- B) All existing tests pass using the agentic path with the feature flag on; parity achieved for representative scenarios
- C) No references to removed code remain; CI green; documentation and runbooks updated

### 13.2 Backward Compatibility Considerations

**During Migration**:

- Consider maintaining both old and new implementations temporarily
- Use feature flags to switch between procedural and agentic execution
- Gradual migration of test cases
- Comprehensive logging to compare behaviors

---

## 14. Configuration and Tuning

### 14.1 Agent Configuration Parameters

- Budgets per agent/test (tokens, tool calls, time); enforce via middleware
- Max delegation depth and max tool calls per task per agent
- Global and per-tool timeouts and retry strategies aligned with error taxonomy

**New Configuration Needed**:

- Agent model selection per agent type
- Agent timeout and max iterations
- Tool execution timeouts per agent
- Retry configurations per agent
- Logging levels per agent

**Existing Configuration to Review**:

- All parameters in `AgentConfig` class
- Ensure compatibility with agent-based execution
- Add agent-specific configuration options

### 14.2 Prompt Configuration

- Introduce prompt versioning with semantic tags; record prompt version in traces and logs
- Maintain per-agent prompt directories under resources/prompt_templates/system/{agent}/ with version headers

**Externalize Prompts**:

- Create system prompt templates for each agent
- Store in resources folder similar to existing prompts
- Allow runtime customization
- Version control for prompt changes

---

## 15. Documentation and Knowledge Transfer

### 15.1 Architecture Documentation

**Required Documentation**:

- Agent hierarchy diagram
- Agent communication flow diagrams
- Tool-to-agent mapping
- Decision-making flowcharts per agent
- Error handling and recovery flows

### 15.2 Developer Documentation

**Required Documentation**:

- How to create new sub-agents
- How to add tools to existing agents
- Agent testing guidelines
- Debugging agent behavior
- Prompt engineering guidelines

### 15.3 Operational Documentation

**Required Documentation**:

- Configuration guide for agent system
- Monitoring and logging
- Troubleshooting common issues
- Performance tuning guide

---

## 16. Implementation Checklist

### 16.1 Code Structure

- [ ] Create new package structure for agents (e.g., `org.tarik.ta.agents`)
- [ ] Create ExecutionResult record with generic support
- [ ] Create ElementLocationResult record
- [ ] Create all UserDialogResult records
- [ ] Enhance ToolExecutionResult for proper generic usage
- [ ] Add JSON schema annotations to all result records

### 16.2 Agent Implementation

- [ ] Implement UserInteractionAgent
- [ ] Implement ElementLocatorAgent
- [ ] Implement TestExecutionAgent
- [ ] Refactor main Agent.java to orchestrator
- [ ] Configure all agents with appropriate models and tools

### 16.3 Tool Refactoring

- [ ] Update all tool methods to return proper ToolExecutionResult<T>
- [ ] Refactor MouseTools to use only coordinates
- [ ] Refactor KeyboardTools to use only coordinates
- [ ] Extract user dialog logic from ElementLocatorTools
- [ ] Create tools for all user interactions

### 16.4 Integration

- [ ] Integrate UserInteractionAgent with ElementLocatorAgent
- [ ] Integrate ElementLocatorAgent with TestExecutionAgent
- [ ] Integrate TestExecutionAgent with main orchestrator
- [ ] Implement context passing between agents
- [ ] Implement error propagation

### 16.5 Testing

- [ ] Unit tests for all new agents
- [ ] Unit tests for all new records
- [ ] Integration tests for agent communication
- [ ] End-to-end tests with complete test cases
- [ ] Performance testing and optimization

### 16.6 Documentation

- [ ] Update README with new architecture
- [ ] Create agent architecture documentation
- [ ] Document decision-making logic per agent
- [ ] Create troubleshooting guide
- [ ] Update developer onboarding documentation

---

## 17. Success Criteria

### 17.1 Functional Requirements

- All existing test cases execute successfully through agent system
- Agent-based execution produces same results as procedural execution
- User interaction flows work correctly through UserInteractionAgent
- Element location works through ElementLocatorAgent
- All tools properly invoked by agents with correct parameters

### 17.2 Non-Functional Requirements

- Performance within 20% of current implementation
- Clear separation of concerns between agents
- Maintainable and extensible agent structure
- Comprehensive error handling and recovery
- Proper logging and debugging capabilities

### 17.3 Architecture Quality

- No hardcoded workflows in agent implementations
- Autonomous decision-making by each agent
- Clear communication protocols between agents
- Proper abstraction and encapsulation
- Following LangChain4j best practices

---

## 18. Risks and Mitigation

### 18.1 Technical Risks

**Risk**: Agent decision-making may be unpredictable

- Mitigation: Comprehensive testing, clear prompts, logging of all decisions

**Risk**: Performance degradation due to agent overhead

- Mitigation: Benchmarking, caching strategies, optimization of agent communication

**Risk**: Difficulty debugging agent behavior

- Mitigation: Extensive logging, agent execution tracing, reproducible test scenarios

**Risk**: LangChain4j framework limitations

- Mitigation: Prototype early, identify workarounds, contribute to framework if needed

### 18.2 Migration Risks

**Risk**: Breaking existing functionality

- Mitigation: Phased migration, parallel execution, comprehensive regression testing

**Risk**: Incomplete migration leaving inconsistent codebase

- Mitigation: Clear migration plan, tracking of all components, code review process

---

## 19. Observability and Tracing

### 19.1 Tracing Layer

- Generate unique trace/run IDs per test; create spans per agent invocation and per tool call
- Correlate spans with screenshots, prompts, and element bounding boxes
- Log model ID, prompt version, sanitized tool inputs/outputs, token usage, and latencies
- Provide structured logs and export to OpenTelemetry-compatible backends

### 19.2 Health Checks and Model Fallbacks

- Periodic health checks for primary models; switch to secondary provider/model on failures
- Record failover events in trace; ensure deterministic retries where possible

### 19.3 Dashboards and Cost Tracking

- Build dashboards for pass/fail, retries, error taxonomy distribution, and cost per test/agent
- Track budgets spent (tokens, tool calls, time) per agent and per test

---

## 20. Safety, Security, and Compliance

- Tool allowlisting per agent and parameter bounds
- Audit logs for all tool calls and sensitive operations; redact PII
- Retention and deletion policies for artifacts (screenshots, traces) with TTLs
- Privacy filter pipeline on logs and traces (mask secrets, redact sensitive UI content)

---

## 21. Performance and Cost Governance

- Budgets per test and per agent: max tokens, max tool invocations, wall-clock time
- Caching strategies (element cache, prompt cache) with size limits
- Model tiering and fallbacks based on budget and criticality
- Pre-flight estimates and early-abort when budget insufficient

---

## 22. Future Enhancements

### 22.1 Additional Agents

- **VerificationAgent**: Specialized agent for all verification logic
- **RetryStrategyAgent**: Intelligent retry decision-making based on failure patterns
- **TestDataAgent**: Manage and provide test data dynamically
- **ReportingAgent**: Generate reports and analytics

### 22.2 Advanced Features

- Learning from execution history to improve decision-making
- Dynamic tool creation based on application context
- Multi-modal interaction (voice, touch, etc.)
- Distributed agent execution for parallel testing