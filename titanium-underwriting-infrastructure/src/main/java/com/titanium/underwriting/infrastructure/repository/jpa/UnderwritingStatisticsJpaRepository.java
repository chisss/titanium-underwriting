package com.titanium.underwriting.infrastructure.repository.jpa;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.titanium.underwriting.infrastructure.entity.UnderwritingStatisticsEntity;

/**
 * 核保统计仓储接口 - Infrastructure层 基于Spring Data 实现统计数据访问
 */
public interface UnderwritingStatisticsJpaRepository extends JpaRepository<UnderwritingStatisticsEntity, Long> {

    /**
     * 根据时间范围和租户ID查找统计记录
     */
    Optional<UnderwritingStatisticsEntity> findByStartTimeAndEndTimeAndTenantId(LocalDateTime startTime,
                                                                                LocalDateTime endTime, String tenantId);

    /**
     * 查找指定租户的最新统计记录
     */
    @Query("SELECT s FROM UnderwritingStatisticsEntity s " + "WHERE s.tenantId = :tenantId "
            + "ORDER BY s.generatedAt DESC")
    List<UnderwritingStatisticsEntity> findLatestByTenantId(@Param("tenantId") String tenantId);

    /**
     * 删除指定时间之前的统计记录（用于数据清理）
     */
    void deleteByGeneratedAtBeforeAndTenantId(LocalDateTime cutoffTime, String tenantId);
}
