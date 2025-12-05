# Rebuild Plan: Multi-Module Refactoring

## Objective
Refactor the current single-module project into a multi-module Maven project to support multiple test execution agents. Create a shared `agent_core` module for common functionality and a `ui_test_agent` module for the existing UI automation logic.

## 1. Project Structure Overview

**New Structure:**
```text
D:\Projects\ui-test-automation-agent\
├── pom.xml                  (Parent POM)
├── agent_core\              (New Module: Shared Logic)
│   ├── pom.xml
│   └── src\main\java\...
└── ui_test_agent\           (New Module: Existing UI Agent)
    ├── pom.xml
    └── src\main\java\...
```

## 2. Phase 1: Infrastructure Setup

### 2.1 Parent POM Creation
- Create a root `pom.xml` with `<packaging>pom</packaging>`.
- Move `<properties>`, `<developers>`, `<profiles>`, and `<build>` (plugin management) from the current `pom.xml` to this parent POM.
- Define `<modules>`:
  - `agent_core`
  - `ui_test_agent`
- Define `<dependencyManagement>` to control versions of dependencies (LangChain4j, Jackson, etc.) across modules.

### 2.2 Module Directories
- Create `agent_core` directory.
- Create `ui_test_agent` directory.
- Move the existing `src` folder into `ui_test_agent` initially (to preserve git history, then move core files out).

## 3. Phase 2: `agent_core` Module Implementation

### 3.1 Module Setup
- Create `agent_core/pom.xml`.
- Add dependencies:
  - `langchain4j` (core, ai-gemini, open-ai, etc.)
  - `jackson`
  - `slf4j-api` / `logback`
  - `guava`
  - `commons-math3` (if used in core utils)
  - `lombok` (if used) or `jetbrains-annotations`.

### 3.2 Code Migration (Move from `ui_test_agent` to `agent_core`)
The following classes/packages should be moved to `agent_core`. *Note: Package names can remain `org.tarik.ta.*` or be refined to `org.tarik.ta.core.*`.*

1.  **Configuration**:
    - `org.tarik.ta.AgentConfig` (See Section 4.1 for refactoring)
2.  **Core Models & DTOs**:
    - `org.tarik.ta.model.GenAiModel`, `ModelFactory`
    - `org.tarik.ta.model.TestExecutionContext` (Generic parts)
    - `org.tarik.ta.dto.*` (Result objects: `FinalResult`, `AgentExecutionResult`, `TestCase`, `TestStep` etc. - *Verify if TestCase is generic enough, likely yes*)
    - `org.tarik.ta.tools.AgentExecutionResult`
3.  **Base Agent Logic**:
    - `org.tarik.ta.agents.BaseAiAgent` (See Section 4.2 for refactoring)
    - `org.tarik.ta.agents.annotations.*`
4.  **Utilities**:
    - `org.tarik.ta.utils.CommonUtils` (See Section 4.3)
    - `org.tarik.ta.utils.PromptUtils`, `ModelUtils`
    - `org.tarik.ta.error.*` (`RetryPolicy`, `ErrorCategory` etc.)
    - `org.tarik.ta.exceptions.*`
5.  **Concurrency**:
    - `org.tarik.ta.concurrency.*`
6.  **RAG / Retrieval**:
    - `org.tarik.ta.rag.*` (Interfaces and generic implementations)

## 4. Phase 3: Code Refactoring for Decoupling

### 4.1 `AgentConfig` Handling
- **Action**: Move `AgentConfig.java` to `agent_core`.
- **Reason**: It contains mixed generic and UI config. Splitting it now is high-effort. `agent_core` will provide configuration for all known agents.

### 4.2 `BaseAiAgent` Decoupling (Crucial)
- **Issue**: `BaseAiAgent` interface default methods call `CommonUtils.captureScreen()` (static binding to UI/AWT).
- **Solution**:
  1. Create `org.tarik.ta.core.AgentContext` (or `ContextManager`) in `agent_core`.
  2. Add a static `Supplier<BufferedImage> contextCapturer`.
  3. Modify `BaseAiAgent` to call `AgentContext.captureContext()` instead of `CommonUtils.captureScreen()`.
  4. In `ui_test_agent` (Start-up), initialize `AgentContext` with `CommonUtils::captureScreen`.

### 4.3 `CommonUtils`
- **Action**: Move `CommonUtils` to `agent_core`.
- **Implication**: `agent_core` will depend on `java.awt` (Desktop module). This is acceptable for this project scope.

### 4.4 `AgentExecutionResource`
- **Issue**: Hardcodes `new UiAgentExecutor()`.
- **Action**: Refactor `AgentExecutionResource` (if moving to core) to accept `AgentExecutor` in its constructor.
- **Move**: Move `AgentExecutionResource` and `AgentCardProducer` to `agent_core` (package `org.tarik.ta.server` or similar).
- **Integration**: `Server.java` (in UI module) will instantiate `UiAgentExecutor` and pass it to `AgentExecutionResource`.

## 5. Phase 4: `ui_test_agent` Module Implementation

### 5.1 Module Setup
- Create `ui_test_agent/pom.xml`.
- Dependencies:
  - `agent_core` (Project dependency)
  - `opencv`, `ffmpeg`, `javacv` (UI/Video specific)
  - `a2a-java-sdk-transport-jsonrpc` (if not in core)

### 5.2 Remaining Code
The following stays in `ui_test_agent`:
1.  **Entry Point**: `org.tarik.ta.Server`, `org.tarik.ta.Agent` (Orchestrator).
2.  **UI Executors**: `org.tarik.ta.a2a.UiAgentExecutor`.
3.  **UI Agents**: `ElementSelectionAgent`, `Precondition*Agent`, `TestStep*Agent`, `ElementBoundingBoxAgent`, etc.
4.  **Tools**: `MouseTools`, `KeyboardTools`, `ElementLocatorTools`.
5.  **UI Utils**: `ImageUtils` (if heavy OpenCV usage), `ScreenRecorder`.
6.  **Managers**: `VerificationManager`.

## 6. Implementation Steps (Execution Order)

1.  **Backup**: Ensure Git status is clean.
2.  **Root POM**: Create `pom.xml` in root.
3.  **Create Folders**: `mkdir agent_core`, `mkdir ui_test_agent`.
4.  **Move Source**:
    - `git mv src ui_test_agent/src`
    - `git mv pom.xml ui_test_agent/pom.xml` (rename/edit later)
5.  **Setup Core**:
    - Create `agent_core/src/main/java/...` structure.
    - `git mv` identified core files from `ui_test_agent/src/...` to `agent_core/src/...`.
    - Create `agent_core/pom.xml`.
6.  **Refactor Core**:
    - Implement `AgentContext` for screenshot decoupling.
    - Update `BaseAiAgent` to use `AgentContext`.
    - Update `AgentExecutionResource` constructor.
7.  **Setup UI Agent**:
    - Update `ui_test_agent/pom.xml` to depend on `agent_core`.
    - Update `Server.java` to inject `UiAgentExecutor` into `AgentExecutionResource`.
    - Update `Server.java` to initialize `AgentContext`.
8.  **Verify**: Run `mvn clean install` from root. Fix imports and visibility issues (some package-private classes might need to be public if moved across packages/modules).

## 7. Potential Risks & Mitigations
- **Circular Dependencies**: Watch out for `AgentConfig` usage in generic classes vs UI classes. (Mitigated by keeping `AgentConfig` in core).
- **Package-Private Access**: Moving classes to a different module/package will break package-private access.
  - *Mitigation*: Change visibility to `public` for shared symbols.
- **Tests**: Tests need to be split too.
  - *Action*: Move `src/test` files corresponding to the moved classes into `agent_core/src/test`. Keep UI specific tests in `ui_test_agent`.

## 8. Final Artifacts
- `pom.xml` (Root)
- `agent_core` (Jar)
- `ui_test_agent` (Executable Jar with dependencies)
