package com.titanium.underwriting.query.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.domain.valueobject.CustomerId;
import com.titanium.underwriting.query.entity.UnderwritingQueryResult;
import com.titanium.underwriting.query.entity.UnderwritingStatisticsResult;

/**
 * 核保查询服务接口 定义复杂查询业务逻辑的接口规范
 * 偏向 “业务语义”，retrieve = 检索（强调业务逻辑），fetch = 获取（简单查询）
 */
public interface UnderwritingQueryService {

    /**
     * 根据风险等级查询核保信息
     */
    Page<UnderwritingQueryResult> retriveByRiskLevelAndTenantId(UnderwritingEnum.RiskLevel riskLevel, String tenantId,
                                                             Pageable pageable);

    /**
     * 根据核保员和时间范围查询核保信息
     */
    Page<UnderwritingQueryResult> findByUnderwriterAndTimeRange(String underwriterId, LocalDateTime startTime,
                                                                LocalDateTime endTime, String tenantId,
                                                                Pageable pageable);

    /**
     * 多条件组合查询核保信息
     */
    Page<UnderwritingQueryResult> findByMultipleConditions(UnderwritingEnum.UnderwritingStatus status,
                                                           UnderwritingEnum.RiskLevel riskLevel,
                                                           UnderwritingEnum.AuditType auditType, String underwriterId,
                                                           LocalDateTime startTime, LocalDateTime endTime,
                                                           String tenantId, Pageable pageable);

    /**
     * 根据客户ID查询核保历史记录
     */
    List<UnderwritingQueryResult> findHistoryByCustomerIdAndTenantId(CustomerId customerId, String tenantId);

    /**
     * 获取核保统计数据
     */
    UnderwritingStatisticsResult getStatistics(LocalDateTime startTime, LocalDateTime endTime, String tenantId);

    /**
     * 查询待处理的核保任务
     */
    Page<UnderwritingQueryResult> findPendingTasks(String underwriterId, UnderwritingEnum.UnderwritingStatus status,
                                                   String tenantId, Pageable pageable);
}
