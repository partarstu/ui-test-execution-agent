# Refactoring Plan: Tool Chaining Architecture

This document outlines the plan to refactor the agent's tool architecture to support dynamic tool chaining, where the `ElementLocator` provides coordinates to other tools like `MouseTools`.

## 1. Core Architectural Changes

The main goal is to move from a single tool per test step to a sequence of tool calls, allowing for a "find-then-act" pattern.

- **`ElementLocator`** will find a UI element and output a `Rectangle` object.
- **`Agent.java`** will be responsible for executing a sequence of tool calls, extracting simple types (like `x` and `y` coordinates) from the output of one tool and passing them as input to the next.
- **`MouseTools` / `KeyboardTools`** will be refactored to accept simple `int` coordinates to perform actions, while retaining all existing arguments.
- **The LLM** will be responsible for generating the sequence of tool calls, referencing specific properties of a tool's output (e.g., `centerX`).

## 2. Data Transfer Object (DTO) Modifications

The structure of the execution plan needs to be updated to support a sequence of actions.

### 2.1. `TestStepExecutionPlan.java`
This record will be modified to hold a list of tool calls instead of a single one.

**Current:**
```java
public record TestStepExecutionPlan(
        String actionUniqueId,
        String toolName,
        List<String> arguments
) {
}
```

**New:**
```java
public record TestStepExecutionPlan(
        @JsonFieldDescription("original action unique ID") String actionUniqueId,
        @JsonFieldDescription("A list of tool calls to be executed in sequence to perform the action.") List<ToolCall> toolCalls
) {
}
```

### 2.2. Create `ToolCall.java`
A new DTO will be created to represent a single tool call within the sequence.

**New File: `src/main/java/org/tarik/ta/dto/ToolCall.java`**
```java
package org.tarik.ta.dto;

import org.tarik.ta.annotations.JsonFieldDescription;

import java.util.Map;

public record ToolCall(
        @JsonFieldDescription("Name of the tool to be executed.") String toolName,
        @JsonFieldDescription("A map of argument names to their values for the tool. The values can be strings or placeholders for outputs from previous tools.") Map<String, Object> arguments
) {
}
```

### 2.3. `TestCaseExecutionPlan.java`
No changes are needed here, as it already contains a list of `TestStepExecutionPlan`s.

## 3. Tool Class Refactoring

### 3.1. `ElementLocator.java`
- This class will be converted into a proper tool class.
- The `locateElementOnTheScreen` method will be annotated with `@Tool`. Its return type will be changed from `UiElementLocationResult` to `java.awt.Rectangle`. This complex object will be handled by the `Agent` and not directly passed to other tools by the LLM.
- It will no longer be a static class. An instance will be created and registered with the agent.

**Example Method Signature:**
```java
@Tool("Locates a UI element on the screen and returns its bounding box (a Rectangle object).")
public Rectangle locateElementOnTheScreen(
    @P("Detailed description of the UI element to locate.") String elementDescription,
    @P(value = "Test data associated with the element, if any", required = false) String testSpecificData) {
    // ... existing logic ...
    // return uiElementLocationResult.uiElementBoundingBox();
}
```

### 3.2. `MouseTools.java`
- Methods will be updated to accept simple integer coordinates (`x`, `y`) instead of deriving the location from an `elementDescription`.
- All existing parameters, like `elementDescription` and `relatedData`, will be preserved to maintain context and logging capabilities.
- The internal calls to `locateElementOnTheScreen` will be removed.

**Example (Before):**
```java
public static ToolExecutionResult leftMouseClick(
        @P("Detailed description of the UI element to left-click on") String elementDescription,
        @P(...) String relatedData) {
    // ... calls locateElementOnTheScreen ...
}
```

**Example (After):**
```java
@Tool("Performs a left mouse click at the specified coordinates.")
public ToolExecutionResult leftMouseClick(
        @P("The x-coordinate for the click.") int x,
        @P("The y-coordinate for the click.") int y,
        @P("Detailed description of the UI element to left-click on, for logging and context.") String elementDescription,
        @P(value = "Any data related to this action or this element, if any", required = false) String relatedData) {
    Point clickPoint = new Point(x, y);
    // ... perform click at 'clickPoint' ...
    var message = "Clicked left mouse button on '%s' at coordinates (%d, %d)".formatted(elementDescription, x, y);
    return getSuccessfulResult(message);
}
```

### 3.3. `KeyboardTools.java`
- Methods like `typeText` that interact with specific UI elements will be updated similarly to `MouseTools` to click at a coordinate before typing.

**Example (After):**
```java
@Tool("Types text at a specified location, after clicking it first.")
public ToolExecutionResult typeText(
        @P("The text to be typed.") String text,
        @P("The x-coordinate of the target element to click before typing.") int x,
        @P("The y-coordinate of the target element to click before typing.") int y,
        @P("A boolean which defines if existing contents of the UI element need to be wiped out.") boolean wipeOutOldContent,
        @P("Detailed description of the UI element, for logging and context.") String elementDescription,
        @P(value = "Test data associated with the element, if any", required = false) String testSpecificData) {
    // 1. Perform a click at (x, y)
    // 2. Type the text
    // ...
}
```

## 4. Agent Logic Refactoring (`Agent.java`)

The `Agent.java` class requires the most significant changes to orchestrate the tool chain.

### 4.1. Tool Registration
- In `getToolsByName()`, instantiate and include the new `ElementLocator` tool alongside `MouseTools`, `KeyboardTools`, and `CommonTools`.

### 4.2. `processToolExecutionRequest`
- This method will be completely overhauled to take a `TestStepExecutionPlan` (which contains a list of `ToolCall`s).
- It must iterate through the `toolCalls` list.
- It needs a mechanism to handle placeholders that can access properties of complex objects. A `Map<String, Object>` will store the outputs of executed tools.
- For each `ToolCall`:
    1.  **Resolve Arguments:** Check the `arguments` map for placeholders (e.g., `${locateElementOnTheScreen.output.centerX}`). Use reflection or a library like Apache Commons BeanUtils to access properties from the objects stored in the context map.
    2.  Execute the tool using the resolved arguments.
    3.  Store the entire returned object (e.g., the `Rectangle`) in the context map: `context.put("locateElementOnTheScreen.output", result)`.
    4.  If any tool fails, the entire test step fails.

## 5. Prompt Engineering

### 5.1. `ActionExecutionPlanPrompt.java`
- The system prompt needs to be updated to instruct the LLM on how to chain tools and access output properties.

**Example Instruction for the Prompt:**
> "For actions that involve interacting with a UI element, you MUST follow a sequence. First, use the 'locateElementOnTheScreen' tool. This tool returns a `Rectangle` object. You can then access its properties. To get the center coordinates, use `${locateElementOnTheScreen.output.centerX}` for the x-coordinate and `${locateElementOnTheScreen.output.centerY}` for the y-coordinate. You MUST pass these coordinates to the `x` and `y` arguments of the subsequent tool call (e.g., 'leftMouseClick'). Always pass the original element description through to all tools for logging purposes."

This explicit instruction is crucial for the LLM to generate the correct `TestCaseExecutionPlan`.

## 6. Implementation Steps

1.  [ ] **DTOs:** Modify `TestStepExecutionPlan.java` and create `ToolCall.java`.
2.  [ ] **`ElementLocator.java`:** Refactor into a non-static tool class and update `locateElementOnTheScreen` to return a `Rectangle`.
3.  [ ] **`MouseTools.java`:** Refactor methods to accept `int x, int y` coordinates and retain all other arguments.
4.  [ ] **`KeyboardTools.java`:** Refactor methods to accept `int x, int y` coordinates and retain all other arguments.
5.  [ ] **`Agent.java`:**
    - [ ] Update tool registration.
    - [ ] Re-implement `processToolExecutionRequest` to handle the tool chain and property-based placeholder resolution.
6.  [ ] **Prompts:** Update `ActionExecutionPlanPrompt` with new instructions for chaining and property access.
7.  [ ] **Testing:** Create or update tests to validate the new tool chaining functionality.
