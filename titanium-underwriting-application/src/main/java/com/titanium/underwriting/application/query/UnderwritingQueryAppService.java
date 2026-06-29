package com.titanium.underwriting.application.query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.axonframework.queryhandling.QueryGateway;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.domain.aggregate.Underwriting;
import com.titanium.underwriting.domain.valueobject.CustomerId;
import com.titanium.underwriting.domain.valueobject.PolicyId;
import com.titanium.underwriting.domain.valueobject.UnderwritingId;
import com.titanium.underwriting.query.entity.UnderwritingQueryResult;
import com.titanium.underwriting.query.entity.UnderwritingStatisticsResult;
import com.titanium.underwriting.query.query.FindPendingUnderwritingTasksQuery;
import com.titanium.underwriting.query.query.FindUnderwritingByIdQuery;
import com.titanium.underwriting.query.query.FindUnderwritingByPolicyIdQuery;
import com.titanium.underwriting.query.query.FindUnderwritingHistoryByCustomerQuery;
import com.titanium.underwriting.query.query.FindUnderwritingStatisticsQuery;
import com.titanium.underwriting.query.query.FindUnderwritingsByMultipleConditionsQuery;
import com.titanium.underwriting.query.query.FindUnderwritingsByRiskLevelQuery;
import com.titanium.underwriting.query.query.FindUnderwritingsByStatusQuery;
import com.titanium.underwriting.query.query.FindUnderwritingsByUnderwriterQuery;

/**
 * 核保查询服务 基于保险业务全生命周期核保承保阶段的业务需求扩展
 */
@Service
@Transactional(readOnly = true)
public class UnderwritingQueryAppService {

    private final QueryGateway queryGateway;

    public UnderwritingQueryAppService(QueryGateway queryGateway) {
        this.queryGateway = queryGateway;
    }

    // ========== 基础查询方法 ==========

    /**
     * 根据ID查询核保
     *
     * @param underwritingId 核保ID
     * @param tenantId 租户ID
     * @return 核保对象
     */
    public Optional<Underwriting> findUnderwritingById(UnderwritingId underwritingId, String tenantId) {
        return queryGateway.query(new FindUnderwritingByIdQuery(underwritingId, tenantId), Optional.class).join();
    }

    /**
     * 根据保单ID查询核保
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 核保对象
     */
    public Optional<Underwriting> findUnderwritingByPolicyId(PolicyId policyId, String tenantId) {
        return queryGateway.query(new FindUnderwritingByPolicyIdQuery(policyId, tenantId), Optional.class).join();
    }

    /**
     * 根据状态查询核保列表
     *
     * @param status 核保状态
     * @param pageable 分页信息
     * @param tenantId 租户ID
     * @return 核保对象列表
     */
    public Page<Underwriting> findUnderwritingsByStatus(UnderwritingEnum.UnderwritingStatus status, Pageable pageable,
                                                        String tenantId) {
        return queryGateway.query(new FindUnderwritingsByStatusQuery(status, pageable, tenantId), Page.class).join();
    }

    /**
     * 获取核保状态
     *
     * @param underwritingId 核保ID
     * @param tenantId 租户ID
     * @return 核保状态
     */
    public UnderwritingEnum.UnderwritingStatus getUnderwritingStatus(UnderwritingId underwritingId, String tenantId) {
        Optional<Underwriting> underwriting = findUnderwritingById(underwritingId, tenantId);
        return underwriting.map(Underwriting::getStatus).orElse(null);
    }

    // ========== 扩展查询方法 ==========

    /**
     * 根据风险等级查询核保信息
     *
     * @param riskLevel 风险等级
     * @param pageable 分页信息
     * @param tenantId 租户ID
     * @return 核保查询结果列表
     */
    public Page<UnderwritingQueryResult> findUnderwritingsByRiskLevel(UnderwritingEnum.RiskLevel riskLevel,
                                                                      Pageable pageable, String tenantId) {
        return queryGateway.query(new FindUnderwritingsByRiskLevelQuery(riskLevel, pageable, tenantId), Page.class)
                .join();
    }

    /**
     * 根据核保员和时间范围查询核保信息
     *
     * @param underwriterId 核保员ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param pageable 分页信息
     * @param tenantId 租户ID
     * @return 核保查询结果列表
     */
    public Page<UnderwritingQueryResult> findUnderwritingsByUnderwriter(String underwriterId, LocalDateTime startTime,
                                                                        LocalDateTime endTime, Pageable pageable,
                                                                        String tenantId) {
        return queryGateway
                .query(new FindUnderwritingsByUnderwriterQuery(underwriterId, startTime, endTime, pageable, tenantId),
                        Page.class)
                .join();
    }

    /**
     * 多条件组合查询核保信息
     *
     * @param status 核保状态
     * @param riskLevel 风险等级
     * @param auditType 核保类型
     * @param underwriterId 核保员ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param pageable 分页信息
     * @param tenantId 租户ID
     * @return 核保查询结果列表
     */
    public Page<UnderwritingQueryResult> findUnderwritingsByMultipleConditions(UnderwritingEnum.UnderwritingStatus status,
                                                                               UnderwritingEnum.RiskLevel riskLevel,
                                                                               UnderwritingEnum.AuditType auditType,
                                                                               String underwriterId,
                                                                               LocalDateTime startTime,
                                                                               LocalDateTime endTime, Pageable pageable,
                                                                               String tenantId) {
        return queryGateway.query(new FindUnderwritingsByMultipleConditionsQuery(status, riskLevel, auditType,
                underwriterId, startTime, endTime, pageable, tenantId), Page.class).join();
    }

    /**
     * 根据客户ID查询核保历史记录
     *
     * @param customerId 客户ID
     * @param tenantId 租户ID
     * @return 核保历史记录列表
     */
    public List<UnderwritingQueryResult> findUnderwritingHistoryByCustomer(CustomerId customerId, String tenantId) {
        return queryGateway.query(new FindUnderwritingHistoryByCustomerQuery(customerId, tenantId), List.class).join();
    }

    /**
     * 获取核保统计数据
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param tenantId 租户ID
     * @return 核保统计结果
     */
    public UnderwritingStatisticsResult getUnderwritingStatistics(LocalDateTime startTime, LocalDateTime endTime,
                                                                  String tenantId) {
        return queryGateway.query(new FindUnderwritingStatisticsQuery(startTime, endTime, tenantId),
                UnderwritingStatisticsResult.class).join();
    }

    /**
     * 查询待处理的核保任务
     *
     * @param underwriterId 核保员ID
     * @param status 核保状态
     * @param pageable 分页信息
     * @param tenantId 租户ID
     * @return 待处理核保任务列表
     */
    public Page<UnderwritingQueryResult> findPendingUnderwritingTasks(String underwriterId,
                                                                      UnderwritingEnum.UnderwritingStatus status,
                                                                      Pageable pageable, String tenantId) {
        return queryGateway
                .query(new FindPendingUnderwritingTasksQuery(underwriterId, status, pageable, tenantId), Page.class)
                .join();
    }
}
