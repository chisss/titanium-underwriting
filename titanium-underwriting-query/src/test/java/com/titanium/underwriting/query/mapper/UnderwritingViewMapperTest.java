package com.titanium.underwriting.query.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.underwriting.MaintenanceUnderwritingConclusion;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.common.enums.ProductConfigSource;
import com.titanium.underwriting.event.MaintenanceUnderwritingAssessedEvent;
import com.titanium.underwriting.event.UnderwritingDecidedEvent;
import com.titanium.underwriting.event.UnderwritingInputSubmittedEvent;
import com.titanium.underwriting.query.view.UnderwritingView;
import com.titanium.underwriting.valueobject.OccupationInfo;
import com.titanium.underwriting.valueobject.PolicyId;
import com.titanium.underwriting.valueobject.UnderwritingId;
import com.titanium.underwriting.valueobject.UnderwritingInput;

/**
 * 核保读模型投影映射器测试（m7-1001 新增的两条投影链路）。
 * <p>
 * 直接断言生成实现 {@link UnderwritingViewMapperImpl} 的真实映射结果，而非只测投影处理器：映射器
 * {@code unmappedTargetPolicy} 为 {@code IGNORE}，映射源写错时 MapStruct 只会**静默漏字段**、不报编译错，
 * 读模型该列将永远为空（即「写通读不通」的复发形态）。保全核保与险种输入此前**完全无投影**，
 * 是最容易再次静默断链的两处，故逐列断言。
 * </p>
 */
class UnderwritingViewMapperTest {

    private final UnderwritingViewMapper mapper = new UnderwritingViewMapperImpl();

    @Test
    void applyInputSubmittedMarksSubmittedAndCarriesAggregatedRiskScore() {
        UnderwritingView view = new UnderwritingView();
        UnderwritingInput input = UnderwritingInput.builder()
                .occupationInfo(new OccupationInfo("高空作业", 5, new BigDecimal("1.5")))
                .build();

        mapper.applyInputSubmitted(view, new UnderwritingInputSubmittedEvent(new UnderwritingId("UW-001"), input,
                LocalDateTime.parse("2026-09-14T10:00:00"), "uw01", "TENANT-001"));

        assertTrue(view.getInputSubmitted(), "已提交有效输入必须置位，否则工作台筛不出待决策件");
        assertEquals(input.aggregateRiskScore(), view.getInputRiskScore(), "决策前预览的评分须与输入容器自评口径一致");
        assertEquals("uw01", view.getUpdatedBy());
    }

    @Test
    void applyInputSubmittedKeepsFlagFalseWhenContainerCarriesNoItem() {
        UnderwritingView view = new UnderwritingView();

        mapper.applyInputSubmitted(view, new UnderwritingInputSubmittedEvent(new UnderwritingId("UW-002"),
                UnderwritingInput.builder().build(), LocalDateTime.now(), "uw01", "TENANT-001"));

        assertFalse(view.getInputSubmitted(), "空容器不构成有效输入，标志位不得误置为 true");
    }

    @Test
    void applyMaintenanceAssessedUpsertsMaintenanceColumnsAndMapsStatusFromConclusion() {
        UnderwritingView view = new UnderwritingView();

        mapper.applyMaintenanceAssessed(view, assessedEvent(MaintenanceUnderwritingConclusion.CONDITIONAL_APPROVED,
                List.of("加费20%", "除外既往症")));

        assertEquals("UW-003", view.getUnderwritingId());
        assertEquals("POL-003", view.getPolicyId());
        assertEquals(UnderwritingEnum.UnderwritingType.ENDORSEMENT, view.getUnderwritingType());
        assertEquals(UnderwritingEnum.UnderwritingStatus.RATED, view.getStatus(), "条件承保须映射为加费承保");
        assertEquals(MaintenanceUnderwritingConclusion.CONDITIONAL_APPROVED, view.getMaintenanceConclusion());
        assertEquals("MT-003", view.getMaintenanceId());
        assertEquals("ITEM-003", view.getMaintenanceItemCode());
        assertEquals("条件承保", view.getMaintenanceSummary());
        assertEquals("[\"加费20%\",\"除外既往症\"]", view.getMaintenanceAdditionalConditionsJson());
        assertEquals(LocalDateTime.parse("2026-09-14T09:00:00"), view.getMaintenanceCompletedAt());
        assertEquals("uw09", view.getCreatedBy());
        assertEquals(LocalDateTime.parse("2026-09-14T09:30:00"), view.getCreatedAt());
    }

    @Test
    void applyMaintenanceAssessedMapsRejectedConclusionToDeclinedSoWriteAndReadShareOneScale() {
        UnderwritingView view = new UnderwritingView();

        mapper.applyMaintenanceAssessed(view, assessedEvent(MaintenanceUnderwritingConclusion.REJECTED, List.of()));

        // 与写侧事件回放共用 MaintenanceUnderwritingConclusion#toUnderwritingStatus，防止两侧各写一份 switch 漂移
        assertEquals(UnderwritingEnum.UnderwritingStatus.DECLINED, view.getStatus());
    }

    @Test
    void applyMaintenanceAssessedLeavesConditionsJsonNullWhenNoConditions() {
        UnderwritingView view = new UnderwritingView();

        mapper.applyMaintenanceAssessed(view, assessedEvent(MaintenanceUnderwritingConclusion.APPROVED, List.of()));

        assertNull(view.getMaintenanceAdditionalConditionsJson(), "无条件时不得存 \"[]\"，避免与「无条件」语义歧义");
        assertEquals(LocalDateTime.parse("2026-09-14T09:00:00"), view.getMaintenanceCompletedAt(),
                "完成时间独立于附加条件，语义为「已完成评估」而非「有条件」");
    }

    private MaintenanceUnderwritingAssessedEvent assessedEvent(MaintenanceUnderwritingConclusion conclusion,
            List<String> conditions) {
        return new MaintenanceUnderwritingAssessedEvent(new UnderwritingId("UW-003"), "TENANT-001", "MT-003", "POL-003",
                3L, "ITEM-003", "IDEM-003", "HASH-003", "RULE-V1", "MODEL-V1", conclusion, conditions, "条件承保",
                LocalDateTime.parse("2026-09-14T09:00:00"), LocalDateTime.parse("2026-09-14T09:30:00"), "uw09");
    }

    /**
     * D-501-44：决策时间必须落读模型 —— 该时间此前只服务于聚合回放（{@code Underwriting.updateTime}），
     * 读模型无列承接，致列表「核保完成时间」与详情「处理耗时」恒显示 {@code -}。
     */
    @Test
    void applyDecidedCarriesDecisionTimeIntoCompletedTimeColumn() {
        UnderwritingView view = new UnderwritingView();

        mapper.applyDecided(view, new UnderwritingDecidedEvent(new UnderwritingId("UW-004"), new PolicyId("POL-004"),
                UnderwritingEnum.RiskLevel.STANDARD, UnderwritingEnum.ConclusionType.ACCEPT,
                UnderwritingEnum.AuditType.AUTOMATIC, UnderwritingEnum.UnderwritingStatus.PENDING,
                UnderwritingEnum.UnderwritingStatus.STANDARD, 0, null,
                LocalDateTime.parse("2026-09-16T11:22:33"), "uw04", "TENANT-001", null,
                ProductConfigSource.NOT_CONFIGURED));

        assertEquals(LocalDateTime.parse("2026-09-16T11:22:33"), view.getUnderwritingCompletedTime(),
                "决策时间必须落读模型，否则「核保完成时间」永久为空");
        assertEquals(UnderwritingEnum.UnderwritingStatus.STANDARD, view.getStatus());
        assertEquals("uw04", view.getUpdatedBy());
    }
}
