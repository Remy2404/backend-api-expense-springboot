package com.wing.backendapiexpensespringboot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.wing.backendapiexpensespringboot.config.OpenRouterConfig;
import com.wing.backendapiexpensespringboot.dto.agent.AgentDecision;
import com.wing.backendapiexpensespringboot.dto.agent.BudgetProposal;
import com.wing.backendapiexpensespringboot.dto.agent.TransactionProposal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiDecisionServiceParsingTest {

    private AiDecisionService service;
    private OpenRouterConfig openRouterConfig;

    @BeforeEach
    void setUp() {
        openRouterConfig = mock(OpenRouterConfig.class);
        when(openRouterConfig.getModel()).thenReturn("openai/gpt-oss-20b:free");
        when(openRouterConfig.getFallbackModels()).thenReturn("openai/gpt-oss-120b:free, google/gemma-4-31b-it:free");
        service = new AiDecisionService(
                mock(OpenAIClient.class),
                openRouterConfig,
                new ObjectMapper());
    }

    @Test
    void parseDecision_shouldCoerceBudgetAmountFromCurrencyString() throws Exception {
        String rawJson = """
                {
                  "actionType": "PREPARE_BUDGET",
                  "riskLevel": "WRITE_FINANCIAL_DATA",
                  "dataScope": "CURRENT_USER_ONLY",
                  "requiresConfirmation": true,
                  "missingFields": [],
                  "userFacingMessage": "I can set this budget.",
                  "reasoning": "User provided budget amount.",
                  "queryPlan": null,
                  "proposal": {
                    "amount": "$400"
                  }
                }
                """;

        AgentDecision decision = service.parseDecision(rawJson, "2026-03");
        BudgetProposal proposal = (BudgetProposal) decision.proposal();

        assertThat(proposal.totalAmount()).isEqualTo(400.0);
        assertThat(proposal.month()).isEqualTo("2026-03");
    }

    @Test
    void parseDecision_shouldAcceptFlatTransactionProposalShape() throws Exception {
        String rawJson = """
                {
                  "actionType": "PREPARE_TRANSACTION",
                  "riskLevel": "WRITE_FINANCIAL_DATA",
                  "dataScope": "CURRENT_USER_ONLY",
                  "requiresConfirmation": true,
                  "missingFields": [],
                  "userFacingMessage": "I can add this expense.",
                  "reasoning": "User provided transaction details.",
                  "queryPlan": null,
                  "proposal": {
                    "type": "expense",
                    "amount": "$45.50",
                    "currency": "USD",
                    "category": "Groceries",
                    "merchant": "Whole Foods"
                  }
                }
                """;

        AgentDecision decision = service.parseDecision(rawJson, "2026-03");
        TransactionProposal proposal = (TransactionProposal) decision.proposal();

        assertThat(proposal.transactions()).hasSize(1);
        assertThat(proposal.transactions().get(0).amount()).isEqualTo(45.50);
        assertThat(proposal.transactions().get(0).category()).isEqualTo("Groceries");
    }

    @Test
    void parseDecision_shouldHandleWrappedJson() throws Exception {
        String rawJson = """
                Here is the decision:
                ```json
                {
                  "actionType": "PREPARE_TRANSACTION",
                  "riskLevel": "WRITE_FINANCIAL_DATA",
                  "dataScope": "CURRENT_USER_ONLY",
                  "requiresConfirmation": true,
                  "missingFields": [],
                  "userFacingMessage": "ok",
                  "reasoning": "ok",
                  "queryPlan": null,
                  "proposal": {
                    "transactions": [
                      {
                        "type": "expense",
                        "amount": 45.5,
                        "currency": "USD",
                        "category": "Groceries"
                      }
                    ]
                  }
                }
                ```
                """;

        AgentDecision decision = service.parseDecision(rawJson, "2026-03");
        assertThat(decision.actionType().name()).isEqualTo("PREPARE_TRANSACTION");
    }

    @Test
    void buildModelCandidates_shouldIncludePrimaryAndFallbacksWithoutDuplicates() {
        List<String> models = service.buildModelCandidates();

        assertThat(models).containsExactly(
                "openai/gpt-oss-20b:free",
                "openai/gpt-oss-120b:free",
                "google/gemma-4-31b-it:free");
    }
}
