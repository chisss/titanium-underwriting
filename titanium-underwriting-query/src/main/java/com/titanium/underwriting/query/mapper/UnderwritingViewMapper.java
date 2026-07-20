package com.titanium.underwriting.query.mapper;

import java.math.BigDecimal;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import com.titanium.underwriting.event.UnderwritingCreatedEvent;
import com.titanium.underwriting.query.view.UnderwritingView;
import com.titanium.underwriting.valueobject.CustomerId;
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
}
