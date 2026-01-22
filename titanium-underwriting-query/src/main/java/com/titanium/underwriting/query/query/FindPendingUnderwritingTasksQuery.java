package com.titanium.underwriting.query.query;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import org.springframework.data.domain.Pageable;

/**
 * 查询待处理的核保任务
 * 用于核保员工作台，获取需要处理的核保任务列表
 */
public record FindPendingUnderwritingTasksQuery(
        String underwriterId,
        UnderwritingEnum.UnderwritingStatus status,
        Pageable pageable,
        String tenantId
) {
}