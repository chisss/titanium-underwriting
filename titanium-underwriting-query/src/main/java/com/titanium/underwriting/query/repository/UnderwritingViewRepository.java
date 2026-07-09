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
