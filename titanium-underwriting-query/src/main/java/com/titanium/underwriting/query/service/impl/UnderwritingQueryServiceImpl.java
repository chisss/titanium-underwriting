package com.titanium.underwriting.query.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.domain.aggregate.Underwriting;
import com.titanium.underwriting.domain.repository.UnderwritingQueryRepository;
import com.titanium.underwriting.domain.valueobject.CustomerId;
import com.titanium.underwriting.query.entity.UnderwritingQueryResult;
import com.titanium.underwriting.query.entity.UnderwritingStatisticsResult;
import com.titanium.underwriting.query.mapper.UnderwritingQueryMapper;
import com.titanium.underwriting.query.service.UnderwritingQueryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 核保查询服务实现类 基于保险业务全生命周期核保承保阶段的业务需求实现 符合项目规约的查询层服务实现，包含缓存优化
 * 严格遵循DDD架构：Query层负责「聚合根→QueryResult」转换
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UnderwritingQueryServiceImpl implements UnderwritingQueryService {

    private final UnderwritingQueryRepository underwritingQueryRepository;

    @Cacheable(value = "underwriting:riskLevel", key = "#riskLevel + ':' + #tenantId + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<UnderwritingQueryResult> retriveByRiskLevelAndTenantId(UnderwritingEnum.RiskLevel riskLevel,
                                                                       String tenantId, Pageable pageable) {

        log.info("根据风险等级查询核保记录: riskLevel={}, tenantId={}, page={}, size={}", riskLevel, tenantId,
                pageable.getPageNumber(), pageable.getPageSize());

        // 1. 通过Domain Repository获取聚合根
        Page<Underwriting> underwritings = underwritingQueryRepository.findByRiskLevelAndTenantId(riskLevel, tenantId,
                pageable);

        // 2. Query层负责转换为QueryResult
        return underwritings.map(UnderwritingQueryMapper.INSTANCE::toQueryResult);
    }

    @Cacheable(value = "underwriting:underwriter", key = "#underwriterId + ':' + #tenantId + ':' + #startTime + ':' + #endTime + ':' + #pageable.pageNumber")
    public Page<UnderwritingQueryResult> findByUnderwriterAndTimeRange(String underwriterId, LocalDateTime startTime,
                                                                       LocalDateTime endTime, String tenantId,
                                                                       Pageable pageable) {

        log.info("根据核保员和时间范围查询核保记录: underwriterId={}, startTime={}, endTime={}, tenantId={}", underwriterId, startTime,
                endTime, tenantId);

        // 1. 通过Domain Repository获取聚合根
        Page<Underwriting> underwritings = underwritingQueryRepository.findByUnderwriterAndTimeRange(underwriterId,
                startTime, endTime, tenantId, pageable);

        // 2. Query层负责转换为QueryResult
        return underwritings.map(UnderwritingQueryMapper.INSTANCE::toQueryResult);
    }

    public Page<UnderwritingQueryResult> findByMultipleConditions(UnderwritingEnum.UnderwritingStatus status,
                                                                  UnderwritingEnum.RiskLevel riskLevel,
                                                                  UnderwritingEnum.AuditType auditType,
                                                                  String underwriterId, LocalDateTime startTime,
                                                                  LocalDateTime endTime, String tenantId,
                                                                  Pageable pageable) {

        log.info("多条件组合查询核保记录: status={}, riskLevel={}, auditType={}, underwriterId={}, tenantId={}", status, riskLevel,
                auditType, underwriterId, tenantId);

        // 1. 通过Domain Repository获取聚合根
        Page<Underwriting> underwritings = underwritingQueryRepository.findByMultipleConditions(status, riskLevel,
                auditType, underwriterId, startTime, endTime, tenantId, pageable);

        // 2. Query层负责转换为QueryResult
        return underwritings.map(UnderwritingQueryMapper.INSTANCE::toQueryResult);
    }

    @Cacheable(value = "underwriting:customerHistory", key = "#customerId.value + ':' + #tenantId")
    public List<UnderwritingQueryResult> findHistoryByCustomerIdAndTenantId(CustomerId customerId, String tenantId) {

        log.info("查询客户核保历史记录: customerId={}, tenantId={}", customerId.toString(), tenantId);

        // 1. 通过Domain Repository获取聚合根
        List<Underwriting> underwritings = underwritingQueryRepository
                .findHistoryByCustomerIdAndTenantId(customerId.toString(), tenantId);

        // 2. Query层负责转换为QueryResult
        return UnderwritingQueryMapper.INSTANCE.toQueryResultList(underwritings);
    }

    @Cacheable(value = "underwriting:statistics", key = "#startTime + ':' + #endTime + ':' + #tenantId", unless = "#result == null")
    public UnderwritingStatisticsResult getStatistics(LocalDateTime startTime, LocalDateTime endTime, String tenantId) {

        log.info("获取核保统计数据: startTime={}, endTime={}, tenantId={}", startTime, endTime, tenantId);

        // 1. 通过Domain Repository获取原始统计数据
        List<Object[]> rawData = underwritingQueryRepository.getStatisticsRawData(startTime, endTime, tenantId);

        // 2. Query层负责转换为StatisticsResult
        return UnderwritingQueryMapper.INSTANCE.createStatisticsResult(startTime, endTime, tenantId, rawData);
    }

    public Page<UnderwritingQueryResult> findPendingTasks(String underwriterId,
                                                          UnderwritingEnum.UnderwritingStatus status, String tenantId,
                                                          Pageable pageable) {

        log.info("查询待处理核保任务: underwriterId={}, status={}, tenantId={}", underwriterId, status, tenantId);

        // 1. 通过Domain Repository获取聚合根
        Page<Underwriting> underwritings = underwritingQueryRepository.findPendingTasks(underwriterId, status, tenantId,
                pageable);

        // 2. Query层负责转换为QueryResult
        return underwritings.map(UnderwritingQueryMapper.INSTANCE::toQueryResult);
    }
}
