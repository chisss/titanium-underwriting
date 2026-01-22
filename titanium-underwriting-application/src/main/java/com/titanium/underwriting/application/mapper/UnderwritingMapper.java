package com.titanium.underwriting.application.mapper;

import com.titanium.underwriting.api.dto.UnderwritingDTO;
import com.titanium.underwriting.domain.aggregate.Underwriting;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.ReportingPolicy;

/**
 * 核保领域对象到DTO的映射器
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UnderwritingMapper {

    @Mappings({
            @Mapping(source = "underwritingId.value", target = "underwritingId"),
            @Mapping(source = "policyId.value", target = "policyId"),
            @Mapping(source = "customerId.value", target = "customerId"),
            @Mapping(source = "amount.amount", target = "amount")
    })
    UnderwritingDTO toDTO(Underwriting underwriting);
}