package com.titanium.underwriting.query.handler;

import java.util.List;
import java.util.Optional;

import org.axonframework.queryhandling.QueryHandler;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.titanium.underwriting.domain.aggregate.Underwriting;
import com.titanium.underwriting.domain.repository.UnderwritingRepository;
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
import com.titanium.underwriting.query.service.UnderwritingQueryService;

/**
 * 核保查询处理器 处理所有与核保相关的查询请求 基于保险业务全生命周期核保承保阶段的业务需求设计
 */
@Component
public class UnderwritingConditionQueryHandler {
    private final UnderwritingRepository   underwritingRepository;
    private final UnderwritingQueryService underwritingQueryService;

    public UnderwritingConditionQueryHandler(UnderwritingRepository underwritingRepository,
                                             UnderwritingQueryService underwritingQueryService) {
        this.underwritingRepository = underwritingRepository;
        this.underwritingQueryService = underwritingQueryService;
    }

    // ========== 基础查询处理器 ==========

    @QueryHandler
    public Optional<Underwriting> handle(FindUnderwritingByIdQuery query) {
        return underwritingRepository.findByIdAndTenantId(query.underwritingId(), query.tenantId());
    }

    @QueryHandler
    public Optional<Underwriting> handle(FindUnderwritingByPolicyIdQuery query) {
        return underwritingRepository.findByPolicyIdAndTenantId(query.policyId(), query.tenantId());
    }

    @QueryHandler
    public Page<Underwriting> handle(FindUnderwritingsByStatusQuery query) {
        return underwritingRepository.findByStatusAndTenantId(query.status(), query.tenantId(), query.pageable());
    }

    // ========== 扩展查询处理器 ==========

    /**
     * 处理按风险等级查询核保信息
     */
    @QueryHandler
    public Page<UnderwritingQueryResult> handle(FindUnderwritingsByRiskLevelQuery query) {
        return underwritingQueryService.retriveByRiskLevelAndTenantId(query.riskLevel(), query.tenantId(),
                query.pageable());
    }

    /**
     * 处理按核保员查询核保信息
     */
    @QueryHandler
    public Page<UnderwritingQueryResult> handle(FindUnderwritingsByUnderwriterQuery query) {
        return underwritingQueryService.findByUnderwriterAndTimeRange(query.underwriterId(), query.startTime(),
                query.endTime(), query.tenantId(), query.pageable());
    }

    /**
     * 处理多条件组合查询
     */
    @QueryHandler
    public Page<UnderwritingQueryResult> handle(FindUnderwritingsByMultipleConditionsQuery query) {
        return underwritingQueryService.findByMultipleConditions(query.status(), query.riskLevel(), query.auditType(),
                query.underwriterId(), query.startTime(), query.endTime(), query.tenantId(), query.pageable());
    }

    /**
     * 处理客户核保历史查询
     */
    @QueryHandler
    public List<UnderwritingQueryResult> handle(FindUnderwritingHistoryByCustomerQuery query) {
        return underwritingQueryService.findHistoryByCustomerIdAndTenantId(query.customerId(), query.tenantId());
    }

    /**
     * 处理核保统计查询
     */
    @QueryHandler
    public UnderwritingStatisticsResult handle(FindUnderwritingStatisticsQuery query) {
        return underwritingQueryService.getStatistics(query.startTime(), query.endTime(), query.tenantId());
    }

    /**
     * 处理待处理核保任务查询
     */
    @QueryHandler
    public Page<UnderwritingQueryResult> handle(FindPendingUnderwritingTasksQuery query) {
        return underwritingQueryService.findPendingTasks(query.underwriterId(), query.status(), query.tenantId(),
                query.pageable());
    }
}
