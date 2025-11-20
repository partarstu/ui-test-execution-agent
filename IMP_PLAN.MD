# Detailed Implementation Plan for Agentic Architecture Migration

## Overview

This document outlines the detailed implementation plan for migrating the current procedural test automation architecture to an agentic architecture using LangChain4j's agent framework. The transformation involves converting hardcoded workflows into autonomous sub-agents that execute strict test steps but make autonomous decisions regarding tools, retries, and error handling.

## 1. Core Architectural Components

### 1.1 Main Orchestrator Agent (Agent.java)

**Purpose**: Transform the current `Agent.java` into a main orchestrator/controller agent that coordinates test case execution through delegation to sub-agents.

**Key Responsibilities**:
- **Strict Sequential Execution**: Execute test steps exactly as defined in the `TestCase`. No dynamic re-ordering or skipping of steps.
- **Context Management**: Initialize and maintain the `TestExecutionContext`.
- **Delegation**: Delegate specific tasks (Precondition Action, Verification, Test Step Action) to specialized sub-agents.
- **Prefetching Strategy**: Implement a "Prefetching" mechanism. While a read-only verification agent is running for Step N, the Orchestrator can asynchronously trigger the `ElementLocatorTools` for Step N+1 to "warm up" or find the element, reducing perceived latency.
- **Result Aggregation**: Collect results and determine overall test status.

### 1.2 New Sub-Agents

**Purpose**: Specialized agents for distinct phases of execution.

*   **Precondition Action Agent**: Executes setup actions.
*   **Precondition Verification Agent**: Verifies setup success.
*   **Test Step Action Agent**: Executes the primary action (Click, Type, etc.).
*   **Test Step Verification Agent**: Verifies the expected result of the step.

**Key Responsibilities**:
- **Autonomous Execution**: Decide *how* to execute a step (e.g., which tool to use, how to handle a popup), but not *what* step to execute.
- **Self-Correction**: If a tool fails (e.g., "Click" misses), retry with a different strategy (e.g., "Javascript Click") before reporting failure.
- **User Interaction**: Invoke `UserInteractionService` when attended execution requires input.

### 1.3 Per-Agent Execution Policies
- **Capability Scopes**: Explicit tool allowlists per agent.
- **Fast-Path Logic**: Agents should prefer faster tools (e.g., Algorithmic Matching) over heavy AI tools (Vision Model) when confidence is high.

---

## 2. Tool System Refactoring

### 2.1 ToolExecutionResult Enhancement
- **Generic Results**: Make `ToolExecutionResult<T>` truly generic to carry structured data (e.g., `ElementLocation`, `ValidationResult`).
- **JSON Schema**: Add annotations to support LLM understanding of result structures.

### 2.2 Tool Classes Refactoring
- **LangChain4j Compliance**: Ensure all tools are properly annotated and described.
- **Decoupling**: Remove direct UI dialog calls from tools; delegate to `UserInteractionService`.

---

## 3. ElementLocatorTools & Circuit Breakers

**Current State**: `ElementLocatorTools` is computationally expensive and a potential single point of failure.

**Required Changes**:
- **Circuit Breaker Pattern**: Implement **Failsafe** (or Resilience4j) circuit breakers around the Vision Model calls.
    - *Policy*: If the Vision Model times out or fails X times consecutively, open the circuit and fallback to **Algorithmic Matching Only** (ORB/Template) or fail fast to save time.
    - *Reasoning*: Prevents the agent from wasting minutes on a vision model that is unresponsive or hallucinating.
- **Optimization**: Refactor to allow "Hint-based" location (using previous location data if available).

---

## 4. UserInteractionService

**Purpose**: Centralized service for all user dialogs (Attended/Semi-attended mode).

**Responsibilities**:
- **Dialog Management**: Handle `NewElementCreation`, `Refinement`, `Confirmation`, etc.
- **Semi-Attended Flow**: Allow user interruption *between* steps or during long-running tool executions.
- **Ambiguity Resolution**: (Future) Allow agents to ask users for help if multiple elements match.

---

## 5. MouseTools and KeyboardTools Refactoring

- **Coordinate Dependency**: These tools must accept coordinates/elements explicitly, decoupling them from the "finding" logic.
- **Input Validation**: Ensure coordinates are within screen bounds.

---

## 6. Agent Communication and Workflow

### 6.1 Orchestrator → Sub-Agents Flow (Sequential with Prefetching)

1.  **Orchestrator** starts Step N.
2.  **Action Agent** executes Step N Action.
3.  **Orchestrator** receives success.
4.  **Verification Agent** starts verifying Step N (Read-Only).
5.  **Prefetching (Async)**: Orchestrator *simultaneously* asks `ElementLocatorTools` to find the element for Step N+1 (if applicable).
6.  **Orchestrator** waits for Verification N to complete.
7.  If Verification N passes, proceed to Step N+1 (using the prefetched element location if ready).

### 6.2 Agents → UserInteractionService Flow
- Agents return a "Need User Input" result or call the service directly if configured.
- Service handles the UI thread and returns the structured user decision.

---

## 7. Data Transfer Objects and Records

- **New Records**: `UserDialogResult` subtypes (`NewElementCreationResult`, etc.).
- **Enhanced Records**: `ToolExecutionResult<T>` with metadata (timestamps, confidence scores).

---

## 8. Error Handling and Recovery

### 8.1 Circuit Breakers (Failsafe)
- **Implementation**: Use Failsafe library for zero-dependency resilience.
- **Policies**:
    - `RetryPolicy`: Exponential backoff for transient network errors.
    - `CircuitBreaker`: Open after 3 consecutive failures of Vision Model. Reset after 1 minute.
    - `Fallback`: Return "Algorithmic Match" or "Failure" when circuit is open.

### 8.2 Error Taxonomy
- **USER_INTERRUPTION**: Stop immediately.
- **VERIFICATION_FAILED**: Retry verification once (state might be settling), then fail.
- **TOOL_ERROR**: Retry based on policy.

---

## 9. State Management (TestExecutionContext)

**Critical**: Must be implemented early.

- **Components**:
    - `currentTestCase`: The static definition.
    - `executionHistory`: Log of all steps, tools, and results.
    - `sharedData`: Data passed between steps (e.g., "Generated User ID").
    - `visualState`: Latest screenshot and element locations.

---

## 10. Prompt Engineering for Agents

### 10.1 Agent System Prompts

**Required for Each Agent**:
- Clear instructions on agent's role and responsibilities.
- Decision-making guidelines (e.g., "Prefer algorithmic matching if confidence > 0.9").
- Tool usage instructions and error handling guidelines.

### 10.2 Prompt Versioning and Governance
- **Semantic Versioning**: Use semantic versioning (e.g., v1.2.0) for all agent system prompts.
- **Traceability**: Log the prompt version used in each run/trace.
- **Storage**: Maintain prompt files under `resources/prompt_templates/system/{agent}/`.

---

## 11. Configuration and Tuning

### 11.1 Agent Configuration Parameters
- **Budgets**: Define budgets per agent/test (tokens, tool calls, time).
- **Timeouts**: Global and per-tool timeouts aligned with error taxonomy.

**New Configuration Needed**:
- Agent model selection per agent type.
- Circuit Breaker thresholds (failure rate, reset time).
- Prefetching toggle (enable/disable).

### 11.2 Prompt Configuration
- Externalize system prompt templates.
- Allow runtime customization and version selection.

---

## 12. Documentation and Knowledge Transfer

### 12.1 Architecture Documentation
- Agent hierarchy and communication flow diagrams (including Prefetching).
- Decision-making flowcharts per agent.

### 12.2 Developer Documentation
- How to create new sub-agents.
- How to add tools and configure Circuit Breakers.
- Prompt engineering guidelines.

### 12.3 Operational Documentation
- Configuration guide (Failsafe policies, timeouts).
- Monitoring and logging (Circuit Breaker state, Agent decisions).

---

## 13. Safety, Security, and Compliance

- **Tool Allowlisting**: Enforce strict tool allowlists per agent.
- **Audit Logs**: Log all tool calls and sensitive operations; redact PII.

---

## 14. Performance and Cost Governance

- **Budgets**: Enforce budgets per test and per agent (tokens, tool calls, wall-clock time).
- **Caching**: Implement caching strategies for element location (Hint-based).
- **Early Abort**: Abort execution if budget is insufficient or error rate is too high.

---

## 15. Implementation Checklist

The following checklist enumerates all phases of the migration.

Legend:
- [ ] = TODO
- [x] = Done

### Phase 1 — Context and State Management (High Priority)
- [x] Implement `TestExecutionContext`
  - Tasks:
    - [x] Define context object (History, Data, Visual State).
    - [x] Update `Agent.java` to initialize and pass this context.

### Phase 2 — Foundations and Scaffolding
- [x] Confirm codebase compiles and tests run green.
- [x] Verify Maven build.

### Phase 3 — Core Models and DTO Enhancements
- [x] Make `ToolExecutionResult<T>` generic.
- [x] Add JSON schema annotations.
- [x] Review `ActionExecutionResult`.

### Phase 4 — User Dialog Result Records
- [x] Introduce strongly-typed dialog result records.

### Phase 5 — UserInteractionService API Definition
- [x] Define `UserInteractionService` interface and tool wrappers.
- [x] Implement dialog mappings.

### Phase 6 — Tool System Refactoring & Circuit Breakers
- [x] Update tool classes for LangChain4j.
- [ ] **Implement Failsafe Circuit Breakers** in `ElementLocatorTools`.
  - Tasks:
    - [ ] Add Failsafe dependency (if not present).
    - [ ] Wrap Vision Model calls with CircuitBreaker and RetryPolicy.
    - [ ] Implement Fallback logic.

### Phase 7 — Orchestrator and Sub-Agents
- [x] Extend `Agent.java` as Orchestrator.
- [x] Implement Sub-Agents (Action, Verification).
- [ ] **Implement Prefetching Logic** in `Agent.java`.
  - Tasks:
    - [ ] Identify "safe to prefetch" steps (read-only element location).
    - [ ] Use `CompletableFuture` to trigger location search during verification.

### Phase 8 — Agent Communication and Flows
- [x] Wire Orchestrator → Sub-agents.
- [x] Wire Agents → UserInteractionService.

### Phase 9 — Error Handling & Policies
- [ ] Implement Error Taxonomy.
- [ ] Configure Retry Policies per agent.

### Phase 10 — Prompt Engineering
- [ ] Author and version agent system prompts.
- [ ] Implement prompt logging.

### Phase 11 — Configuration and Tuning
- [ ] Extend `AgentConfig` (Timeouts, Budgets, Circuit Breakers).

### Phase 12 — Documentation
- [ ] Architecture and Developer docs.

### Phase 13 — Testing & Rollout
- [ ] Expand Test Suite (Unit, Integration).
- [ ] Feature Flag rollout.
- [ ] Post-migration cleanup.