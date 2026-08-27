package com.titanium.underwriting.web.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.titanium.underwriting.application.service.UnderwritingCommandService;
import com.titanium.underwriting.command.AssessMaintenanceUnderwritingCommand;
import com.titanium.underwriting.event.MaintenanceUnderwritingAssessedEvent;
import com.titanium.underwriting.valueobject.MaintenanceUnderwritingConclusion;
import com.titanium.underwriting.valueobject.UnderwritingId;

class MaintenanceUnderwritingApiProviderTest {

    @Test
    void shouldExposeFormalMaintenanceAssessmentRoute() throws Exception {
        UnderwritingCommandService commandService = mock(UnderwritingCommandService.class);
        when(commandService.assessMaintenance(any())).thenReturn(new MaintenanceUnderwritingAssessedEvent(
                UnderwritingId.of("underwriting-1"), "tenant-1", "case-1", "policy-1", 7L,
                "POLICY_INFO_CHANGE", "case-1:task-1", "a".repeat(64), "rule-v1", "model-v1",
                MaintenanceUnderwritingConclusion.CONDITIONAL_APPROVED,
                List.of("REVIEW_FIELD:insured.occupation"), "附加条件通过",
                LocalDateTime.parse("2026-08-25T12:00:00"),
                LocalDateTime.parse("2026-08-25T12:00:00"), "maintenance-service"));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new MaintenanceUnderwritingApiProvider(commandService)).build();

        MvcResult result = mockMvc.perform(post("/underwriting/api/maintenance-assessments")
                        .header("X-Tenant-ID", "tenant-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "maintenanceId": "case-1",
                                  "policyId": "policy-1",
                                  "policyBaselineVersion": 7,
                                  "productId": "product-1",
                                  "productVersion": "product-v3",
                                  "planVersion": "plan-v2",
                                  "itemCode": "POLICY_INFO_CHANGE",
                                  "configurationVersion": "config-v4",
                                  "configurationContentHash": "%s",
                                  "configurationRequiresUnderwriting": true,
                                  "riskFieldChanges": [],
                                  "idempotencyKey": "case-1:task-1",
                                  "payloadHash": "%s",
                                  "requestedBy": "maintenance-service"
                                }
                                """.formatted("b".repeat(64), "a".repeat(64))))
                .andReturn();

        assertEquals(200, result.getResponse().getStatus());
        String responseBody = result.getResponse().getContentAsString();
        assertTrue(responseBody.contains("\"underwritingCaseId\":\"underwriting-1\""));
        assertTrue(responseBody.contains("\"conclusion\":\"CONDITIONAL_APPROVED\""));

        ArgumentCaptor<AssessMaintenanceUnderwritingCommand> captor =
                ArgumentCaptor.forClass(AssessMaintenanceUnderwritingCommand.class);
        verify(commandService).assessMaintenance(captor.capture());
        assertEquals("tenant-1", captor.getValue().tenantId());
    }
}
