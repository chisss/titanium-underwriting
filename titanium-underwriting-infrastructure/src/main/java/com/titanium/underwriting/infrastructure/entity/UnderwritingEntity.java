package com.titanium.underwriting.infrastructure.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 核保实体类
 */
@Entity
@Table(name = "t_underwriting")
@Data
@NoArgsConstructor
public class UnderwritingEntity {
    @Id
    @Column(name = "underwriting_id", length = 36, nullable = false)
    private String        underwritingId;

    @Column(name = "policy_id", length = 36, nullable = false)
    private String        policyId;

    @Column(name = "customer_id", length = 36, nullable = false)
    private String        customerId;

    @Column(name = "amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal    amount;

    @Column(name = "underwriting_type", length = 50, nullable = false)
    private String        underwritingType;

    @Column(name = "status", length = 20, nullable = false)
    private String        status;

    @Column(name = "review_result", length = 20)
    private String        reviewResult;

    @Column(name = "review_reason", columnDefinition = "TEXT")
    private String        reviewReason;

    @Column(name = "reviewer_id", length = 36)
    private String        reviewerId;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "tenant_id", length = 36, nullable = false)
    private String        tenantId;

    @Column(name = "created_by", length = 50, nullable = false)
    private String        createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_by", length = 50, nullable = false)
    private String        updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
