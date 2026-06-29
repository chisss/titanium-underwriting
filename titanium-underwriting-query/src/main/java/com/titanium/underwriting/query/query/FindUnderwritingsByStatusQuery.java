package com.titanium.underwriting.query.query;

import org.springframework.data.domain.Pageable;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;

/**
 * 根据状态查询核保信息列表
 */
public record FindUnderwritingsByStatusQuery(UnderwritingEnum.UnderwritingStatus status, Pageable pageable,
                                             String tenantId) {
}
