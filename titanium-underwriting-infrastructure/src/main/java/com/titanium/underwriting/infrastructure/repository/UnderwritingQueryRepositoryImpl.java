package com.titanium.underwriting.infrastructure.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.domain.aggregate.Underwriting;
import com.titanium.underwriting.domain.repository.UnderwritingQueryRepository;
import com.titanium.underwriting.infrastructure.entity.UnderwritingQueryEntity;
import com.titanium.underwriting.infrastructure.mapper.UnderwritingInfrastructureMapper;
import com.titanium.underwriting.infrastructure.repository.jpa.UnderwritingQueryJpaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 核保查询仓储实现类 - Infrastructure层 实现Domain层定义的UnderwritingQueryRepository接口 负责「JPA
 * Entity → 聚合根」转换，符合DDD架构 严格遵循「返回方向：数据库 → infra → query → application → 外部」
 */
@Repository
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UnderwritingQueryRepositoryImpl implements UnderwritingQueryRepository {

    private final UnderwritingQueryJpaRepository   jpaRepository;
    private final UnderwritingInfrastructureMapper mapper;

    // ========== 基础CRUD操作 ==========

    @Override
    @Transactional
    public Underwriting save(Underwriting underwriting) {
        log.debug("保存核保聚合根: {}", underwriting.getUnderwritingId());
        UnderwritingQueryEntity jpaEntity = mapper.toJpaEntity(underwriting);
        UnderwritingQueryEntity savedEntity = jpaRepository.save(jpaEntity);
        return mapper.toAggregate(savedEntity);
    }

    @Override
    public Optional<Underwriting> findByUnderwritingIdAndTenantId(String underwritingId, String tenantId) {
        log.debug("根据核保ID和租户ID查询: underwritingId={}, tenantId={}", underwritingId, tenantId);
        return jpaRepository.findByUnderwritingIdAndTenantId(underwritingId, tenantId).map(mapper::toAggregate);
    }

    // ========== 业务查询方法 ==========

    @Override
    public Page<Underwriting> findByRiskLevelAndTenantId(UnderwritingEnum.RiskLevel riskLevel, String tenantId,
                                                         Pageable pageable) {
        log.debug("根据风险等级查询: riskLevel={}, tenantId={}", riskLevel, tenantId);
        return jpaRepository.findByRiskLevelAndTenantIdOrderByCreatedAtDesc(riskLevel, tenantId, pageable)
                .map(mapper::toAggregate);
    }

    @Override
    public Page<Underwriting> findByUnderwriterAndTimeRange(String underwriterId, LocalDateTime startTime,
                                                            LocalDateTime endTime, String tenantId, Pageable pageable) {
        log.debug("根据核保员和时间范围查询: underwriterId={}, startTime={}, endTime={}, tenantId={}", underwriterId, startTime,
                endTime, tenantId);
        return jpaRepository.findByUnderwriterAndTimeRange(underwriterId, startTime, endTime, tenantId, pageable)
                .map(mapper::toAggregate);
    }

    @Override
    public Page<Underwriting> findByMultipleConditions(UnderwritingEnum.UnderwritingStatus status,
                                                       UnderwritingEnum.RiskLevel riskLevel,
                                                       UnderwritingEnum.AuditType auditType, String underwriterId,
                                                       LocalDateTime startTime, LocalDateTime endTime, String tenantId,
                                                       Pageable pageable) {
        log.debug("多条件组合查询: status={}, riskLevel={}, auditType={}, underwriterId={}, tenantId={}", status, riskLevel,
                auditType, underwriterId, tenantId);
        return jpaRepository.findByMultipleConditions(status, riskLevel, auditType, underwriterId, startTime, endTime,
                tenantId, pageable).map(mapper::toAggregate);
    }

    @Override
    public List<Underwriting> findHistoryByCustomerIdAndTenantId(String customerId, String tenantId) {
        log.debug("根据客户ID查询核保历史: customerId=, tenantId={}", customerId, tenantId);
        return jpaRepository.findByCustomerIdAndTenantIdOrderByCreatedAtDesc(customerId, tenantId).stream()
                .map(mapper::toAggregate).toList();
    }

    @Override
    public Page<Underwriting> findPendingTasks(String underwriterId, UnderwritingEnum.UnderwritingStatus status,
                                               String tenantId, Pageable pageable) {
        log.debug("查询待处理核保任务: underwriterId={}, status={}, tenantId={}", underwriterId, status, tenantId);
        return jpaRepository.findPendingTasks(underwriterId, status, tenantId, pageable).map(mapper::toAggregate);
    }

    // ========== 统计查询方法 ==========

    @Override
    public List<Object[]> getStatisticsRawData(LocalDateTime startTime, LocalDateTime endTime, String tenantId) {
        log.debug("获取核保统计原始数据: startTime={}, endTime={}, tenantId={}", startTime, endTime, tenantId);

        // 返回原始统计数据，由Query层负责转换为StatisticsResult
        return jpaRepository.getStatisticsRawData(startTime, endTime, tenantId);
    }
}
