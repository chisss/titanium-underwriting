package com.titanium.underwriting.application.query;

import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.query.query.FindPendingUnderwritingTasksQuery;
import com.titanium.underwriting.query.query.FindUnderwritingByIdQuery;
import com.titanium.underwriting.query.query.FindUnderwritingByPolicyIdQuery;
import com.titanium.underwriting.query.query.FindUnderwritingHistoryByCustomerQuery;
import com.titanium.underwriting.query.query.FindUnderwritingStatisticsQuery;
import com.titanium.underwriting.query.query.FindUnderwritingsByMultipleConditionsQuery;
import com.titanium.underwriting.query.query.FindUnderwritingsByRiskLevelQuery;
import com.titanium.underwriting.query.query.FindUnderwritingsByStatusQuery;
import com.titanium.underwriting.query.query.FindUnderwritingsByUnderwriterQuery;
import com.titanium.underwriting.query.result.UnderwritingQueryResult;
import com.titanium.underwriting.query.result.UnderwritingStatisticsResult;
import com.titanium.underwriting.valueobject.CustomerId;
import com.titanium.underwriting.valueobject.PolicyId;
import com.titanium.underwriting.valueobject.UnderwritingId;

/**
 * 核保查询应用服务（读入口门面）
 * <p>
 * CQRS 读入口：构造 {@code FindXxxQuery} 经 {@link QueryGateway} 派发到读侧查询处理器， 返回读模型查询结果
 * DTO，不含查询实现细节，也不触碰写侧聚合。
 * </p>
 */
@Service
@Transactional(readOnly = true)
public class UnderwritingQueryAppService {

    private final QueryGateway queryGateway;

    public UnderwritingQueryAppService(QueryGateway queryGateway) {
        this.queryGateway = queryGateway;
    }

    /**
     * 根据ID查询核保详情
     */
    public UnderwritingQueryResult findUnderwritingById(UnderwritingId underwritingId, String tenantId) {
        return queryGateway
                .query(new FindUnderwritingByIdQuery(underwritingId, tenantId),
                        ResponseTypes.instanceOf(UnderwritingQueryResult.class))
                .join();
    }

    /**
     * 根据保单ID查询核保
     */
    public UnderwritingQueryResult findUnderwritingByPolicyId(PolicyId policyId, String tenantId) {
        return queryGateway
                .query(new FindUnderwritingByPolicyIdQuery(policyId, tenantId),
                        ResponseTypes.instanceOf(UnderwritingQueryResult.class))
                .join();
    }

    /**
     * 根据状态分页查询核保
     */
    public Page<UnderwritingQueryResult> findUnderwritingsByStatus(UnderwritingEnum.UnderwritingStatus status,
                                                                   Pageable pageable, String tenantId) {
        return queryGateway
                .query(new FindUnderwritingsByStatusQuery(status, pageable, tenantId),
                        ResponseTypes.instanceOf(Page.class))
                .join();
    }

    /**
     * 获取核保状态
     */
    public UnderwritingEnum.UnderwritingStatus getUnderwritingStatus(UnderwritingId underwritingId, String tenantId) {
        UnderwritingQueryResult result = findUnderwritingById(underwritingId, tenantId);
        return result != null ? result.getStatus() : null;
    }

    /**
     * 根据风险等级分页查询核保
     */
    public Page<UnderwritingQueryResult> findUnderwritingsByRiskLevel(UnderwritingEnum.RiskLevel riskLevel,
                                                                      Pageable pageable, String tenantId) {
        return queryGateway
                .query(new FindUnderwritingsByRiskLevelQuery(riskLevel, pageable, tenantId),
                        ResponseTypes.instanceOf(Page.class))
                .join();
    }

    /**
     * 根据核保员和时间范围分页查询核保
     */
    public Page<UnderwritingQueryResult> findUnderwritingsByUnderwriter(String underwriterId, LocalDateTime startTime,
                                                                        LocalDateTime endTime, Pageable pageable,
                                                                        String tenantId) {
        return queryGateway
                .query(new FindUnderwritingsByUnderwriterQuery(underwriterId, startTime, endTime, pageable, tenantId),
                        ResponseTypes.instanceOf(Page.class))
                .join();
    }

    /**
     * 多条件组合查询核保
     */
    public Page<UnderwritingQueryResult> findUnderwritingsByMultipleConditions(
            UnderwritingEnum.UnderwritingStatus status, UnderwritingEnum.RiskLevel riskLevel,
            UnderwritingEnum.AuditType auditType, String underwriterId, LocalDateTime startTime, LocalDateTime endTime,
            Pageable pageable, String tenantId) {
        return queryGateway.query(new FindUnderwritingsByMultipleConditionsQuery(status, riskLevel, auditType,
                underwriterId, startTime, endTime, pageable, tenantId), ResponseTypes.instanceOf(Page.class)).join();
    }

    /**
     * 根据客户ID查询核保历史
     */
    public List<UnderwritingQueryResult> findUnderwritingHistoryByCustomer(CustomerId customerId, String tenantId) {
        return queryGateway
                .query(new FindUnderwritingHistoryByCustomerQuery(customerId, tenantId),
                        ResponseTypes.multipleInstancesOf(UnderwritingQueryResult.class))
                .join();
    }

    /**
     * 获取核保统计数据
     */
    public UnderwritingStatisticsResult getUnderwritingStatistics(LocalDateTime startTime, LocalDateTime endTime,
                                                                  String tenantId) {
        return queryGateway
                .query(new FindUnderwritingStatisticsQuery(startTime, endTime, tenantId),
                        ResponseTypes.instanceOf(UnderwritingStatisticsResult.class))
                .join();
    }

    /**
     * 查询待处理的核保任务
     */
    public Page<UnderwritingQueryResult> findPendingUnderwritingTasks(String underwriterId,
                                                                      UnderwritingEnum.UnderwritingStatus status,
                                                                      Pageable pageable, String tenantId) {
        return queryGateway
                .query(new FindPendingUnderwritingTasksQuery(underwriterId, status, pageable, tenantId),
                        ResponseTypes.instanceOf(Page.class))
                .join();
    }
}
