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
import com.titanium.underwriting.query.query.FindUnderwritingByIdQuery;
import com.titanium.underwriting.query.query.FindUnderwritingByPolicyIdQuery;
import com.titanium.underwriting.query.query.FindUnderwritingHistoryByCustomerQuery;
import com.titanium.underwriting.query.query.FindUnderwritingStatisticsQuery;
import com.titanium.underwriting.query.result.UnderwritingQueryResult;
import com.titanium.underwriting.query.result.UnderwritingStatisticsResult;
import com.titanium.underwriting.query.service.UnderwritingQueryService;
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
    private final UnderwritingQueryService underwritingQueryService;

    public UnderwritingQueryAppService(QueryGateway queryGateway,
                                       UnderwritingQueryService underwritingQueryService) {
        this.queryGateway = queryGateway;
        this.underwritingQueryService = underwritingQueryService;
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
        // 分页查询直调读侧服务，绕过 Axon 查询总线（InstanceResponseType 无法匹配 Page 返回）
        return underwritingQueryService.findByStatus(status, tenantId, pageable);
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
        // 分页查询直调读侧服务，绕过 Axon 查询总线（InstanceResponseType 无法匹配 Page 返回）
        return underwritingQueryService.findByRiskLevel(riskLevel, tenantId, pageable);
    }

    /**
     * 根据核保员和时间范围分页查询核保
     */
    public Page<UnderwritingQueryResult> findUnderwritingsByUnderwriter(String underwriterId, LocalDateTime startTime,
                                                                        LocalDateTime endTime, Pageable pageable,
                                                                        String tenantId) {
        // 分页查询直调读侧服务，绕过 Axon 查询总线（InstanceResponseType 无法匹配 Page 返回）
        return underwritingQueryService.findByUnderwriterAndTimeRange(underwriterId, startTime, endTime, tenantId,
                pageable);
    }

    /**
     * 多条件组合查询核保
     */
    public Page<UnderwritingQueryResult> findUnderwritingsByMultipleConditions(
            UnderwritingEnum.UnderwritingStatus status, UnderwritingEnum.RiskLevel riskLevel,
            UnderwritingEnum.AuditType auditType, String underwriterId, LocalDateTime startTime, LocalDateTime endTime,
            Pageable pageable, String tenantId) {
        // 分页查询直调读侧服务，绕过 Axon 查询总线（InstanceResponseType 无法匹配 Page 返回）
        return underwritingQueryService.findByMultipleConditions(status, riskLevel, auditType, underwriterId, startTime,
                endTime, tenantId, pageable);
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
        // 分页查询直调读侧服务，绕过 Axon 查询总线（InstanceResponseType 无法匹配 Page 返回）
        return underwritingQueryService.findPendingTasks(underwriterId, status, tenantId, pageable);
    }
}
