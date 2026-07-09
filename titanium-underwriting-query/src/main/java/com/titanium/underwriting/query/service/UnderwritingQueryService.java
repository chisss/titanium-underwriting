package com.titanium.underwriting.query.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.query.result.UnderwritingQueryResult;
import com.titanium.underwriting.query.result.UnderwritingStatisticsResult;

/**
 * 核保查询服务（CQRS 读侧）
 * <p>
 * 查询由事件投影维护的读模型表 {@code t_underwriting_view}，实现真正的读写分离。 复杂查询逻辑内聚于本服务实现，应用层读入口仅表达「要查什么」。
 * </p>
 */
public interface UnderwritingQueryService {

    /**
     * 根据核保ID查询详情
     */
    UnderwritingQueryResult findById(String underwritingId, String tenantId);

    /**
     * 根据保单ID查询核保
     */
    UnderwritingQueryResult findByPolicyId(String policyId, String tenantId);

    /**
     * 根据状态分页查询
     */
    Page<UnderwritingQueryResult> findByStatus(UnderwritingEnum.UnderwritingStatus status, String tenantId,
                                               Pageable pageable);

    /**
     * 根据风险等级分页查询
     */
    Page<UnderwritingQueryResult> findByRiskLevel(UnderwritingEnum.RiskLevel riskLevel, String tenantId,
                                                  Pageable pageable);

    /**
     * 根据核保员和时间范围分页查询
     */
    Page<UnderwritingQueryResult> findByUnderwriterAndTimeRange(String underwriterId, LocalDateTime startTime,
                                                               LocalDateTime endTime, String tenantId,
                                                               Pageable pageable);

    /**
     * 多条件组合动态查询
     */
    Page<UnderwritingQueryResult> findByMultipleConditions(UnderwritingEnum.UnderwritingStatus status,
                                                           UnderwritingEnum.RiskLevel riskLevel,
                                                           UnderwritingEnum.AuditType auditType, String underwriterId,
                                                           LocalDateTime startTime, LocalDateTime endTime,
                                                           String tenantId, Pageable pageable);

    /**
     * 根据客户ID查询核保历史
     */
    List<UnderwritingQueryResult> findHistoryByCustomer(String customerId, String tenantId);

    /**
     * 查询待处理的核保任务
     */
    Page<UnderwritingQueryResult> findPendingTasks(String underwriterId, UnderwritingEnum.UnderwritingStatus status,
                                                   String tenantId, Pageable pageable);

    /**
     * 获取核保统计数据
     */
    UnderwritingStatisticsResult getStatistics(LocalDateTime startTime, LocalDateTime endTime, String tenantId);
}
