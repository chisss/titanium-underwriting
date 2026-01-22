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
import com.titanium.underwriting.infrastructure.entity.UnderwritingQueryEntity;

/**
 * 核保查询JPA仓储接口 - Infrastructure层 基于Spring Data JPA实现数据访问
 * 符合项目规约第12条：基础设施层仓库类实现(包含Jpa包)
 */
public interface UnderwritingQueryJpaRepository extends JpaRepository<UnderwritingQueryEntity, String> {

    // ========== 基础查询方法 ==========

    /**
     * 根据核保ID和租户ID查找核保记录
     */
    Optional<UnderwritingQueryEntity> findByUnderwritingIdAndTenantId(String underwritingId, String tenantId);

    /**
     * 根据保单ID和租户ID查找核保记录
     */
    Optional<UnderwritingQueryEntity> findByPolicyIdAndTenantId(String policyId, String tenantId);

    // ========== 业务查询方法 ==========

    /**
     * 根据风险等级查询核保信息
     */
    Page<UnderwritingQueryEntity> findByRiskLevelAndTenantIdOrderByCreatedAtDesc(UnderwritingEnum.RiskLevel riskLevel,
                                                                                 String tenantId, Pageable pageable);

    /**
     * 根据核保员和时间范围查询核保信息
     */
    @Query("SELECT u FROM UnderwritingQueryEntity u " + "WHERE u.underwriterId = :underwriterId "
            + "AND u.createdAt BETWEEN :startTime AND :endTime " + "AND u.tenantId = :tenantId "
            + "ORDER BY u.createdAt DESC")
    Page<UnderwritingQueryEntity> findByUnderwriterAndTimeRange(@Param("underwriterId") String underwriterId,
                                                                @Param("startTime") LocalDateTime startTime,
                                                                @Param("endTime") LocalDateTime endTime,
                                                                @Param("tenantId") String tenantId, Pageable pageable);

    /**
     * 多条件组合查询核保信息
     */
    @Query("SELECT u FROM UnderwritingQueryEntity u " + "WHERE (:status IS NULL OR u.status = :status) "
            + "AND (:riskLevel IS NULL OR u.riskLevel = :riskLevel) "
            + "AND (:auditType IS NULL OR u.auditType = :auditType) "
            + "AND (:underwriterId IS NULL OR u.underwriterId = :underwriterId) "
            + "AND (:startTime IS NULL OR u.createdAt >= :startTime) "
            + "AND (:endTime IS NULL OR u.createdAt <= :endTime) " + "AND u.tenantId = :tenantId "
            + "ORDER BY u.createdAt DESC")
    Page<UnderwritingQueryEntity> findByMultipleConditions(@Param("status") UnderwritingEnum.UnderwritingStatus status,
                                                           @Param("riskLevel") UnderwritingEnum.RiskLevel riskLevel,
                                                           @Param("auditType") UnderwritingEnum.AuditType auditType,
                                                           @Param("underwriterId") String underwriterId,
                                                           @Param("startTime") LocalDateTime startTime,
                                                           @Param("endTime") LocalDateTime endTime,
                                                           @Param("tenantId") String tenantId, Pageable pageable);

    /**
     * 根据客户ID查询核保历史记录
     */
    List<UnderwritingQueryEntity> findByCustomerIdAndTenantIdOrderByCreatedAtDesc(String customerId, String tenantId);

    /**
     * 查询待处理的核保任务
     */
    @Query("SELECT u FROM UnderwritingQueryEntity u "
            + "WHERE (:underwriterId IS NULL OR u.underwriterId = :underwriterId) "
            + "AND (:status IS NULL OR u.status = :status) " + "AND u.tenantId = :tenantId "
            + "AND u.status IN ('PENDING', 'IN_REVIEW', 'WAITING_FOR_DOCUMENTS') " + "ORDER BY u.createdAt ASC")
    Page<UnderwritingQueryEntity> findPendingTasks(@Param("underwriterId") String underwriterId,
                                                   @Param("status") UnderwritingEnum.UnderwritingStatus status,
                                                   @Param("tenantId") String tenantId, Pageable pageable);

    // ========== 统计查询方法 ==========

    /**
     * 统计指定时间范围内的核保总数
     */
    @Query("SELECT COUNT(u) FROM UnderwritingQueryEntity u " + "WHERE u.createdAt BETWEEN :startTime AND :endTime "
            + "AND u.tenantId = :tenantId")
    Long countByTimeRangeAndTenantId(@Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime, @Param("tenantId") String tenantId);

    /**
     * 统计指定时间范围内按状态分组的核保数量
     */
    @Query("SELECT u.status, COUNT(u) FROM UnderwritingQueryEntity u "
            + "WHERE u.createdAt BETWEEN :startTime AND :endTime " + "AND u.tenantId = :tenantId "
            + "GROUP BY u.status")
    List<Object[]> countByStatusAndTimeRangeAndTenantId(@Param("startTime") LocalDateTime startTime,
                                                        @Param("endTime") LocalDateTime endTime,
                                                        @Param("tenantId") String tenantId);

    /**
     * 统计指定时间范围内按风险等级分组的核保数量
     */
    @Query("SELECT u.riskLevel, COUNT(u) FROM UnderwritingQueryEntity u "
            + "WHERE u.createdAt BETWEEN :startTime AND :endTime " + "AND u.tenantId = :tenantId "
            + "GROUP BY u.riskLevel")
    List<Object[]> countByRiskLevelAndTimeRangeAndTenantId(@Param("startTime") LocalDateTime startTime,
                                                           @Param("endTime") LocalDateTime endTime,
                                                           @Param("tenantId") String tenantId);

    /**
     * 统计指定时间范围内按核保类型分组的核保数量
     */
    @Query("SELECT u.auditType, COUNT(u) FROM UnderwritingQueryEntity u "
            + "WHERE u.createdAt BETWEEN :startTime AND :endTime " + "AND u.tenantId = :tenantId "
            + "GROUP BY u.auditType")
    List<Object[]> countByAuditTypeAndTimeRangeAndTenantId(@Param("startTime") LocalDateTime startTime,
                                                           @Param("endTime") LocalDateTime endTime,
                                                           @Param("tenantId") String tenantId);

    /**
     * 计算指定时间范围内的平均核保时效
     */
    @Query("SELECT AVG(u.processingHours) FROM UnderwritingQueryEntity u "
            + "WHERE u.createdAt BETWEEN :startTime AND :endTime " + "AND u.tenantId = :tenantId "
            + "AND u.processingHours IS NOT NULL")
    Double calculateAverageProcessingHours(@Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime, @Param("tenantId") String tenantId);

    /**
     * 统计指定时间范围内的核保金额汇总
     */
    @Query("SELECT u.status, SUM(u.amount) FROM UnderwritingQueryEntity u "
            + "WHERE u.createdAt BETWEEN :startTime AND :endTime " + "AND u.tenantId = :tenantId "
            + "AND u.amount IS NOT NULL " + "GROUP BY u.status")
    List<Object[]> sumAmountByStatusAndTimeRangeAndTenantId(@Param("startTime") LocalDateTime startTime,
                                                            @Param("endTime") LocalDateTime endTime,
                                                            @Param("tenantId") String tenantId);

    /**
     * 获取统计原始数据 返回综合统计信息，由Query层负责转换为StatisticsResult
     */
    @Query("SELECT " + "COUNT(u), " + // 0: 总数
            "SUM(CASE WHEN u.status = 'APPROVED' THEN 1 ELSE 0 END), " + // 1: 标准承保数
            "SUM(CASE WHEN u.status = 'APPROVED_WITH_SURCHARGE' THEN 1 ELSE 0 END), " + // 2: 加费承保数
            "SUM(CASE WHEN u.status = 'APPROVED_WITH_EXCLUSION' THEN 1 ELSE 0 END), " + // 3: 除外承保数
            "SUM(CASE WHEN u.status = 'POSTPONED' THEN 1 ELSE 0 END), " + // 4: 延期承保数
            "SUM(CASE WHEN u.status = 'DECLINED' THEN 1 ELSE 0 END), " + // 5: 拒保数
            "SUM(CASE WHEN u.riskLevel = 'LOW' THEN 1 ELSE 0 END), " + // 6: 低风险数
            "SUM(CASE WHEN u.riskLevel = 'MEDIUM' THEN 1 ELSE 0 END), " + // 7: 中风险数
            "SUM(CASE WHEN u.riskLevel = 'HIGH' THEN 1 ELSE 0 END), " + // 8: 高风险数
            "SUM(CASE WHEN u.auditType = 'AUTO' THEN 1 ELSE 0 END), " + // 9: 自动核保数
            "SUM(CASE WHEN u.auditType = 'MANUAL' THEN 1 ELSE 0 END), " + // 10: 人工核保数
            "SUM(CASE WHEN u.auditType = 'HYBRID' THEN 1 ELSE 0 END), " + // 11: 混合核保数
            "AVG(u.processingHours), " + // 12: 平均时效
            "SUM(u.amount), " + // 13: 总金额
            "SUM(CASE WHEN u.status = 'APPROVED' THEN u.amount ELSE 0 END), " + // 14: 标准承保金额
            "SUM(CASE WHEN u.status = 'APPROVED_WITH_SURCHARGE' THEN u.amount ELSE 0 END), " + // 15: 加费承保金额
            "SUM(CASE WHEN u.status = 'APPROVED_WITH_EXCLUSION' THEN u.amount ELSE 0 END), " + // 16: 除外承保金额
            "SUM(CASE WHEN u.status = 'POSTPONED' THEN u.amount ELSE 0 END), " + // 17: 延期承保金额
            "SUM(CASE WHEN u.status = 'DECLINED' THEN u.amount ELSE 0 END) " + // 18: 拒保金额
            "FROM UnderwritingQueryEntity u " + "WHERE u.createdAt BETWEEN :startTime AND :endTime "
            + "AND u.tenantId = :tenantId")
    List<Object[]> getStatisticsRawData(@Param("startTime") LocalDateTime startTime,
                                        @Param("endTime") LocalDateTime endTime, @Param("tenantId") String tenantId);
}
