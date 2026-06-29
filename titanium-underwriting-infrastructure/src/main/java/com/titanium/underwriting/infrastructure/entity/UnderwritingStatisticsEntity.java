package com.titanium.underwriting.infrastructure.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

import org.hibernate.proxy.HibernateProxy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 核保统计实体 - Infrastructure层数据库实体 用于数据库持久化 基于保险业务全生命周期核保承保阶段的统计分析需求设计
 * 符合项目规约第13条：所有数据表中必须包含tenant_id字段
 */
@Entity
@Table(name = "t_underwriting_statistics", indexes = {
        @Index(name = "idx_underwriting_statistics_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_underwriting_statistics_time_range", columnList = "start_time, end_time, tenant_id"),
        @Index(name = "idx_underwriting_statistics_generated_at", columnList = "generated_at, tenant_id") })
@Getter
@Setter
@ToString
@RequiredArgsConstructor
public class UnderwritingStatisticsEntity {

    // ========== 主键和基础信息 ==========
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long          id;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "tenant_id", length = 50, nullable = false)
    private String        tenantId;

    // ========== 核保数量统计 ==========
    @Column(name = "total_count", nullable = false)
    private Long          totalCount;

    @Column(name = "standard_count", nullable = false)
    private Long          standardCount;

    @Column(name = "surcharge_count", nullable = false)
    private Long          surchargeCount;

    @Column(name = "exclusion_count", nullable = false)
    private Long          exclusionCount;

    @Column(name = "postpone_count", nullable = false)
    private Long          postponeCount;

    @Column(name = "decline_count", nullable = false)
    private Long          declineCount;

    // ========== 核保金额统计 ==========
    @Column(name = "total_amount", precision = 20, scale = 2, nullable = false)
    private BigDecimal    totalAmount;

    @Column(name = "standard_amount", precision = 20, scale = 2, nullable = false)
    private BigDecimal    standardAmount;

    @Column(name = "surcharge_amount", precision = 20, scale = 2, nullable = false)
    private BigDecimal    surchargeAmount;

    @Column(name = "exclusion_amount", precision = 20, scale = 2, nullable = false)
    private BigDecimal    exclusionAmount;

    @Column(name = "postpone_amount", precision = 20, scale = 2, nullable = false)
    private BigDecimal    postponeAmount;

    @Column(name = "decline_amount", precision = 20, scale = 2, nullable = false)
    private BigDecimal    declineAmount;

    // ========== 核保效率统计 ==========
    @Column(name = "average_processing_hours", precision = 8, scale = 2)
    private BigDecimal    averageProcessingHours;

    @Column(name = "auto_underwriting_count", nullable = false)
    private Long          autoUnderwritingCount;

    @Column(name = "manual_underwriting_count", nullable = false)
    private Long          manualUnderwritingCount;

    @Column(name = "hybrid_underwriting_count", nullable = false)
    private Long          hybridUnderwritingCount;

    // ========== 风险等级统计 ==========
    @Column(name = "low_risk_count", nullable = false)
    private Long          lowRiskCount;

    @Column(name = "medium_risk_count", nullable = false)
    private Long          mediumRiskCount;

    @Column(name = "high_risk_count", nullable = false)
    private Long          highRiskCount;

    // ========== 核保通过率统计 ==========
    @Column(name = "overall_pass_rate", precision = 5, scale = 4)
    private BigDecimal    overallPassRate;

    @Column(name = "standard_pass_rate", precision = 5, scale = 4)
    private BigDecimal    standardPassRate;

    @Column(name = "surcharge_pass_rate", precision = 5, scale = 4)
    private BigDecimal    surchargePassRate;

    @Column(name = "exclusion_pass_rate", precision = 5, scale = 4)
    private BigDecimal    exclusionPassRate;

    @Column(name = "decline_rate", precision = 5, scale = 4)
    private BigDecimal    declineRate;

    // ========== 系统字段 ==========
    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "generated_by", length = 50, nullable = false)
    private String        generatedBy;

    // ========== JPA生命周期回调 ==========
    @PrePersist
    protected void onCreate() {
        if (generatedAt == null) {
            generatedAt = LocalDateTime.now();
        }
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        Class<?> oEffectiveClass = o instanceof HibernateProxy
                ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass()
                : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy
                ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass()
                : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) {
            return false;
        }
        UnderwritingStatisticsEntity that = (UnderwritingStatisticsEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy
                ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode()
                : getClass().hashCode();
    }
}
