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
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.titanium.common.context.RequestContextInterceptor;
import com.titanium.metadata.enums.underwriting.MaintenanceUnderwritingConclusion;
import com.titanium.underwriting.application.service.UnderwritingCommandService;
import com.titanium.underwriting.command.AssessMaintenanceUnderwritingCommand;
import com.titanium.underwriting.event.MaintenanceUnderwritingAssessedEvent;
import com.titanium.underwriting.valueobject.UnderwritingId;
import com.titanium.underwriting.web.mapper.MaintenanceUnderwritingWebMapper;

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
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new MaintenanceUnderwritingApiProvider(commandService,
                        Mappers.getMapper(MaintenanceUnderwritingWebMapper.class)))
                // 租户已不再由契约入参显式传递，改由 RequestContextInterceptor 从请求头填充上下文供 provider 读取
                .addInterceptors(new RequestContextInterceptor())
                .build();

        String payload = """
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
                                """.formatted("b".repeat(64), "a".repeat(64));

        MvcResult result = mockMvc.perform(post("/api/v1/maintenance-assessments")
                        .header("X-Tenant-ID", "tenant-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andReturn();

        assertEquals(200, result.getResponse().getStatus());
        String responseBody = result.getResponse().getContentAsString();
        // m24-01a：契约统一返回 ApiResponse 信封，载荷落在 data 内（字段断言仍成立，另锁信封成功码）
        assertTrue(responseBody.contains("\"code\":\"00000000\""), responseBody);
        assertTrue(responseBody.contains("\"underwritingCaseId\":\"underwriting-1\""));
        assertTrue(responseBody.contains("\"conclusion\":\"CONDITIONAL_APPROVED\""));

        ArgumentCaptor<AssessMaintenanceUnderwritingCommand> captor =
                ArgumentCaptor.forClass(AssessMaintenanceUnderwritingCommand.class);
        verify(commandService).assessMaintenance(captor.capture());
        assertEquals("tenant-1", captor.getValue().tenantId());

        // m24-06（API-06）双挂回归：契约路径补版本段后，服务端在一个版本周期内**同时挂新旧两条路径**，
        // 旧路径必须仍可路由（否则迁移期内的调用方被硬切）。仅断言状态码不足以证明命中 provider——
        // 200 只可能来自 controller，路由缺失时 MockMvc 抛的是 404 而非静默通过。
        MvcResult legacy = mockMvc.perform(post("/underwriting/api/maintenance-assessments")
                        .header("X-Tenant-ID", "tenant-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andReturn();
        assertEquals(200, legacy.getResponse().getStatus(), legacy.getResponse().getContentAsString());
        assertTrue(legacy.getResponse().getContentAsString().contains("\"code\":\"00000000\""));
    }
}
