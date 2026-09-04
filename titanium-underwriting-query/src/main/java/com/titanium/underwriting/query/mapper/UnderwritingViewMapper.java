package com.titanium.underwriting.query.mapper;

import java.math.BigDecimal;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.event.UnderwritingCreatedEvent;
import com.titanium.underwriting.event.UnderwritingDecidedEvent;
import com.titanium.underwriting.event.UnderwritingStatusChangedEvent;
import com.titanium.underwriting.query.view.UnderwritingView;
import com.titanium.underwriting.valueobject.CustomerId;
import com.titanium.underwriting.valueobject.ExtraPremium;
import com.titanium.underwriting.valueobject.PolicyId;
import com.titanium.underwriting.valueobject.UnderwritingAmount;
import com.titanium.underwriting.valueobject.UnderwritingId;

/**
 * 核保读模型投影映射器（MapStruct，事件 → 读模型字段拷贝）
 * <p>
 * 承担「新建型」投影 {@link UnderwritingCreatedEvent} → {@link UnderwritingView} 的字段映射，取代投影处理器中
 * 逐字段 set。采用 {@link MappingTarget} 就地更新既有/新建 View 实例，保留投影的 upsert 语义；
 * {@link NullValuePropertyMappingStrategy#IGNORE} 确保事件缺省字段不覆盖 View 既有值（等价于原 handler
 * 中 {@code if (event.amount() != null)} 之类的空值守卫）。
 * </p>
 * <p>
 * <b>职责边界</b>：仅做纯字段/值对象结构翻译（{@link UnderwritingId}/{@link PolicyId}/{@link CustomerId}
 * 拆包为 String，{@link UnderwritingAmount} 取金额）与初始状态常量（创建即 {@code PENDING}）。审计时间戳
 * （createTime 仅首次、updateTime 每次 now）含运行时副作用与"仅首次"语义，仍由投影处理器控制，不下沉映射器，
 * 故此处对应目标字段 {@code ignore}。乐观锁 version 由 JPA 维护，事件无该字段，经
 * {@link ReportingPolicy#IGNORE} 放行未映射目标。
 * </p>
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UnderwritingViewMapper {

    /**
     * 核保创建事件 → 核保读模型（就地 upsert）：值对象拆包为标识串/金额，初始状态置 PENDING，
     * updatedBy 复用创建人；审计时间戳由处理器 stampAuditTime 控制，此处忽略。
     */
    @Mapping(target = "underwritingId", source = "underwritingId", qualifiedByName = "underwritingIdValue")
    @Mapping(target = "policyId", source = "policyId", qualifiedByName = "policyIdValue")
    @Mapping(target = "customerId", source = "customerId", qualifiedByName = "customerIdValue")
    @Mapping(target = "amount", source = "amount", qualifiedByName = "underwritingAmountValue")
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "updatedBy", source = "createdBy")
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    void applyCreated(@MappingTarget UnderwritingView view, UnderwritingCreatedEvent event);

    /**
     * 核保状态变更事件 → 读模型（就地更新）：状态与更新人直映；拒保/退回类状态写入拒保原因、
     * 其余状态写入审核意见（互斥逻辑由事件级 {@code @Named} 空安全转换承载，IGNORE 保持另一侧既有值）。
     */
    @Mapping(target = "underwritingId", ignore = true)
    @Mapping(target = "status", source = "newStatus")
    @Mapping(target = "updatedBy", source = "changedBy")
    @Mapping(target = "rejectReason", source = "event", qualifiedByName = "rejectReasonOfStatusChanged")
    @Mapping(target = "reviewComments", source = "event", qualifiedByName = "reviewCommentsOfStatusChanged")
    void applyStatusChanged(@MappingTarget UnderwritingView view, UnderwritingStatusChangedEvent event);

    /**
     * 核保决策事件 → 读模型（就地更新）：风险等级/结论/方式/评分直映，状态与更新人改名映射；
     * 结构化加费三字段在加费明细为空时经空安全转换返回 null，由 IGNORE 保持既有值。
     * 拒保/转人工状态由事件级 {@code @Named} 转换把规则引擎原因（dev-505 {@code reason}）写入
     * 拒保原因/审核意见，其余状态返回 null（IGNORE 保持既有值）。
     */
    @Mapping(target = "underwritingId", ignore = true)
    @Mapping(target = "policyId", ignore = true)
    @Mapping(target = "status", source = "newStatus")
    @Mapping(target = "updatedBy", source = "decidedBy")
    @Mapping(target = "rejectReason", source = "event", qualifiedByName = "rejectReasonOfDecided")
    @Mapping(target = "reviewComments", source = "event", qualifiedByName = "reviewCommentsOfDecided")
    @Mapping(target = "extraPremiumType", source = "extraPremium", qualifiedByName = "extraPremiumTypeCode")
    @Mapping(target = "extraPremiumRatio", source = "extraPremium", qualifiedByName = "extraPremiumRatioValue")
    @Mapping(target = "extraPremiumFixedAmount", source = "extraPremium",
            qualifiedByName = "extraPremiumFixedAmountValue")
    @Mapping(target = "extraPremiumReason", source = "extraPremium", qualifiedByName = "extraPremiumReasonValue")
    void applyDecided(@MappingTarget UnderwritingView view, UnderwritingDecidedEvent event);

    /** 核保标识值对象 → 标识串（空安全） */
    @Named("underwritingIdValue")
    default String underwritingIdValue(UnderwritingId underwritingId) {
        return underwritingId != null ? underwritingId.value() : null;
    }

    /** 保单标识值对象 → 标识串（空安全） */
    @Named("policyIdValue")
    default String policyIdValue(PolicyId policyId) {
        return policyId != null ? policyId.value() : null;
    }

    /** 客户标识值对象 → 标识串（空安全） */
    @Named("customerIdValue")
    default String customerIdValue(CustomerId customerId) {
        return customerId != null ? customerId.value() : null;
    }

    /** 核保金额值对象 → 金额数值（空安全） */
    @Named("underwritingAmountValue")
    default BigDecimal underwritingAmountValue(UnderwritingAmount amount) {
        return amount != null ? amount.amount() : null;
    }

    /** 拒保/撤单状态 → 拒保原因，其余状态返回 null（IGNORE 保持既有值） */
    @Named("rejectReasonOfStatusChanged")
    default String rejectReasonOfStatusChanged(UnderwritingStatusChangedEvent event) {
        if (event.newStatus() == UnderwritingEnum.UnderwritingStatus.REJECTED
                || event.newStatus() == UnderwritingEnum.UnderwritingStatus.DECLINED) {
            return event.reason();
        }
        return null;
    }

    /** 非拒保状态 → 审核意见，拒保/撤单状态返回 null（IGNORE 保持既有值） */
    @Named("reviewCommentsOfStatusChanged")
    default String reviewCommentsOfStatusChanged(UnderwritingStatusChangedEvent event) {
        if (event.newStatus() == UnderwritingEnum.UnderwritingStatus.REJECTED
                || event.newStatus() == UnderwritingEnum.UnderwritingStatus.DECLINED) {
            return null;
        }
        return event.reason();
    }

    /** 决策拒保 → 拒保原因（规则引擎原因），其余状态返回 null（IGNORE 保持既有值） */
    @Named("rejectReasonOfDecided")
    default String rejectReasonOfDecided(UnderwritingDecidedEvent event) {
        if (event.newStatus() == UnderwritingEnum.UnderwritingStatus.REJECTED
                || event.newStatus() == UnderwritingEnum.UnderwritingStatus.DECLINED) {
            return event.reason();
        }
        return null;
    }

    /** 决策转人工 → 审核意见（规则引擎原因），其余状态返回 null（IGNORE 保持既有值） */
    @Named("reviewCommentsOfDecided")
    default String reviewCommentsOfDecided(UnderwritingDecidedEvent event) {
        if (event.newStatus() == UnderwritingEnum.UnderwritingStatus.MANUAL_REVIEW) {
            return event.reason();
        }
        return null;
    }

    /** 加费明细 → 加费类型 code（空安全） */
    @Named("extraPremiumTypeCode")
    default String extraPremiumTypeCode(ExtraPremium extraPremium) {
        if (extraPremium == null || extraPremium.type() == null) {
            return null;
        }
        return extraPremium.type().getCode();
    }

    /** 加费明细 → 加费率（空安全） */
    @Named("extraPremiumRatioValue")
    default BigDecimal extraPremiumRatioValue(ExtraPremium extraPremium) {
        return extraPremium == null ? null : extraPremium.ratio();
    }

    /** 加费明细 → 固定加费额（空安全） */
    @Named("extraPremiumFixedAmountValue")
    default BigDecimal extraPremiumFixedAmountValue(ExtraPremium extraPremium) {
        return extraPremium == null ? null : extraPremium.fixedAmount();
    }

    /** 加费明细 → 加费原因（空安全） */
    @Named("extraPremiumReasonValue")
    default String extraPremiumReasonValue(ExtraPremium extraPremium) {
        return extraPremium == null ? null : extraPremium.reason();
    }
}
