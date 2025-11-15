# Agentic Architecture Refactoring Implementation Plan

This document outlines the detailed, phase-based plan to refactor the UI Test Automation Agent from its current state to a more flexible and powerful agentic architecture, leveraging LangChain4j.

## Phase 1: Core Data Structures and Tooling Refactoring

This foundational phase focuses on updating the core data structures and tool contracts to support the new agentic architecture.

### ~~1.1. Generic `ToolExecutionResult` - Done~~

- **Objective:** Modify the existing `ToolExecutionResult` to be a generic record. This will allow tools to return structured, type-safe results beyond a simple string message.
- **File to Modify:** `src/main/java/org/tarik/ta/tools/AbstractTools.java`
- **Plan:**
    1.  Modify the `ToolExecutionResult` record to be generic: `ToolExecutionResult<T>`.
    2.  The record will contain `ToolExecutionStatus executionStatus`, `String message`, `boolean retryMakesSense`, `BufferedImage screenshot`, and a new optional field `T resultPayload`.
    3.  Update the constructor and factory methods (`getSuccessfulResult`, `getFailedToolExecutionResult`, etc.) to support the new generic type and payload. Create overloaded versions to handle cases with and without a result payload.
    
### ~~1.2. Modified `ElementLocation` Record~~

- **Objective:** ElementLocation record must contain to serve as the standardized output for the element location agent.
- **File to Create:** `src/main/java/org/tarik/ta/dto/ElementLocation.java`
- **Plan:**
    1.  Create a new public record named `ElementLocation`.
    2.  It will contain x and y coordinates of the center of the element and original bounding box.
    3.  Annotate the record and its fields with `@JsonClassDescription` and `@JsonFieldDescription` respectively, similar to other DTOs, to provide a clear schema for the LLM.

### ~~1.3. Update Existing Tools~~

- **Objective:** Refactor all classes in the `tools` package to use the new generic `ToolExecutionResult<T>`.
- **Files to Modify:** `CommonTools.java`, `KeyboardTools.java`, `MouseTools.java`.
- **Plan:**
    1.  Iterate through each tool method in the specified classes.
    2.  Update the method signatures to return `ToolExecutionResult<T>` where `T` is the specific, structured result type. For tools that do not produce a specific output, `ToolExecutionResult<?>` can be used.
    3.  For methods that currently return `ToolExecutionResult`, update them to return the new generic version, populating the `resultPayload` field where applicable. For instance, a tool that finds an element should return `ToolExecutionResult<ElementLocation>`.
    4.  Ensure all DTOs returned as payloads are annotated correctly for LLM schema generation.


## Phase 2: User Interaction and Element Locator Agents

This phase focuses on creating the first two sub-agents, encapsulating user dialogs and element finding logic.

### 2.1. `UserInteractionAgent`

- **Objective:** Create a new agent to handle all user dialog interactions, abstracting them into tools.
- **Files to Create:**
    - `src/main/java/org/tarik/ta/agents/UserInteractionAgent.java` (interface)
    - `src/main/java/org/tarik/ta/agents/UserInteractionTools.java` (implementation class)
- **Plan:**
    1.  Create the `UserInteractionAgent` interface. This interface will define the agent's capabilities.
    2.  Create the `UserInteractionTools` class. This class will contain the `@Tool`-annotated methods.
    3.  For each dialog in `src/main/java/org/tarik/ta/user_dialogs/**`, create a corresponding tool in `UserInteractionTools`.
        -   Example: Create a tool `promptForNewElementInfo` that encapsulates the logic from `NewElementInfoNeededPopup` and `UiElementScreenshotCaptureWindow`. This tool will take an element description as input and return the newly created `UiElement` information.
        -   Example: Create a tool `confirmLocatedElement` which takes a screenshot and a bounding box, displays the `LocatedElementConfirmationDialog`, and returns the user's choice (`CORRECT`, `INCORRECT`).
    4.  All methods in `UserInteractionTools` will be static and will return a structured result (e.g., a record representing the user's choice or input).
    5.  The existing dialog classes will be called by these new tools.

### 2.2. `ElementLocatorAgent`

- **Objective:** Convert the `ElementLocator` class into a sub-agent responsible for finding UI elements.
- **Files to Create/Modify:**
    - `src/main/java/org/tarik/ta/agents/ElementLocatorAgent.java` (interface)
    - `src/main/java/org/tarik/ta/tools/ElementLocator.java` (will be refactored into `ElementLocatorTools.java`)
- **Plan:**
    1.  Create the `ElementLocatorAgent` interface. It will have one primary method, e.g., `locateElement(String elementDescription, String testSpecificData)`, which will be annotated with `@Agent`.
    2.  Rename `ElementLocator.java` to `ElementLocatorTools.java` to better reflect its new role.
    3.  The `locateElementOnTheScreen` method will become the primary tool in `ElementLocatorTools`. Its signature will be updated to return `ToolExecutionResult<ElementLocation>`.
    4.  Deprecate the static `locateElementOnTheScreen` method in `ElementLocator` and mark it for future removal.
    5.  Refactor the logic within `ElementLocatorTools` to call the `UserInteractionAgent` (via agentic invocation) instead of directly calling the dialog popups. For example, when no element is found, it will invoke the `UserInteractionAgent` to ask the user for the next action.
    6.  Use `AgenticServices.builder()` to construct the `ElementLocatorAgent`, providing it with the `ElementLocatorTools` and the `UserInteractionAgent` as a sub-agent.

### 2.3. Testing for Phase 2

- **Objective:** Verify the functionality of the new agents.
- **Plan:**
    1.  Create `UserInteractionAgentTest.java`. Use Mockito to mock the dialogs and verify that the tools call the correct dialogs with the correct parameters.
    2.  Create `ElementLocatorAgentTest.java`. Mock the `UserInteractionAgent` and the underlying models to test the `ElementLocatorAgent`'s logic under various scenarios (element found, element not found, user interruption).

## Phase 3: Refactoring `MouseTools` and `KeyboardTools`

This phase decouples the mouse and keyboard tools from element descriptions, making them operate on coordinates.

### ~~3.1. `MouseTools` Refactoring~~

- **Objective:** Modify `MouseTools` methods to accept or `int` coordinates instead of element descriptions.
- **File to Modify:** `src/main/java/org/tarik/ta/tools/MouseTools.java`
- **Plan:**
    1.  For each method that takes an `elementDescription` (e.g., `rightMouseClick`, `leftMouseClick`), replace this argument with `int x, 
    int y` coordinates.
    2.  The new methods will perform the mouse action directly at the given coordinates.
    

### ~~3.2. `KeyboardTools` Refactoring~~

- **Objective:** Modify `KeyboardTools` methods that interact with UI elements to accept coordinates.
- **File to Modify:** `src/main/java/org/tarik/ta/tools/KeyboardTools.java`
- **Plan:**
    1.  For methods like `typeText` and `clearData` that accept an `elementDescription`, create new overloaded versions.
    2.  The new `typeText` will accept `String text, int x, int y, ...`. It will first perform a left-click at the given coordinates and then type the text.
    3.  The old methods will be deprecated and marked for future removal.

### 3.3. Testing for Phase 3

- **Objective:** Ensure the refactored tools work correctly with coordinate-based input.
- **Plan:**
    1.  Update `MouseToolsTest.java` and `KeyboardToolsTest.java`.
    2.  Create tests for the new coordinate-based methods. Use `Mockito` to mock the `Robot` class to verify that mouse movements and clicks happen at the expected coordinates.

## Phase 4: `TestExecutionAgent` Implementation

This phase involves creating the core sub-agent for executing individual test steps.

### 4.1. `TestExecutionAgent` Creation

- **Objective:** Create a new agent that executes a single test step or precondition by using a dedicated set of tools for action and verification.
- **Files to Create:**
    - `src/main/java/org/tarik/ta/agents/TestExecutionAgent.java` (interface)
    - `src/main/java/org/tarik/ta/agents/TestExecutionTools.java` (implementation class)
- **Plan:**
    1.  Define the `TestExecutionAgent` interface. It will have a main method like `executeStep(TestStep testStep)` that is responsible for carrying out a single step.
    2.  Create the `TestExecutionTools` class. This class will contain the four specific tools required by the agent, as defined in the architecture:
        -   `executeAction(String actionDescription, String testData)`: A tool to perform the main action of a test step. This tool will internally orchestrate calls to the `ElementLocatorAgent`, `MouseTools`, and `KeyboardTools` to perform the required UI interaction.
        -   `verifyActionResult(String actionDescription, String expectedResult)`: A tool to verify that the outcome of an action matches the expected result, likely by using a vision model to analyze the screen.
        -   `executePrecondition(String preconditionDescription, String testData)`: A tool similar to `executeAction`, but for setting up preconditions.
        -   `verifyPrecondition(String preconditionDescription, String expectedState)`: A tool similar to `verifyActionResult`, but for preconditions.
    3.  The `TestExecutionAgent` will be constructed with the tools from `TestExecutionTools`.
    4.  The agent's primary responsibility will be to analyze the incoming `TestStep` or precondition and decide which of its tools to call in the correct sequence. For a standard test step, it will first call `executeAction` and then `verifyActionResult`.
    5.  The agent will aggregate the results from its tools and return a final, structured result (e.g., a new `TestStepExecutionResult` record) indicating the overall success or failure of the step.

### 4.2. Testing for Phase 4

- **Objective:** Verify the `TestExecutionAgent` can correctly orchestrate a test step.
- **Plan:**
    1.  Create `TestExecutionAgentTest.java`.
    2.  Mock all sub-agents and tools (`ElementLocatorAgent`, `MouseTools`, etc.).
    3.  Write tests to verify that for a given test step description, the `TestExecutionAgent` calls the correct sequence of sub-agents and tools with the correct parameters.

## Phase 5: Main Orchestrator (`Agent.java`) Refactoring

This final implementation phase refactors the main `Agent.java` to act as the top-level orchestrator.

### 5.1. `Agent.java` as Orchestrator

- **Objective:** Refactor `Agent.java` to delegate test execution to the `TestExecutionAgent`.
- **File to Modify:** `src/main/java/org/tarik/ta/Agent.java`
- **Plan:**
    1.  The `Agent.java` class will no longer contain the complex logic for parsing test steps and calling tools directly.
    2.  Its primary method, `executeTestCase`, will be refactored.
    3.  Inside `executeTestCase`, it will loop through each precondition and test step from the `TestCase` object.
    4.  For each step, it will invoke the `TestExecutionAgent`.
    5.  It will manage the overall test execution context, collecting the `TestStepResult` from each `TestExecutionAgent` invocation.
    6.  The old logic for tool execution and planning within `Agent.java` will be deprecated and marked for removal.
    7.  Use `AgenticServices.builder()` to construct the `TestExecutionAgent` and provide it with its required sub-agents and tools.

### 5.2. Testing for Phase 5

- **Objective:** Ensure the main orchestrator correctly delegates tasks and aggregates results.
- **Plan:**
    1.  Update `AgentTest.java`.
    2.  Mock the `TestExecutionAgent`.
    3.  Verify that `executeTestCase` iterates through all test steps and calls the `TestExecutionAgent` for each one.
    4.  Verify that the final `TestExecutionResult` is correctly assembled based on the mocked results from the `TestExecutionAgent`.

## Phase 6: Integration and End-to-End Testing

- **Objective:** Ensure all agents work together seamlessly.
- **Plan:**
    1.  Develop a suite of integration tests that run a full `TestCase` through the new agentic architecture.
    2.  These tests should not use mocks for the agents themselves but can mock external dependencies (like the LLM if necessary, or use a test-specific model).
    3.  Create end-to-end tests that execute a simple, real UI automation task to validate the entire system from test case definition to UI interaction.
    4.  Perform manual testing to confirm that the user-in-the-loop dialogs are functioning as expected within the new architecture.
