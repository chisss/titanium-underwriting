package com.titanium.underwriting.query.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.query.mapper.UnderwritingQueryResultMapper;
import com.titanium.underwriting.query.repository.UnderwritingViewRepository;
import com.titanium.underwriting.query.result.UnderwritingQueryResult;
import com.titanium.underwriting.query.result.UnderwritingStatisticsResult;
import com.titanium.underwriting.query.service.UnderwritingQueryService;
import com.titanium.underwriting.query.view.UnderwritingView;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 核保查询服务实现（CQRS 读侧）
 * <p>
 * 查询由事件投影维护的读模型表 {@code t_underwriting_view}，复杂动态条件用 JPA {@link Specification} 组装。
 * 原「重建写侧事件溯源聚合根来查询」的读写混用缺陷已在此彻底根除。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UnderwritingQueryServiceImpl implements UnderwritingQueryService {

    /** 待处理任务状态集合：待核保 / 人工审核 / 复核 / 待定 */
    private static final List<UnderwritingEnum.UnderwritingStatus> PENDING_STATUSES = List.of(
            UnderwritingEnum.UnderwritingStatus.PENDING, UnderwritingEnum.UnderwritingStatus.MANUAL_REVIEW,
            UnderwritingEnum.UnderwritingStatus.REVIEW, UnderwritingEnum.UnderwritingStatus.AWAITING_INFO);

    // ========== JPA 查询字段名常量（集中管理，禁止散落 root.get("...") 裸字段名，红线 21） ==========

    /** 租户ID字段名 */
    private static final String FIELD_TENANT_ID      = "tenantId";
    /** 核保状态字段名 */
    private static final String FIELD_STATUS         = "status";
    /** 风险等级字段名 */
    private static final String FIELD_RISK_LEVEL     = "riskLevel";
    /** 核保方式字段名 */
    private static final String FIELD_AUDIT_TYPE     = "auditType";
    /** 核保员ID字段名 */
    private static final String FIELD_UNDERWRITER_ID = "underwriterId";
    /** 业务创建时间字段名 */
    private static final String FIELD_CREATED_AT     = "createdAt";

    private final UnderwritingViewRepository    underwritingViewRepository;
    private final UnderwritingQueryResultMapper underwritingQueryResultMapper;

    @Override
    public UnderwritingQueryResult findById(String underwritingId, String tenantId) {
        log.info("查询核保详情: underwritingId={}, tenantId={}", underwritingId, tenantId);
        return underwritingViewRepository.findByUnderwritingIdAndTenantId(underwritingId, tenantId)
                .map(underwritingQueryResultMapper::toQueryResult).orElse(null);
    }

    @Override
    public UnderwritingQueryResult findByPolicyId(String policyId, String tenantId) {
        log.info("根据保单ID查询核保: policyId={}, tenantId={}", policyId, tenantId);
        return underwritingViewRepository.findByPolicyIdAndTenantId(policyId, tenantId).map(underwritingQueryResultMapper::toQueryResult)
                .orElse(null);
    }

    @Override
    public Page<UnderwritingQueryResult> findByStatus(UnderwritingEnum.UnderwritingStatus status, String tenantId,
                                                      Pageable pageable) {
        log.info("根据状态查询核保: status={}, tenantId={}", status, tenantId);
        return underwritingViewRepository.findByStatusAndTenantId(status, tenantId, pageable).map(underwritingQueryResultMapper::toQueryResult);
    }

    @Override
    public Page<UnderwritingQueryResult> findByRiskLevel(UnderwritingEnum.RiskLevel riskLevel, String tenantId,
                                                         Pageable pageable) {
        log.info("根据风险等级查询核保: riskLevel={}, tenantId={}", riskLevel, tenantId);
        return underwritingViewRepository.findByRiskLevelAndTenantId(riskLevel, tenantId, pageable)
                .map(underwritingQueryResultMapper::toQueryResult);
    }

    @Override
    public Page<UnderwritingQueryResult> findByUnderwriterAndTimeRange(String underwriterId, LocalDateTime startTime,
                                                                       LocalDateTime endTime, String tenantId,
                                                                       Pageable pageable) {
        log.info("根据核保员和时间范围查询核保: underwriterId={}, startTime={}, endTime={}, tenantId={}", underwriterId, startTime,
                endTime, tenantId);
        return underwritingViewRepository
                .findByUnderwriterIdAndCreatedAtBetweenAndTenantId(underwriterId, startTime, endTime, tenantId,
                        pageable)
                .map(underwritingQueryResultMapper::toQueryResult);
    }

    @Override
    public Page<UnderwritingQueryResult> findByMultipleConditions(UnderwritingEnum.UnderwritingStatus status,
                                                                  UnderwritingEnum.RiskLevel riskLevel,
                                                                  UnderwritingEnum.AuditType auditType,
                                                                  String underwriterId, LocalDateTime startTime,
                                                                  LocalDateTime endTime, String tenantId,
                                                                  Pageable pageable) {
        log.info("多条件组合查询核保: status={}, riskLevel={}, auditType={}, underwriterId={}, tenantId={}", status, riskLevel,
                auditType, underwriterId, tenantId);
        Specification<UnderwritingView> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get(FIELD_TENANT_ID), tenantId));
            if (status != null) {
                predicates.add(cb.equal(root.get(FIELD_STATUS), status));
            }
            if (riskLevel != null) {
                predicates.add(cb.equal(root.get(FIELD_RISK_LEVEL), riskLevel));
            }
            if (auditType != null) {
                predicates.add(cb.equal(root.get(FIELD_AUDIT_TYPE), auditType));
            }
            if (underwriterId != null) {
                predicates.add(cb.equal(root.get(FIELD_UNDERWRITER_ID), underwriterId));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get(FIELD_CREATED_AT), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get(FIELD_CREATED_AT), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return underwritingViewRepository.findAll(spec, pageable).map(underwritingQueryResultMapper::toQueryResult);
    }

    @Override
    public List<UnderwritingQueryResult> findHistoryByCustomer(String customerId, String tenantId) {
        log.info("查询客户核保历史: customerId={}, tenantId={}", customerId, tenantId);
        return underwritingViewRepository.findByCustomerIdAndTenantIdOrderByCreatedAtDesc(customerId, tenantId).stream()
                .map(underwritingQueryResultMapper::toQueryResult).toList();
    }

    @Override
    public Page<UnderwritingQueryResult> findPendingTasks(String underwriterId,
                                                          UnderwritingEnum.UnderwritingStatus status, String tenantId,
                                                          Pageable pageable) {
        log.info("查询待处理核保任务: underwriterId={}, status={}, tenantId={}", underwriterId, status, tenantId);
        Specification<UnderwritingView> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get(FIELD_TENANT_ID), tenantId));
            if (underwriterId != null) {
                predicates.add(cb.equal(root.get(FIELD_UNDERWRITER_ID), underwriterId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get(FIELD_STATUS), status));
            } else {
                predicates.add(root.get(FIELD_STATUS).in(PENDING_STATUSES));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return underwritingViewRepository.findAll(spec, pageable).map(underwritingQueryResultMapper::toQueryResult);
    }

    @Override
    public UnderwritingStatisticsResult getStatistics(LocalDateTime startTime, LocalDateTime endTime, String tenantId) {
        log.info("获取核保统计数据: startTime={}, endTime={}, tenantId={}", startTime, endTime, tenantId);
        Specification<UnderwritingView> spec = (root, query, cb) -> cb.and(cb.equal(root.get(FIELD_TENANT_ID), tenantId),
                cb.greaterThanOrEqualTo(root.get(FIELD_CREATED_AT), startTime),
                cb.lessThanOrEqualTo(root.get(FIELD_CREATED_AT), endTime));
        List<UnderwritingView> views = underwritingViewRepository.findAll(spec);
        return buildStatistics(startTime, endTime, tenantId, views);
    }

    /**
     * 基于读模型记录聚合统计（读侧最终一致，用于报表分析）
     */
    private UnderwritingStatisticsResult buildStatistics(LocalDateTime startTime, LocalDateTime endTime,
                                                         String tenantId, List<UnderwritingView> views) {
        UnderwritingStatisticsResult result = new UnderwritingStatisticsResult();
        result.setStartTime(startTime);
        result.setEndTime(endTime);
        result.setTenantId(tenantId);
        result.setGeneratedAt(LocalDateTime.now());

        long total = views.size();
        long standard = countByStatus(views, UnderwritingEnum.UnderwritingStatus.STANDARD);
        long rated = countByStatus(views, UnderwritingEnum.UnderwritingStatus.RATED);
        long postponed = countByStatus(views, UnderwritingEnum.UnderwritingStatus.POSTPONED);
        long declined = countByStatus(views, UnderwritingEnum.UnderwritingStatus.DECLINED);
        long pending = countByStatus(views, UnderwritingEnum.UnderwritingStatus.PENDING);

        result.setTotalCount(total);
        result.setStandardCount(standard);
        result.setRatedCount(rated);
        result.setPostponedCount(postponed);
        result.setDeclinedCount(declined);
        result.setPendingCount(pending);
        result.setTotalAmount(views.stream().map(UnderwritingView::getAmount)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
        if (total > 0) {
            result.setOverallPassRate(BigDecimal.valueOf(standard + rated)
                    .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP));
        } else {
            result.setOverallPassRate(BigDecimal.ZERO);
        }
        return result;
    }

    /**
     * 统计指定状态的记录数
     */
    private long countByStatus(List<UnderwritingView> views, UnderwritingEnum.UnderwritingStatus status) {
        return views.stream().filter(v -> v.getStatus() == status).count();
    }
}
