package com.titanium.underwriting.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.domain.aggregate.Underwriting;

/**
 * 核保查询仓储接口 - 定义在Domain层 基于保险业务全生命周期核保承保阶段的查询需求
 * 符合DDD架构：Repository接口定义在Domain层，返回聚合根 严格遵循「调用方向：application → query → infra →
 * domain」 严格遵循「返回方向：数据库 → infra → query → application → 外部」
 */
public interface UnderwritingQueryRepository {

    // ========== 基础CRUD操作 ==========

    /**
     * 保存核保聚合根
     */
    Underwriting save(Underwriting underwriting);

    /**
     * 根据核保ID和租户ID查找核保记录
     *
     * @return 返回聚合根，不是QueryResult
     */
    Optional<Underwriting> findByUnderwritingIdAndTenantId(String underwritingId, String tenantId);

    // ========== 业务查询方法 - 返回聚合根 ==========

    /**
     * 根据风险等级查询核保信息
     *
     * @return 返回聚合根分页，由Query层负责转换为QueryResult
     */
    Page<Underwriting> findByRiskLevelAndTenantId(UnderwritingEnum.RiskLevel riskLevel, String tenantId,
                                                  Pageable pageable);

    /**
     * 根据核保员和时间范围查询核保信息
     *
     * @return 返回聚合根分页，由Query层负责转换为QueryResult
     */
    Page<Underwriting> findByUnderwriterAndTimeRange(String underwriterId, LocalDateTime startTime,
                                                     LocalDateTime endTime, String tenantId, Pageable pageable);

    /**
     * 多条件组合查询核保信息
     *
     * @return 返回聚合根分页，由Query层负责转换为QueryResult
     */
    Page<Underwriting> findByMultipleConditions(UnderwritingEnum.UnderwritingStatus status,
                                                UnderwritingEnum.RiskLevel riskLevel,
                                                UnderwritingEnum.AuditType auditType, String underwriterId,
                                                LocalDateTime startTime, LocalDateTime endTime, String tenantId,
                                                Pageable pageable);

    /**
     * 根据客户ID查询核保历史记录
     *
     * @return 返回聚合根列表，由Query层负责转换为QueryResult
     */
    List<Underwriting> findHistoryByCustomerIdAndTenantId(String customerId, String tenantId);

    /**
     * 查询待处理的核保任务
     *
     * @return 返回聚合根分页，由Query层负责转换为QueryResult
     */
    Page<Underwriting> findPendingTasks(String underwriterId, UnderwritingEnum.UnderwritingStatus status,
                                        String tenantId, Pageable pageable);

    // ========== 统计查询方法 ==========

    /**
     * 获取核保统计数据的原始数据 注意：这里返回统计原始数据，由Query层负责转换为StatisticsResult
     */
    List<Object[]> getStatisticsRawData(LocalDateTime startTime, LocalDateTime endTime, String tenantId);
}
