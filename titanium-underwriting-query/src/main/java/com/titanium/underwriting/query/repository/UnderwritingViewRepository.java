package com.titanium.underwriting.query.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.query.view.UnderwritingView;

/**
 * 核保读模型仓储
 * <p>
 * CQRS 查询侧仓储，直接访问读模型表 {@code t_underwriting_view}，与写侧事件溯源存储隔离。 查询方法强制携带
 * {@code tenantId} 实现多租户数据隔离。复杂动态查询由 {@link JpaSpecificationExecutor} 支持。
 * </p>
 */
@Repository
public interface UnderwritingViewRepository
        extends JpaRepository<UnderwritingView, String>, JpaSpecificationExecutor<UnderwritingView> {

    /**
     * 按核保ID + 租户ID查询
     */
    Optional<UnderwritingView> findByUnderwritingIdAndTenantId(String underwritingId, String tenantId);

    /**
     * 按保单ID + 租户ID查询
     */
    Optional<UnderwritingView> findByPolicyIdAndTenantId(String policyId, String tenantId);

    /**
     * 按投保单号 + 租户ID查询核保单（按创建时间升序）
     * <p>
     * 返回 {@link List} 而非 {@code Optional}：投保单号是 g02-04 起的幂等键，但读模型**不建唯一约束**
     * （理由见迁移脚本 {@code underwriting_view_202609271200_weisun_ddl.sql}），历史数据与并发场景下
     * 同一投保单可能存在多行。调用方据此自行收敛（已出结论者优先复用，否则续跑最早一张）。
     * </p>
     */
    List<UnderwritingView> findByInsuranceIdAndTenantIdOrderByCreatedAtAsc(String insuranceId, String tenantId);

    /**
     * 按状态 + 租户ID分页查询
     */
    Page<UnderwritingView> findByStatusAndTenantId(UnderwritingEnum.UnderwritingStatus status, String tenantId,
                                                   Pageable pageable);

    /**
     * 按风险等级 + 租户ID分页查询
     */
    Page<UnderwritingView> findByRiskLevelAndTenantId(UnderwritingEnum.RiskLevel riskLevel, String tenantId,
                                                      Pageable pageable);

    /**
     * 按核保员 + 创建时间范围 + 租户ID分页查询
     */
    Page<UnderwritingView> findByUnderwriterIdAndCreatedAtBetweenAndTenantId(String underwriterId,
                                                                             LocalDateTime startTime,
                                                                             LocalDateTime endTime, String tenantId,
                                                                             Pageable pageable);

    /**
     * 按客户ID + 租户ID查询核保历史（按创建时间倒序）
     */
    List<UnderwritingView> findByCustomerIdAndTenantIdOrderByCreatedAtDesc(String customerId, String tenantId);
}
