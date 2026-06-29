package com.titanium.underwriting.infrastructure.repository.jpa;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.infrastructure.entity.UnderwritingEntity;

/**
 * 核保JPA仓储接口
 */
public interface UnderwritingJpaRepository extends JpaRepository<UnderwritingEntity, String> {
    /**
     * 根据保单ID和租户ID查找核保记录
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 核保实体
     */
    Optional<UnderwritingEntity> findByPolicyIdAndTenantId(String policyId, String tenantId);

    /**
     * 根据客户ID和租户ID查找核保记录列表
     *
     * @param customerId 客户ID
     * @param tenantId 租户ID
     * @return 核保实体列表
     */
    List<UnderwritingEntity> findByCustomerIdAndTenantId(String customerId, String tenantId);

    /**
     * 根据状态和租户ID查找核保记录列表
     *
     * @param status 核保状态
     * @param tenantId 租户ID
     * @return 核保实体列表
     */
    List<UnderwritingEntity> findByStatusAndTenantId(UnderwritingEnum.UnderwritingStatus status, String tenantId);

    /**
     * 根据核保类型和租户ID查找核保记录列表
     *
     * @param underwritingType 核保类型
     * @param tenantId 租户ID
     * @return 核保实体列表
     */
    List<UnderwritingEntity> findByUnderwritingTypeAndTenantId(UnderwritingEnum.UnderwritingType underwritingType,
                                                              String tenantId);

    /**
     * 根据租户ID查找所有核保记录
     *
     * @param tenantId 租户ID
     * @return 核保实体列表
     */
    List<UnderwritingEntity> findByTenantId(String tenantId);

    /**
     * 删除核保记录
     *
     * @param underwritingId 核保ID
     * @param tenantId 租户ID
     */
    @Query("DELETE FROM UnderwritingEntity u WHERE u.underwritingId = :underwritingId AND u.tenantId = :tenantId")
    void deleteByUnderwritingIdAndTenantId(@Param("underwritingId") String underwritingId,
                                           @Param("tenantId") String tenantId);

    /**
     * 根据核保ID和租户ID查找核保记录
     *
     * @param underwritingId 核保ID
     * @param tenantId 租户ID
     * @return 核保实体
     */
    Optional<UnderwritingEntity> findByUnderwritingIdAndTenantId(String underwritingId, String tenantId);

    /**
     * 根据客户ID和租户ID分页查找核保记录
     *
     * @param customerId 客户ID
     * @param tenantId 租户ID
     * @param pageable 分页信息
     * @return 核保实体分页列表
     */
    Page<UnderwritingEntity> findByCustomerIdAndTenantId(String customerId, String tenantId, Pageable pageable);

    /**
     * 根据状态和租户ID分页查找核保记录
     *
     * @param status 核保状态
     * @param tenantId 租户ID
     * @param pageable 分页信息
     * @return 核保实体分页列表
     */
    Page<UnderwritingEntity> findByStatusAndTenantId(UnderwritingEnum.UnderwritingStatus status, String tenantId,
                                                     Pageable pageable);

    /**
     * 根据客户ID、状态和租户ID分页查找核保记录
     *
     * @param customerId 客户ID
     * @param status 核保状态
     * @param tenantId 租户ID
     * @param pageable 分页信息
     * @return 核保实体分页列表
     */
    Page<UnderwritingEntity> findByCustomerIdAndStatusAndTenantId(String customerId,
                                                                  UnderwritingEnum.UnderwritingStatus status,
                                                                  String tenantId, Pageable pageable);

    /**
     * 根据创建时间范围和租户ID分页查找核保记录
     *
     * @param startDate 开始时间
     * @param endDate 结束时间
     * @param tenantId 租户ID
     * @param pageable 分页信息
     * @return 核保实体分页列表
     */
    Page<UnderwritingEntity> findByCreatedAtBetweenAndTenantId(LocalDateTime startDate, LocalDateTime endDate,
                                                               String tenantId, Pageable pageable);

    /**
     * 根据核保类型和租户ID分页查找核保记录
     *
     * @param underwritingType 核保类型
     * @param tenantId 租户ID
     * @param pageable 分页信息
     * @return 核保实体分页列表
     */
    Page<UnderwritingEntity> findByUnderwritingTypeAndTenantId(UnderwritingEnum.UnderwritingType underwritingType,
                                                               String tenantId, Pageable pageable);

    /**
     * 根据状态和租户ID统计核保记录数量
     *
     * @param status 核保状态
     * @param tenantId 租户ID
     * @return 核保记录数量
     */
    long countByStatusAndTenantId(UnderwritingEnum.UnderwritingStatus status, String tenantId);
}
