package com.titanium.underwriting.infrastructure.mapper;

import java.math.BigDecimal;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.domain.aggregate.Underwriting;
import com.titanium.underwriting.domain.valueobject.CustomerId;
import com.titanium.underwriting.domain.valueobject.PolicyId;
import com.titanium.underwriting.domain.valueobject.UnderwritingAmount;
import com.titanium.underwriting.domain.valueobject.UnderwritingId;
import com.titanium.underwriting.infrastructure.entity.UnderwritingEntity;

/**
 * 核保实体映射器 将数据库实体转换为领域对象，反之亦然
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UnderwritingEntityMapper {

    /**
     * 将数据库实体转换为领域对象
     * 
     * @param entity 数据库实体
     * @return 领域对象
     */
    @Mappings({
            @Mapping(source = "underwritingId", target = "underwritingId", qualifiedByName = "stringToUnderwritingId"),
            @Mapping(source = "policyId", target = "policyId", qualifiedByName = "stringToPolicyId"),
            @Mapping(source = "customerId", target = "customerId", qualifiedByName = "stringToCustomerId"),
            @Mapping(source = "amount", target = "amount", qualifiedByName = "bigDecimalToUnderwritingAmount"),
            @Mapping(source = "status", target = "status", qualifiedByName = "stringToUnderwritingStatus") })
    Underwriting toDomain(UnderwritingEntity entity);

    /**
     * 将领域对象转换为数据库实体
     * 
     * @param underwriting 领域对象
     * @return 数据库实体
     */
    @Mappings({ @Mapping(source = "underwritingId.value", target = "underwritingId"),
            @Mapping(source = "policyId.value", target = "policyId"),
            @Mapping(source = "customerId.value", target = "customerId"),
            @Mapping(source = "amount.amount", target = "amount"),
            @Mapping(source = "status.code", target = "status") })
    UnderwritingEntity toEntity(Underwriting underwriting);

    /**
     * 将字符串转换为UnderwritingId
     * 
     * @param id 字符串ID
     * @return UnderwritingId对象
     */
    @Named("stringToUnderwritingId")
    default UnderwritingId stringToUnderwritingId(String id) {
        return UnderwritingId.of(id);
    }

    /**
     * 将字符串转换为PolicyId
     * 
     * @param id 字符串ID
     * @return PolicyId对象
     */
    @Named("stringToPolicyId")
    default PolicyId stringToPolicyId(String id) {
        return PolicyId.of(id);
    }

    /**
     * 将字符串转换为CustomerId
     * 
     * @param id 字符串ID
     * @return CustomerId对象
     */
    @Named("stringToCustomerId")
    default CustomerId stringToCustomerId(String id) {
        return CustomerId.of(id);
    }

    /**
     * 将BigDecimal转换为UnderwritingAmount
     * 
     * @param amount BigDecimal金额
     * @return UnderwritingAmount对象
     */
    @Named("bigDecimalToUnderwritingAmount")
    default UnderwritingAmount bigDecimalToUnderwritingAmount(BigDecimal amount, String currency) {
        return UnderwritingAmount.of(amount, currency);
    }

    /**
     * 将字符串转换为UnderwritingStatus
     * 
     * @param code 状态码
     * @return UnderwritingStatus枚举值
     */
    @Named("stringToUnderwritingStatus")
    default UnderwritingEnum.UnderwritingStatus stringToUnderwritingStatus(String code) {
        return UnderwritingEnum.UnderwritingStatus.fromCode(code);
    }
}
