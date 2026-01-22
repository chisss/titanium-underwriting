package com.titanium.underwriting.query.query;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import org.springframework.data.domain.Pageable;

/**
 * 根据状态查询核保信息列表
 */
public record FindUnderwritingsByStatusQuery(UnderwritingEnum.UnderwritingStatus status, Pageable pageable,
                                             String tenantId) {
}