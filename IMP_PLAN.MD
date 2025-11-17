# Detailed Implementation Plan for Agentic Architecture Migration

## Overview

This document outlines the detailed implementation plan for migrating the current procedural test automation architecture to an agentic
architecture using LangChain4j's agent framework. The transformation involves converting some hardcoded workflows into autonomous 
sub-agents that make decisions based on execution state.

## 1. Core Architectural Components

### 1.1 Main Orchestrator Agent (Agent.java)

**Purpose**: Transform the current `Agent.java` into a main orchestrator/controller agent that coordinates test case execution through
delegation to sub-agents.

**Key Changes**:

- Retain the test case execution orchestration logic at the top level
- Maintain the test execution context initialization and state management
- Replace the direct procedural workflow for executing preconditions and test steps, as well as verifications, each with delegation to 
  the corresponding sub-agent
- The orchestrator will iterate through preconditions and test steps, delegating each to the corresponding sub-agent
- Maintain the retry logic and timeout handling at the orchestration level
- Keep screen recording functionality at this level
- Preserve error handling and test result aggregation logic
- Handle failures and determine if retry is appropriate

### 1.2 New Sub-Agents for executing preconditions/test step actions, verifying preconditions and verifying test step expected results

**Purpose**: New sub-agents are responsible for executing corresponding tests related to test case execution. Use the documentation 
under this link to find out how to correctly create agents : https://docs.langchain4j.dev/tutorials/agents 

**Key Responsibilities**:

- Execute a single precondition or test step action having access to the current execution context
- Verify precondition fulfillment or test step expected results
- Use UserInteractionService for user dialog interactions, if the whole system runs in attended mode (inside the corresponding tools)
- Make autonomous decisions on which tools to invoke based on the current task and execution context
- Return structured `ToolExecutionResult` instances to the orchestrator

**Required Tools**:

- All tools located in `org.tarik.ta.tools` package

**Decision-Making Capability**:

- Analyze the precondition or test step and determine which sequence of tools to invoke
- Decide when to interact with the user via UserInteractionService

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
    - Explicit tool allowlists per agent (test case execution sub-agents, ElementLocatorTools, UserInteractionService)
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

- Ensure all tools can be properly registered with the LangChain4j agent framework. Use the documentation under following link for this 
  task: https://docs.langchain4j.dev/tutorials/tools
- Update tool descriptions to be more agent-friendly (clear about inputs, outputs, and when to use them)

---

## 3. ElementLocatorTools

**Current State**: `ElementLocatorTools` is a static tool class with the `locateElementOnTheScreen` method.

**Required Changes**:

- Remove all direct user dialog invocations from ElementLocatorTools - they are now the responsibility of the corresponding sub-agents 
  for test execution

---

## 4. UserInteractionService

### 4.1 Purpose and Responsibilities

**Purpose**: A specialized service responsible for all user dialog interactions.

**Responsibilities**:

- Create and display user dialogs based on requests from other agents
- Collect user responses and return structured results
- Implement user decisions (creating new element, updating or remove existing elements)
- Handle dialog sequencing (e.g., multiple dialogs in a workflow)
- Handle user interruptions and termination requests gracefully
- Handle all dialog lifecycle management
- Make decisions on which type of dialog to show based on the situation
- Log embedding model/provider and semantic version for every ingested document and query
- Maintain migration scripts and backfill procedures when embeddings change (model or parameters)
- Store embedding metadata (model ID, dimensions, tokenizer) alongside vectors for reproducibility

### 4.2 User Dialog Operations

**PromptUserToCreateNewElement**

- Opens dialogs for capturing new UI element information
- Coordinates: `BoundingBoxCaptureNeededPopup`, `UiElementScreenshotCaptureWindow`, `ElementDescriptionPrompt`, `UiElementInfoPopup`
- Returns structured result with the created element information

**PromptUserToRefineExistingElements**

- Opens `UiElementRefinementPopup` for updating or deleting elements
- Accepts list of candidate elements
- Returns updated elements or deletion confirmations

**ConfirmLocatedElement**

- Opens `LocatedElementConfirmationDialog`
- Shows screenshot with bounding box overlay
- Returns user choice (CORRECT, INCORRECT, INTERRUPTED)

**PromptUserForNoElementFound**

- Opens `NoElementFoundPopup`
- Handles cases where element cannot be found
- Returns user decision (CONTINUE, TERMINATE)

**PromptUserForNextAction**

- Opens `NextActionPopup`
- Presents options for next steps
- Returns user choice (RETRY_SEARCH, CREATE_NEW_ELEMENT, TERMINATE)

**DisplayInformationalPopup**

- Shows information popups like `NewElementInfoNeededPopup`, `TargetElementToGetLocated`
- Blocking informational displays
- Warning user in case the expected results or preconditions verification failed showing the actual screenshot, expected 
  state and the actual state including what is the reason of the failed verification 


### 4.3 Dialog Result Records

**Required New Records**:

- Create strongly-typed result records for each dialog type
- Each record should have JSON schema annotations
- Records encapsulate all user choices and input data
- Enable clear communication between UserInteractionService and requesting agents

---

## 5. MouseTools and KeyboardTools Refactoring

### 5.1 MouseTools Changes

**Impact on Agent Workflow**:

- Any sub-agent needing to perform actions with UI elements must first invoke ElementLocatorTools in order to find them
- Get the coordinates from ElementLocationResult
- Pass coordinates to other tools

### 5.2 KeyboardTools Changes

**Impact on Agent Workflow**:

- Sub-agents using keyboard tools must first obtain coordinates from ElementLocatorTools
- Clear separation between "where" (element location) and "what" (keyboard action)

---

## 6. Agent Communication and Workflow

### 6.1 Main Orchestrator → test execution sub-agents Flow

**Workflow**:

1. Main orchestrator receives test case with preconditions and test steps
2. For each precondition:
    - Delegate to the corresponding agent with precondition details
    - Agent executes precondition and returns ToolExecutionResult
    - Orchestrator evaluates result and decides whether to continue
    - If orchestrator continues, the precondition verification must happen - delegate it to the corresponding agent with precondition details
    - Agent verifies if precondition is fulfilled and returns ToolExecutionResult
    - Orchestrator evaluates result and decides whether to continue
3. For each test step:
    - Delegate to the corresponding agent with step action and data
    - Agent executes action and returns ToolExecutionResult 
    - Orchestrator evaluates result and decides whether to continue
    - If orchestrator continues, the test step expected results verification is delegated  to the corresponding agent with test step details
    - Agent verifies the test step expected results vs. actual state and returns ToolExecutionResult
    - Orchestrator aggregates results and determines overall test status


### 6.2 Agents → UserInteractionService Flow

**Workflow**:

1. If result of ElementLocatorTools execution encounters situation requiring user input, UserInteractionService needs to be invoked if 
   the system is working in attended mode
2. Determines which type of user interaction is needed
3. Invokes appropriate UserInteractionService tool
4. UserInteractionService creates and displays dialog and implements the user decisions
5. Collects all results and returns structured result

### 6.3 Cancellation Propagation and Cooperative Interrupts

- User interruptions (attended mode) map to cooperative interrupts that are propagated to the orchestrator
- Timeouts and budget exhaustion also trigger cancellation propagation
- Ensure clean-up hooks for each agent to release resources (screen recorder, file handles, temp artifacts)

### 6.4 Lightweight Planner for Orchestrator and agents

- Introduce a plan-then-act loop:
    1) Plan: produce a short structured action plan for the step (tools, expected observations, stop conditions)
    2) Act: execute tools following the plan, updating state and checking for cancellations
    3) Reflect: evaluate outcomes, decide retry/adjustments or termination
- Planner outputs are logged for transparency and debuggability and attached to the trace
- Planner respects per-agent execution policies (max tool calls, max delegation depth, budgets)

---

## 7. Data Transfer Objects and Records

### 7.1 New Records to Create

**UserDialogResult Records** (one for each dialog type):

- `NewElementCreationResult` - for new element creation workflow
- `ElementRefinementResult` - for element refinement operations
- `LocationConfirmationResult` - for location confirmation
- `NoElementFoundResult` - for no element found scenario
- `NextActionResult` - for next action selection
- Each with appropriate fields and JSON schema annotations

### 7.2 Existing Records to Enhance

**ToolExecutionResult Record**:

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

## 8. Error Handling and Recovery

### 8.1 Agent-Level Error Handling

**Requirements**:

- Each agent must handle errors within its domain
- Errors should be converted to structured results, not exceptions where possible
- Agents should decide autonomously whether retry makes sense
- Clear error propagation to the orchestrator

**Implementation Considerations**:

- Wrap tool execution in try-catch blocks within agents
- Convert exceptions to ToolExecutionResult with ERROR status
- Include error details in result messages
- Preserve screenshots on errors for debugging

### 8.2 User Interruption Handling

**Current State**: Uses custom exceptions (`UserChoseTerminationException`, `UserInterruptedExecutionException`)

**Required Changes**:

- Ensure user interruptions are properly propagated through agent chain to the orchestrator
- Each agent must handle interruption gracefully
- Clean up resources when user terminates
- Return appropriate status to the orchestrator

### 8.3 Retry Logic

**Current State**: Retry logic hardcoded in `processToolExecutionRequest` method

**Required Changes**:

- Move retry logic into agent decision-making
- Each agent decides when to retry based on tool results
- Configurable retry parameters
- Different retry strategies for different failure types

### 8.4 Error Taxonomy and Retry Policy

- Categories:
    - USER_INTERRUPTION: no retry; severity INFO; propagate cancellation
    - VERIFICATION_FAILED: optional limited retry if non-deterministic; severity WARN
    - TRANSIENT_TOOL_ERROR: exponential backoff retry; severity WARN/ERROR based on frequency
    - NON_RETRYABLE_ERROR: no retry; severity ERROR
    - SAFETY_BLOCKED: no retry; severity WARN; surface reason
    - TIMEOUT: bounded retries if budget allows; severity WARN
- Each category maps to a default retry policy and logging level; configurable per agent
- Circuit breakers trip on repeated failures for the same category and input signature

### 8.5 Global Timeouts and Budget Guards

- Define per-step and per-test global timeouts and budgets (tokens, tool calls, wall-clock)
- Cancellation propagates to all child agents when budget is exhausted or timeout reached
- Agents report partial results and context on timeout/cancellation

---

## 10. State Management and Context

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

- Context object passed between orchestrator and agents
- Each agent maintains its own internal state for its execution scope
- Context is updated after each step execution
- Context includes information needed for agent and orchestrator decision-making 

### 10.2 Inter-Agent Communication State

**Requirements**:

- No shared mutable state between agents
- All communication through return values and parameters

### 10.3 Bounded Element Cache with TTL

- Implement a bounded LRU cache for element locations/bounding boxes with TTL
- Invalidation triggers: window resize, navigation/url change, theme or DPI changes, viewport scroll beyond threshold
- Cache entries include context keys (page signature, viewport, theme) to reduce false reuse
- Provide explicit invalidation hooks that agents can call after impactful tool actions

---

## 11. Prompt Engineering for Agents

### 11.1 Agent System Prompts

**Required for Each Agent**:

- Clear instructions on agent's role and responsibilities
- Decision-making guidelines
- Tool usage instructions
- Error handling guidelines

### 11.2 Prompt Template Updates

**Existing Prompts to Adapt**:

- `ActionExecutionPlanPrompt` - may need to be split or adapted for agent-based execution
- `VerificationExecutionPrompt` - integrate into corresponding agent
- `PreconditionVerificationPrompt` - integrate into corresponding agent
- Other prompts in the prompts package - evaluate for agent integration

### 11.3 Prompt Versioning and Governance

- Use semantic versioning (e.g., v1.2.0) for all agent system prompts and templates
- Log the prompt version used in each run/trace and in outputs stored in artifacts
- Maintain changelogs per prompt; breaking changes increment MAJOR
- Prompt files reside under resources/prompt_templates/system/{agent}/ and include version identifiers in-file

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

## 16. Safety, Security, and Compliance

- Tool allowlisting per agent and parameter bounds
- Audit logs for all tool calls and sensitive operations; redact PII

---

## 17. Performance and Cost Governance

- Budgets per test and per agent: max tokens, max tool invocations, wall-clock time
- Caching strategies (element cache, prompt cache) with size limits
- Model tiering and fallbacks based on budget and criticality
- Pre-flight estimates and early-abort when budget insufficient

---
## 18. Implementation Checklist

The following checklist enumerates all phases of the migration in the exact order they must be implemented. Each item explicitly references the relevant sections of this plan. Each phase must pass its acceptance gate before proceeding to the next. Treat each phase as incrementally shippable and independently testable.

Legend:
- [ ] = TODO
- [x] = Done

Phase 0 — Foundations and Scaffolding
- [x] Confirm codebase compiles and tests run green as a baseline
  - Tasks:
    - [x] Ensure current tests (KeyboardToolsTest, MouseToolsTest, BoundingBoxUtilTest, CommonUtilsTest, AgentTest) pass locally
    - [x] Verify build with Maven (pom.xml) runs successfully and artifacts are generated
  - Acceptance gate:
    - [x] CI pipeline green on main branch

Phase 1 — Core Models and DTO Enhancements (Sections 2.1, 7.2)
- [x] Make ToolExecutionResult<T> truly generic
  - Tasks:
    - [x] Define ToolExecutionResult<T> with fields: success (boolean), message (String), screenshot (BufferedImage, nullable), payload (T), timestamp
    - [x] Add JSON schema annotations to support agent communication
    - [x] Update serialization/deserialization utilities if present; otherwise, document serialization approach
    - [x] Update all factory/utility methods in AbstractTools to produce typed results
  - Acceptance gate:
    - [x] Unit tests verifying typed payload round-trip serialize/deserialize
    - [x] All tool callers compile with generics enforced

- [x] Review and extend ActionExecutionResult (Section 7.2)
  - Tasks:
    - [x] Decide to genericize or wrap with richer metadata; ensure timing fields present
    - [x] Update usages where appropriate
  - Acceptance gate:
    - [x] Tests cover timing metadata and backward compatibility

Phase 2 — User Dialog Result Records (Sections 4.3, 7.1)
- [x] Introduce strongly-typed dialog result records with JSON schema annotations
  - Tasks:
    - [x] Create: NewElementCreationResult, ElementRefinementResult, LocationConfirmationResult, NoElementFoundResult, NextActionResult
    - [x] Define fields per dialog type (choices, inputs, confirmations, metadata)
    - [x] Provide mappers to/from UI dialog components where necessary
  - Acceptance gate:
    - [x] Unit tests validate construction and JSON schema annotations exist on fields

Phase 3 — UserInteractionService API Definition (Section 4.1, 4.2)
- [x] Define UserInteractionService interface and tool wrappers
  - Tasks:
    - [x] Specify operations: PromptUserToCreateNewElement, PromptUserToRefineExistingElements, ConfirmLocatedElement, PromptUserForNoElementFound, PromptUserForNextAction, DisplayInformationalPopup
    - [x] Implement mapping of UI components: BoundingBoxCaptureNeededPopup, UiElementScreenshotCaptureWindow, UiElementRefinementPopup, LocatedElementConfirmationDialog, NoElementFoundPopup, NextActionPopup, NewElementInfoNeededPopup, TargetElementToGetLocated
    - [x] Ensure decisions that mutate elements (create/update/delete) are implemented inside this service
    - [x] Add interruption handling and lifecycle management hooks
  - Acceptance gate:
    - [x] Manual attended-mode flow demo for each dialog, returning typed results
    - [x] Unit tests for service methods with mock UI components

Phase 4 — Tool System Refactoring (Sections 2.2, 3, 5)
- [x] Update tool classes to be agent-friendly and LangChain4j-compliant
  - Tasks:
    - [x] Register tools with LangChain4j per docs; add clear descriptions (inputs/outputs/usage)
    - [x] Remove direct dialog invocations from ElementLocatorTools;
    - [x] Ensure MouseTools and KeyboardTools consume coordinates derived from ElementLocatorTools results
    - [x] Add parameter bounds and timeouts at tool boundary as needed (prelude to policies)
  - Acceptance gate:
    - [x] Static analysis: no tool class imports UI dialogs directly
    - [x] Tests simulate locate-then-act pipeline (ElementLocatorTools -> Mouse/KeyboardTools)

Phase 5 — Orchestrator Agent and Execution Policies (Sections 1.1, 1.3)
- [ ] Transform Agent.java into orchestrator/controller
  - Tasks:
    - [ ] Keep context initialization, retry/timeout at orchestration level
    - [ ] Iterate preconditions and test steps; delegate to sub-agents for action and verification
    - [ ] Maintain screen recording and result aggregation
    - [ ] Implement per-agent execution policies: max tool calls, max delegation depth, circuit breakers, sensitive-op confirmations
    - [ ] Implement cancellation/budget awareness
  
Phase 6 — Sub-Agents for Action and Verification (Sections 1.2, 6.1)
- [ ] Implement sub-agents for: precondition action, precondition verification, test step action, expected results verification
  - Tasks:
    - [ ] Provide decision logic for selecting tools and calling UserInteractionService when in attended mode
    - [ ] Return ToolExecutionResult<T> with structured payloads
    - [ ] Enforce capability scopes (tool allowlists, parameter limits)
  - Acceptance gate:
    - [ ] Sub-agents pass unit tests for typical and edge scenarios
    - [ ] Manual attended-mode demo: a precondition + verification and a test step + verification

Phase 7 — Agent Communication and Flows (Sections 6.1, 6.2, 6.3)
- [ ] Wire orchestrator → sub-agents and sub-agents → UserInteractionService
  - Tasks:
    - [ ] Implement flow 6.1 (delegation for preconditions and steps, including verifications)
    - [ ] Implement flow 6.2 (agent to UserInteractionService with typed results)
    - [ ] Ensure cancellation/interrupt propagation works end-to-end
  - Acceptance gate:
    - [ ] E2E test: user interruption leads to cooperative cancel and clean resource release

Phase 8 — Lightweight Planner (Section 6.4)
- [ ] Add plan-then-act-then-reflect loop
  - Tasks:
    - [ ] Planner produces short structured plan: tools, expected observations, stop conditions
    - [ ] Respect per-agent execution policies
    - [ ] Log/trace planner outputs for debuggability
  - Acceptance gate:
    - [ ] Logs show plan/act/reflect cycles and adjustments under failures

Phase 9 — Context and State Management (Sections 10.1, 10.2, 10.3)
- [ ] Implement Test Execution Context and bounded element cache
  - Tasks:
    - [ ] Define context object with: test case, execution history, current screenshot, timestamps, results history
    - [ ] No shared mutable state; pass via parameters/return values
    - [ ] Implement LRU cache with TTL for element locations/bounding boxes; invalidation on resize/nav/scroll/theme/DPI
    - [ ] Expose explicit invalidation hooks after impactful actions
  - Acceptance gate:
    - [ ] Unit tests for cache correctness and TTL/invalidation behavior

Phase 10 — Prompt Engineering (Sections 11.1, 11.2, 11.3, 14.2)
- [ ] Author agent system prompts and templates with versioning
  - Tasks:
    - [ ] Create per-agent system prompts under resources/prompt_templates/system/{agent}/ with semantic version headers
    - [ ] Update existing prompts (ActionExecutionPlanPrompt, VerificationExecutionPrompt, PreconditionVerificationPrompt) or split as needed
    - [ ] Log prompt version in traces and outputs; maintain changelogs
  - Acceptance gate:
    - [ ] Prompt versions are surfaced in logs and attached to test artifacts

Phase 11 — Error Handling, Retry, and Timeouts (Sections 8.1–8.5)
- [ ] Implement error taxonomy and default retry policies
  - Tasks:
    - [ ] Map USER_INTERRUPTION, VERIFICATION_FAILED, TRANSIENT_TOOL_ERROR, NON_RETRYABLE_ERROR, SAFETY_BLOCKED, TIMEOUT to retry strategies and log levels
    - [ ] Convert exceptions to structured ToolExecutionResult with details and screenshots when possible
    - [ ] Implement global timeouts and budget guards per step/test; propagate cancellations
    - [ ] Add circuit breakers on repeated failures per signature
  - Acceptance gate:
    - [ ] Tests simulate each error class and verify correct policy and logging

Phase 12 — Configuration and Tuning (Section 14.1)
- [ ] Extend AgentConfig and related configuration
  - Tasks:
    - [ ] Add budgets per agent/test (tokens, tool calls, time)
    - [ ] Add per-tool timeouts/retries and per-agent logging levels
    - [ ] Support model selection per agent type and max iterations
  - Acceptance gate:
    - [ ] Configurable via properties; defaults sane; config-driven tests pass

Phase 13 — Safety, Security, and Compliance (Section 16)
- [ ] Enforce tool allowlists and parameter bounds per agent
  - Tasks:
    - [ ] Implement policy layer or guards around tool invocation
    - [ ] Add audit logs for tool calls; redact PII where applicable
  - Acceptance gate:
    - [ ] Security review checklist passes; logs include required metadata

Phase 14 — Performance and Cost Governance (Section 17)
- [ ] Introduce budgets, caching strategies, and model tiering
  - Tasks:
    - [ ] Pre-flight estimates and early-abort when budget insufficient
    - [ ] Size-limited caches and prompt cache if applicable
    - [ ] Model tiering/fallback strategies according to criticality
  - Acceptance gate:
    - [ ] Load tests show bounded token/tool usage; early-abort verified under constrained budgets

Phase 15 — Documentation (Section 15)
- [ ] Architecture and developer documentation
  - Tasks:
    - [ ] Agent hierarchy and communication diagrams
    - [ ] Decision-making flowcharts and error handling/recovery flows
    - [ ] How-tos: creating sub-agents, adding tools, testing, debugging, prompt engineering
    - [ ] Operational docs: configuration, monitoring/logging, troubleshooting, performance tuning
  - Acceptance gate:
    - [ ] Docs reviewed and published; quickstart runbook validated by a new developer

Phase 16 — Test Suite Expansion and E2E Scenarios
- [ ] Expand tests to cover agentic flows end-to-end
  - Tasks:
    - [ ] Unit tests for each agent and tool
    - [ ] Integration tests for orchestrator + sub-agents + UserInteractionService
    - [ ] E2E tests for common scenarios including interruptions and retries
  - Acceptance gate:
    - [ ] CI green with increased coverage; flaky tests triaged

Phase 17 — Rollout Plan and Feature Flags
- [ ] Staged enablement of agentic mode
  - Tasks:
    - [ ] Add feature flag to toggle agentic execution vs. legacy flow
    - [ ] Canary runs on selected test cases; monitor and compare outcomes
    - [ ] Gradual ramp-up and final deprecation of legacy paths when parity achieved
  - Acceptance gate:
    - [ ] Metrics confirm parity or improvement; feature flag default flips to agentic

Phase 18 — Post-Migration Cleanups
- [ ] Remove deprecated code paths and TODOs
  - Tasks:
    - [ ] Eliminate legacy dialog calls remaining in tools
    - [ ] Remove unused fields/parameters and outdated configs
    - [ ] Final code health pass: formatting, warnings, dead code
  - Acceptance gate:
    - [ ] Static analysis and style checks pass; repository free of deprecated paths

Tracking and Governance
- [ ] Create a migration board with phases as epics; each task becomes an issue
- [ ] Define DOR/DOD per phase; link acceptance gates to CI checks
- [ ] Record prompt versions and agent configurations used per CI run for traceability