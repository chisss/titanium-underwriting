package com.titanium.underwriting.query.handler.query;

import java.util.List;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

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
import com.titanium.underwriting.query.service.UnderwritingQueryService;

import lombok.RequiredArgsConstructor;

/**
 * 核保查询处理器（CQRS 读侧 Axon 查询处理）
 * <p>
 * 薄查询处理器：接收 {@code FindXxxQuery}，委托 {@link UnderwritingQueryService} 查读模型， 返回查询结果
 * DTO（不含查询实现细节）。所有查询走独立读模型 {@code t_underwriting_view}。
 * </p>
 */
@Component
@RequiredArgsConstructor
@ProcessingGroup("underwriting-query-group")
public class UnderwritingQueryHandler {

    private final UnderwritingQueryService underwritingQueryService;

    @QueryHandler
    public UnderwritingQueryResult handle(FindUnderwritingByIdQuery query) {
        return underwritingQueryService.findById(query.underwritingId().value(), query.tenantId());
    }

    @QueryHandler
    public UnderwritingQueryResult handle(FindUnderwritingByPolicyIdQuery query) {
        return underwritingQueryService.findByPolicyId(query.policyId().value(), query.tenantId());
    }

    @QueryHandler
    public Page<UnderwritingQueryResult> handle(FindUnderwritingsByStatusQuery query) {
        return underwritingQueryService.findByStatus(query.status(), query.tenantId(), query.pageable());
    }

    @QueryHandler
    public Page<UnderwritingQueryResult> handle(FindUnderwritingsByRiskLevelQuery query) {
        return underwritingQueryService.findByRiskLevel(query.riskLevel(), query.tenantId(), query.pageable());
    }

    @QueryHandler
    public Page<UnderwritingQueryResult> handle(FindUnderwritingsByUnderwriterQuery query) {
        return underwritingQueryService.findByUnderwriterAndTimeRange(query.underwriterId(), query.startTime(),
                query.endTime(), query.tenantId(), query.pageable());
    }

    @QueryHandler
    public Page<UnderwritingQueryResult> handle(FindUnderwritingsByMultipleConditionsQuery query) {
        return underwritingQueryService.findByMultipleConditions(query.status(), query.riskLevel(), query.auditType(),
                query.underwriterId(), query.startTime(), query.endTime(), query.tenantId(), query.pageable());
    }

    @QueryHandler
    public List<UnderwritingQueryResult> handle(FindUnderwritingHistoryByCustomerQuery query) {
        return underwritingQueryService.findHistoryByCustomer(query.customerId().value(), query.tenantId());
    }

    @QueryHandler
    public Page<UnderwritingQueryResult> handle(FindPendingUnderwritingTasksQuery query) {
        return underwritingQueryService.findPendingTasks(query.underwriterId(), query.status(), query.tenantId(),
                query.pageable());
    }

    @QueryHandler
    public UnderwritingStatisticsResult handle(FindUnderwritingStatisticsQuery query) {
        return underwritingQueryService.getStatistics(query.startTime(), query.endTime(), query.tenantId());
    }
}
