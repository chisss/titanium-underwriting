package com.titanium.underwriting.infrastructure.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.common.jpa.BaseEntity;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 核保实体类
 * <p>
 * 继承 {@link BaseEntity}，复用租户ID/创建更新时间/创建更新人/逻辑删除字段，
 * 原 created_at/updated_at 统一为基类 create_time/update_time。
 * </p>
 */
@Entity
@Table(name = "t_underwriting")
@Getter
@Setter
@NoArgsConstructor
public class UnderwritingEntity extends BaseEntity {
    @Id
    @Column(name = "underwriting_id", length = 36, nullable = false)
    private String        underwritingId;

    @Column(name = "policy_id", length = 36, nullable = false)
    private String        policyId;

    @Column(name = "customer_id", length = 36, nullable = false)
    private String        customerId;

    @Column(name = "amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal    amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "underwriting_type", length = 50, nullable = false)
    private UnderwritingEnum.UnderwritingType underwritingType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private UnderwritingEnum.UnderwritingStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_result", length = 20)
    private UnderwritingEnum.ConclusionType reviewResult;

    @Column(name = "review_reason", columnDefinition = "TEXT")
    private String        reviewReason;

    @Column(name = "reviewer_id", length = 36)
    private String        reviewerId;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
}
