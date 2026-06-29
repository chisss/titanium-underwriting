package com.titanium.underwriting.infrastructure.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.titanium.underwriting.domain.aggregate.Underwriting;
import com.titanium.underwriting.domain.valueobject.CustomerId;
import com.titanium.underwriting.domain.valueobject.PolicyId;
import com.titanium.underwriting.domain.valueobject.UnderwritingAmount;
import com.titanium.underwriting.domain.valueobject.UnderwritingId;
import com.titanium.underwriting.infrastructure.entity.UnderwritingQueryEntity;

/**
 * 核保基础设施层映射器
 * 负责「JPA Entity ↔ 聚合根」转换
 * 符合DDD架构：Infrastructure层负责「DO→聚合根」转换
 * 严格遵循项目规约第6条：涉及到实体跨层转换时，必须使用Mapper类（MapStruct）进行转换
 */
@Mapper()
public interface UnderwritingInfrastructureMapper {

    UnderwritingInfrastructureMapper INSTANCE = Mappers.getMapper(UnderwritingInfrastructureMapper.class);

    // ========== JPA Entity ↔ 聚合根转换 ==========

    /**
     * JPA实体转换为聚合根
     * Infrastructure层核心职责：DO→聚合根
     */
    @Mapping(source = "underwritingId", target = "underwritingId", qualifiedByName = "stringToUnderwritingId")
    @Mapping(source = "policyId", target = "policyId", qualifiedByName = "stringToPolicyId")
    @Mapping(source = "customerId", target = "customerId", qualifiedByName = "stringToCustomerId")
    @Mapping(source = "amount", target = "amount", qualifiedByName = "bigDecimalToUnderwritingAmount")
    Underwriting toAggregate(UnderwritingQueryEntity jpaEntity);

    /**
     * 聚合根转换为JPA实体
     * 用于保存操作
     */
    @Mapping(source = "underwritingId.value", target = "underwritingId")
    @Mapping(source = "policyId.value", target = "policyId")
    @Mapping(source = "customerId.value", target = "customerId")
    @Mapping(source = "amount.amount", target = "amount")
    UnderwritingQueryEntity toJpaEntity(Underwriting aggregate);

    // ========== 值对象转换方法 ==========

    @org.mapstruct.Named("stringToUnderwritingId")
    default UnderwritingId stringToUnderwritingId(String value) {
        return value != null ? new UnderwritingId(value) : null;
    }

    @org.mapstruct.Named("stringToPolicyId")
    default PolicyId stringToPolicyId(String value) {
        return value != null ? new PolicyId(value) : null;
    }

    @org.mapstruct.Named("stringToCustomerId")
    default CustomerId stringToCustomerId(String value) {
        return value != null ? new CustomerId(value) : null;
    }

    @org.mapstruct.Named("bigDecimalToUnderwritingAmount")
    default UnderwritingAmount bigDecimalToUnderwritingAmount(java.math.BigDecimal value) {
        return value != null ? new UnderwritingAmount(value) : null;
    }
}
