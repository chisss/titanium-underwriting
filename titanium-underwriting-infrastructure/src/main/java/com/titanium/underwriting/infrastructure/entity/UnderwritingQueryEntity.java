package com.titanium.underwriting.infrastructure.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

import org.hibernate.proxy.HibernateProxy;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;

/**
 * 核保查询实体 - Infrastructure层数据库实体 用于数据库持久化 基于保险业务全生命周期核保承保阶段的关键信息设计
 * 符合项目规约第13条：所有数据表中必须包含tenant_id字段
 */
@Entity
@Table(name = "t_underwriting_query", indexes = { 
        @Index(name = "idx_underwriting_query_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_underwriting_query_policy_id", columnList = "policy_id, tenant_id"),
        @Index(name = "idx_underwriting_query_customer_id", columnList = "customer_id, tenant_id"),
        @Index(name = "idx_underwriting_query_status", columnList = "status, tenant_id"),
        @Index(name = "idx_underwriting_query_risk_level", columnList = "risk_level, tenant_id"),
        @Index(name = "idx_underwriting_query_underwriter", columnList = "underwriter_id, tenant_id"),
        @Index(name = "idx_underwriting_query_created_at", columnList = "created_at, tenant_id"),
        @Index(name = "idx_underwriting_query_audit_type", columnList = "audit_type, tenant_id") })
@Data
public class UnderwritingQueryEntity {

    // ========== 主键和基础信息 ==========
    @Id
    @Column(name = "underwriting_id", length = 50)
    private String                              underwritingId;

    @Column(name = "policy_id", length = 50, nullable = false)
    private String                              policyId;

    @Column(name = "customer_id", length = 50, nullable = false)
    private String                              customerId;

    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal                          amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "underwriting_type", length = 100)
    private UnderwritingEnum.UnderwritingType   underwritingType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private UnderwritingEnum.UnderwritingStatus status;

    @Column(name = "reject_reason", length = 1000)
    private String                              rejectReason;

    @Column(name = "review_comments", length = 2000)
    private String                              reviewComments;

    // ========== 核保依据相关信息 ==========
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 50)
    private UnderwritingEnum.RiskLevel          riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "conclusion_type", length = 50)
    private UnderwritingEnum.ConclusionType     conclusionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "audit_type", length = 50)
    private UnderwritingEnum.AuditType          auditType;

    @Column(name = "underwriter_id", length = 50)
    private String                              underwriterId;

    @Column(name = "underwriter_name", length = 100)
    private String                              underwriterName;

    // ========== 风险评估结果 ==========
    @Column(name = "risk_factors", length = 2000)
    private String                              riskFactors;

    @Column(name = "premium_surcharge_rate", precision = 5, scale = 4)
    private BigDecimal                          premiumSurchargeRate;

    @Column(name = "surcharge_reason", length = 1000)
    private String                              surchargeReason;

    @Column(name = "exclusions", length = 2000)
    private String                              exclusions;

    @Column(name = "postpone_period_months")
    private Integer                             postponePeriodMonths;

    @Column(name = "postpone_reason", length = 1000)
    private String                              postponeReason;

    // ========== 第三方数据相关 ==========
    @Column(name = "medical_exam_id", length = 50)
    private String                              medicalExamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "medical_exam_status", length = 50)
    private UnderwritingEnum.MedicalExamStatus  medicalExamStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "investigation_result", length = 2000)
    private UnderwritingEnum.InvestigationResult investigationResult;

    @Column(name = "industry_decline_record", length = 2000)
    private String                              industryDeclineRecord;

    // ========== 核保流程相关 ==========
    @Column(name = "underwriting_start_time")
    private LocalDateTime                       underwritingStartTime;

    @Column(name = "underwriting_completed_time")
    private LocalDateTime                       underwritingCompletedTime;

    @Column(name = "processing_hours")
    private Integer                             processingHours;

    @Column(name = "requires_review")
    private Boolean                             requiresReview;

    @Column(name = "reviewer_id", length = 50)
    private String                              reviewerId;

    @Column(name = "reviewer_comments", length = 2000)
    private String                              reviewerComments;

    // ========== 保费相关 ==========
    @Column(name = "base_premium", precision = 15, scale = 2)
    private BigDecimal                          basePremium;

    @Column(name = "additional_premium", precision = 15, scale = 2)
    private BigDecimal                          additionalPremium;

    @Column(name = "final_premium", precision = 15, scale = 2)
    private BigDecimal                          finalPremium;

    @Column(name = "discount_amount", precision = 15, scale = 2)
    private BigDecimal                          discountAmount;

    // ========== 系统字段 ==========
    @Column(name = "created_at", nullable = false)
    private LocalDateTime                       createdAt;

    @Column(name = "created_by", length = 50, nullable = false)
    private String                              createdBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime                       updatedAt;

    @Column(name = "updated_by", length = 50, nullable = false)
    private String                              updatedBy;

    @Column(name = "tenant_id", length = 50, nullable = false)
    private String                              tenantId;

    @Version
    @Column(name = "version")
    private Long                                version;

    // ========== JPA生命周期回调 ==========
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null)
            return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy
                ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass()
                : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy
                ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass()
                : this.getClass();
        if (thisEffectiveClass != oEffectiveClass)
            return false;
        UnderwritingQueryEntity that = (UnderwritingQueryEntity) o;
        return getUnderwritingId() != null && Objects.equals(getUnderwritingId(), that.getUnderwritingId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy
                ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode()
                : getClass().hashCode();
    }
}