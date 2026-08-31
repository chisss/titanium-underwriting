package com.titanium.underwriting.web.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import com.titanium.underwriting.api.request.AssessMaintenanceUnderwritingRequest;
import com.titanium.underwriting.api.response.MaintenanceUnderwritingResponse;
import com.titanium.underwriting.command.AssessMaintenanceUnderwritingCommand;
import com.titanium.underwriting.event.MaintenanceUnderwritingAssessedEvent;
import com.titanium.underwriting.valueobject.MaintenanceRiskFieldChange;
import com.titanium.underwriting.valueobject.MaintenanceUnderwritingConclusion;
import com.titanium.underwriting.valueobject.UnderwritingId;

/**
 * 保全核保 Web 层对象映射器（MapStruct）
 * <p>
 * 保全核保 Feign 契约与 CQRS 命令/事件之间的协议转换枢纽：{@link AssessMaintenanceUnderwritingRequest}
 * → {@link AssessMaintenanceUnderwritingCommand}（核保ID由幂等键派生，风险字段差异逐项转值对象）；
 * {@link MaintenanceUnderwritingAssessedEvent} → {@link MaintenanceUnderwritingResponse}
 * （核保ID转案件号、结论枚举转 code）。同名字段交由 MapStruct 声明式映射。
 * </p>
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MaintenanceUnderwritingWebMapper {

    /**
     * 保全核保评估请求 → 评估命令
     *
     * @param request  保全核保评估请求（api 契约）
     * @param tenantId 租户ID（请求头）
     * @return 评估命令
     */
    @Mapping(target = "underwritingId",
            expression = "java(UnderwritingId.forMaintenance(tenantId, request.idempotencyKey()))")
    @Mapping(target = "riskFieldChanges", source = "request.riskFieldChanges", qualifiedByName = "toRiskFieldChange")
    AssessMaintenanceUnderwritingCommand toCommand(AssessMaintenanceUnderwritingRequest request, String tenantId);

    /**
     * 评估事件 → 对外响应（Provider 用）
     *
     * @param event 保全核保评估事件
     * @return 保全核保风险结论响应
     */
    @Mapping(target = "underwritingCaseId", source = "underwritingId", qualifiedByName = "underwritingIdValue")
    @Mapping(target = "conclusion", source = "conclusion", qualifiedByName = "conclusionName")
    MaintenanceUnderwritingResponse toResponse(MaintenanceUnderwritingAssessedEvent event);

    /** 契约风险字段差异 → 域值对象（逐项转换） */
    @Named("toRiskFieldChange")
    default MaintenanceRiskFieldChange toRiskFieldChange(
            AssessMaintenanceUnderwritingRequest.RiskFieldChangeRequest change) {
        return new MaintenanceRiskFieldChange(change.objectId(), change.fieldCode(), change.dataType(),
                change.beforeValue(), change.proposedValue(), change.changeTypeCode());
    }

    /** 核保ID值对象 → 案件号字符串（空安全） */
    @Named("underwritingIdValue")
    default String underwritingIdValue(UnderwritingId id) {
        return id == null ? null : id.value();
    }

    /** 保全核保结论枚举 → code 字符串（空安全） */
    @Named("conclusionName")
    default String conclusionName(MaintenanceUnderwritingConclusion conclusion) {
        return conclusion == null ? null : conclusion.name();
    }
}
