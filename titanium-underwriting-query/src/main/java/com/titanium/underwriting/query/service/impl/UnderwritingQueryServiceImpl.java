package com.titanium.underwriting.query.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
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

    private final UnderwritingViewRepository underwritingViewRepository;

    @Override
    public UnderwritingQueryResult findById(String underwritingId, String tenantId) {
        log.info("查询核保详情: underwritingId={}, tenantId={}", underwritingId, tenantId);
        return underwritingViewRepository.findByUnderwritingIdAndTenantId(underwritingId, tenantId)
                .map(this::toQueryResult).orElse(null);
    }

    @Override
    public UnderwritingQueryResult findByPolicyId(String policyId, String tenantId) {
        log.info("根据保单ID查询核保: policyId={}, tenantId={}", policyId, tenantId);
        return underwritingViewRepository.findByPolicyIdAndTenantId(policyId, tenantId).map(this::toQueryResult)
                .orElse(null);
    }

    @Override
    public Page<UnderwritingQueryResult> findByStatus(UnderwritingEnum.UnderwritingStatus status, String tenantId,
                                                      Pageable pageable) {
        log.info("根据状态查询核保: status={}, tenantId={}", status, tenantId);
        return underwritingViewRepository.findByStatusAndTenantId(status, tenantId, pageable).map(this::toQueryResult);
    }

    @Override
    public Page<UnderwritingQueryResult> findByRiskLevel(UnderwritingEnum.RiskLevel riskLevel, String tenantId,
                                                         Pageable pageable) {
        log.info("根据风险等级查询核保: riskLevel={}, tenantId={}", riskLevel, tenantId);
        return underwritingViewRepository.findByRiskLevelAndTenantId(riskLevel, tenantId, pageable)
                .map(this::toQueryResult);
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
                .map(this::toQueryResult);
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
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (riskLevel != null) {
                predicates.add(cb.equal(root.get("riskLevel"), riskLevel));
            }
            if (auditType != null) {
                predicates.add(cb.equal(root.get("auditType"), auditType));
            }
            if (underwriterId != null) {
                predicates.add(cb.equal(root.get("underwriterId"), underwriterId));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return underwritingViewRepository.findAll(spec, pageable).map(this::toQueryResult);
    }

    @Override
    public List<UnderwritingQueryResult> findHistoryByCustomer(String customerId, String tenantId) {
        log.info("查询客户核保历史: customerId={}, tenantId={}", customerId, tenantId);
        return underwritingViewRepository.findByCustomerIdAndTenantIdOrderByCreatedAtDesc(customerId, tenantId).stream()
                .map(this::toQueryResult).toList();
    }

    @Override
    public Page<UnderwritingQueryResult> findPendingTasks(String underwriterId,
                                                          UnderwritingEnum.UnderwritingStatus status, String tenantId,
                                                          Pageable pageable) {
        log.info("查询待处理核保任务: underwriterId={}, status={}, tenantId={}", underwriterId, status, tenantId);
        Specification<UnderwritingView> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (underwriterId != null) {
                predicates.add(cb.equal(root.get("underwriterId"), underwriterId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            } else {
                predicates.add(root.get("status").in(PENDING_STATUSES));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return underwritingViewRepository.findAll(spec, pageable).map(this::toQueryResult);
    }

    @Override
    public UnderwritingStatisticsResult getStatistics(LocalDateTime startTime, LocalDateTime endTime, String tenantId) {
        log.info("获取核保统计数据: startTime={}, endTime={}, tenantId={}", startTime, endTime, tenantId);
        Specification<UnderwritingView> spec = (root, query, cb) -> cb.and(cb.equal(root.get("tenantId"), tenantId),
                cb.greaterThanOrEqualTo(root.get("createdAt"), startTime),
                cb.lessThanOrEqualTo(root.get("createdAt"), endTime));
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
                .filter(java.util.Objects::nonNull).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
        if (total > 0) {
            result.setOverallPassRate(java.math.BigDecimal.valueOf(standard + rated)
                    .divide(java.math.BigDecimal.valueOf(total), 4, java.math.RoundingMode.HALF_UP));
        } else {
            result.setOverallPassRate(java.math.BigDecimal.ZERO);
        }
        return result;
    }

    /**
     * 统计指定状态的记录数
     */
    private long countByStatus(List<UnderwritingView> views, UnderwritingEnum.UnderwritingStatus status) {
        return views.stream().filter(v -> v.getStatus() == status).count();
    }

    /**
     * 读模型实体 → 查询结果 DTO
     */
    private UnderwritingQueryResult toQueryResult(UnderwritingView view) {
        UnderwritingQueryResult result = new UnderwritingQueryResult();
        result.setUnderwritingId(view.getUnderwritingId());
        result.setPolicyId(view.getPolicyId());
        result.setCustomerId(view.getCustomerId());
        result.setAmount(view.getAmount());
        result.setUnderwritingType(view.getUnderwritingType());
        result.setStatus(view.getStatus());
        result.setRejectReason(view.getRejectReason());
        result.setReviewComments(view.getReviewComments());
        result.setRiskLevel(view.getRiskLevel());
        result.setConclusionType(view.getConclusionType());
        result.setAuditType(view.getAuditType());
        result.setUnderwriterId(view.getUnderwriterId());
        result.setRiskScore(view.getRiskScore());
        result.setCreatedAt(view.getCreatedAt());
        result.setCreatedBy(view.getCreatedBy());
        result.setUpdatedAt(view.getUpdateTime());
        result.setUpdatedBy(view.getUpdatedBy());
        result.setTenantId(view.getTenantId());
        result.setVersion(view.getVersion());
        return result;
    }
}
