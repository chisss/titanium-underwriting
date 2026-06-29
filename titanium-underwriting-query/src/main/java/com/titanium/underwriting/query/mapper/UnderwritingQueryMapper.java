package com.titanium.underwriting.query.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.titanium.underwriting.domain.aggregate.Underwriting;
import com.titanium.underwriting.query.entity.UnderwritingQueryResult;
import com.titanium.underwriting.query.entity.UnderwritingStatisticsResult;

/**
 * 核保查询映射器 - Query层 负责「聚合根→QueryResult」转换 符合DDD架构：Query层负责转换边界 严格遵循「返回方向：数据库 →
 * infra → query → application → 外部」
 */
@Mapper
public interface UnderwritingQueryMapper {

    UnderwritingQueryMapper INSTANCE = Mappers.getMapper(UnderwritingQueryMapper.class);

    // ========== 聚合根到QueryResult的映射 ==========

    /**
     * 聚合根转换为QueryResult 这是Query层的核心职责：将Domain层的聚合根转换为Query层的结果对象
     */
    @Mapping(source = "underwritingId.value", target = "underwritingId")
    @Mapping(source = "policyId.value", target = "policyId")
    @Mapping(source = "customerId.value", target = "customerId")
    @Mapping(source = "amount.amount", target = "amount")
    UnderwritingQueryResult toQueryResult(Underwriting underwriting);

    /**
     * 聚合根列表转换为QueryResult列表
     */
    List<UnderwritingQueryResult> toQueryResultList(List<Underwriting> underwritings);

    // ========== 统计数据转换方法 ==========

    /**
     * 根据原始统计数据创建统计结果 Query层核心职责：将Infrastructure层返回的原始数据转换为StatisticsResult
     */
    default UnderwritingStatisticsResult createStatisticsResult(java.time.LocalDateTime startTime,
                                                                java.time.LocalDateTime endTime, String tenantId,
                                                                List<Object[]> rawData) {

        UnderwritingStatisticsResult result = new UnderwritingStatisticsResult();
        result.setStartTime(startTime);
        result.setEndTime(endTime);
        result.setTenantId(tenantId);
        result.setGeneratedAt(java.time.LocalDateTime.now());

        // 处理原始统计数据
        if (rawData != null && !rawData.isEmpty()) {
            Object[] data = rawData.get(0); // 获取第一行数据（聚合查询只返回一行）

            // 基础统计数据
            result.setTotalCount(data[0] != null ? ((Number) data[0]).longValue() : 0L);
            result.setStandardCount(data[1] != null ? ((Number) data[1]).longValue() : 0L);
            result.setSurchargeCount(data[2] != null ? ((Number) data[2]).longValue() : 0L);
            result.setExclusionCount(data[3] != null ? ((Number) data[3]).longValue() : 0L);
            result.setPostponeCount(data[4] != null ? ((Number) data[4]).longValue() : 0L);
            result.setDeclineCount(data[5] != null ? ((Number) data[5]).longValue() : 0L);

            // 风险等级统计
            result.setLowRiskCount(data[6] != null ? ((Number) data[6]).longValue() : 0L);
            result.setMediumRiskCount(data[7] != null ? ((Number) data[7]).longValue() : 0L);
            result.setHighRiskCount(data[8] != null ? ((Number) data[8]).longValue() : 0L);

            // 核保类型统计
            result.setAutoUnderwritingCount(data[9] != null ? ((Number) data[9]).longValue() : 0L);
            result.setManualUnderwritingCount(data[10] != null ? ((Number) data[10]).longValue() : 0L);
            result.setHybridUnderwritingCount(data[11] != null ? ((Number) data[11]).longValue() : 0L);

            // 平均时效
            result.setAverageProcessingHours(
                    data[12] != null ? java.math.BigDecimal.valueOf(((Number) data[12]).doubleValue())
                            : java.math.BigDecimal.ZERO);

            // 金额统计
            result.setTotalAmount(data[13] != null ? (java.math.BigDecimal) data[13] : java.math.BigDecimal.ZERO);
            result.setStandardAmount(data[14] != null ? (java.math.BigDecimal) data[14] : java.math.BigDecimal.ZERO);
            result.setSurchargeAmount(data[15] != null ? (java.math.BigDecimal) data[15] : java.math.BigDecimal.ZERO);
            result.setExclusionAmount(data[16] != null ? (java.math.BigDecimal) data[16] : java.math.BigDecimal.ZERO);
            result.setPostponeAmount(data[17] != null ? (java.math.BigDecimal) data[17] : java.math.BigDecimal.ZERO);
            result.setDeclineAmount(data[18] != null ? (java.math.BigDecimal) data[18] : java.math.BigDecimal.ZERO);

            // 计算通过率
            calculatePassRates(result);
        }

        return result;
    }

    /**
     * 计算各种通过率
     */
    default void calculatePassRates(UnderwritingStatisticsResult result) {
        Long totalCount = result.getTotalCount();
        if (totalCount == null || totalCount == 0) {
            return;
        }

        java.math.BigDecimal total = java.math.BigDecimal.valueOf(totalCount);

        // 总通过率 = (标准承保 + 加费承保 + 除外承保) / 总数
        Long passedCount = (result.getStandardCount() != null ? result.getStandardCount() : 0L)
                + (result.getSurchargeCount() != null ? result.getSurchargeCount() : 0L)
                + (result.getExclusionCount() != null ? result.getExclusionCount() : 0L);
        result.setOverallPassRate(
                java.math.BigDecimal.valueOf(passedCount).divide(total, 4, java.math.RoundingMode.HALF_UP));

        // 标准承保率
        if (result.getStandardCount() != null && result.getStandardCount() > 0) {
            result.setStandardPassRate(java.math.BigDecimal.valueOf(result.getStandardCount()).divide(total, 4,
                    java.math.RoundingMode.HALF_UP));
        }

        // 加费承保率
        if (result.getSurchargeCount() != null && result.getSurchargeCount() > 0) {
            result.setSurchargePassRate(java.math.BigDecimal.valueOf(result.getSurchargeCount()).divide(total, 4,
                    java.math.RoundingMode.HALF_UP));
        }

        // 除外承保率
        if (result.getExclusionCount() != null && result.getExclusionCount() > 0) {
            result.setExclusionPassRate(java.math.BigDecimal.valueOf(result.getExclusionCount()).divide(total, 4,
                    java.math.RoundingMode.HALF_UP));
        }

        // 拒保率
        if (result.getDeclineCount() != null && result.getDeclineCount() > 0) {
            result.setDeclineRate(java.math.BigDecimal.valueOf(result.getDeclineCount()).divide(total, 4,
                    java.math.RoundingMode.HALF_UP));
        }
    }
}
