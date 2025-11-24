package org.tarik.ta.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tarik.ta.AgentConfig;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BudgetManagerTest {

    @BeforeEach
    void setUp() {
        BudgetManager.reset();
    }

    @Test
    void checkTokenBudget_shouldNotThrow_whenUnderLimit() {
        // Given
        int limit = AgentConfig.getAgentTokenBudget();
        if (limit <= 0) return; // Skip if no limit

        // When
        BudgetManager.consumeTokens(limit - 1);

        // Then
        assertThatCode(BudgetManager::checkTokenBudget).doesNotThrowAnyException();
    }

    @Test
    void checkTokenBudget_shouldNotThrow_whenAtLimit() {
        // Given
        int limit = AgentConfig.getAgentTokenBudget();
        if (limit <= 0) return; // Skip if no limit

        // When
        BudgetManager.consumeTokens(limit);

        // Then
        assertThatCode(BudgetManager::checkTokenBudget).doesNotThrowAnyException();
    }

    @Test
    void checkTokenBudget_shouldThrow_whenOverLimit() {
        // Given
        int limit = AgentConfig.getAgentTokenBudget();
        if (limit <= 0) return; // Skip if no limit

        // When
        try {
            BudgetManager.consumeTokens(limit + 1);
        } catch (RuntimeException e) {
            // Expected
        }

        // Then
        assertThatThrownBy(BudgetManager::checkTokenBudget)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Token budget exceeded");
    }

    @Test
    void checkToolCallBudget_shouldNotThrow_whenUnderLimit() {
        // Given
        int limit = AgentConfig.getAgentToolCallsBudget();
        if (limit <= 0) return; // Skip if no limit

        // When
        BudgetManager.consumeToolCalls(limit - 1);

        // Then
        assertThatCode(BudgetManager::checkToolCallBudget).doesNotThrowAnyException();
    }

    @Test
    void checkToolCallBudget_shouldNotThrow_whenAtLimit() {
        // Given
        int limit = AgentConfig.getAgentToolCallsBudget();
        if (limit <= 0) return; // Skip if no limit

        // When
        BudgetManager.consumeToolCalls(limit);

        // Then
        assertThatCode(BudgetManager::checkToolCallBudget).doesNotThrowAnyException();
    }

    @Test
    void checkToolCallBudget_shouldThrow_whenOverLimit() {
        // Given
        int limit = AgentConfig.getAgentToolCallsBudget();
        if (limit <= 0) return; // Skip if no limit

        // When
        try {
            BudgetManager.consumeToolCalls(limit + 1);
        } catch (RuntimeException e) {
            // Expected
        }

        // Then
        assertThatThrownBy(BudgetManager::checkToolCallBudget)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Tool call budget exceeded");
    }
}
