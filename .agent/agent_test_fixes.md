## AgentTest Fixes

### Issues Identified:
1. **Mockito stubbing issue**: Tests use `when().thenReturn()` but should use `doReturn().when()` for the builder pattern
2. **Method signature mismatch**: Action agents use 1-arg `executeWithRetry(Supplier)` but tests stub 2-arg version
3. **Missing AgentConfig mocks**: `isPrefetchingEnabled()` and `isUnattendedMode()` are not mocked
4. **Test expectation mismatch**: `preconditionExecutionFails` expects FAILED but Agent.java returns ERROR

### Fixes Applied:
1. Changed Mockito stubbing in `configureBuilder` from `when().thenReturn()` to `doReturn().when()`
2. Fixed all action agent stubs to use 1-arg `executeWithRetry(Supplier)` 
3. Added mocks for `isPrefetchingEnabled()` and `isUnattendedMode()` 
4. Test expectations remain as-is; the implementation in Agent.java may need adjustment for semantic correctness

### Status:
- Modified AgentTest.java with proper stubbing
- Ready to run tests
