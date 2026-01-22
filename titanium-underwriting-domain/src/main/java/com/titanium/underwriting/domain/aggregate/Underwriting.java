package com.titanium.underwriting.domain.aggregate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.domain.command.CreateUnderwritingCommand;
import com.titanium.underwriting.domain.command.ManualReviewCommand;
import com.titanium.underwriting.domain.command.UnderwriteCommand;
import com.titanium.underwriting.domain.event.UnderwritingCreatedEvent;
import com.titanium.underwriting.domain.event.UnderwritingStatusChangedEvent;
import com.titanium.underwriting.domain.valueobject.CustomerId;
import com.titanium.underwriting.domain.valueobject.PolicyId;
import com.titanium.underwriting.domain.valueobject.UnderwritingAmount;
import com.titanium.underwriting.domain.valueobject.UnderwritingId;

import lombok.Getter;

/**
 * Underwriting Aggregate Root
 */
@Aggregate
@Builder(builderMethodName = "builder")
@AllArgsConstructor(access = AccessLevel.PRIVATE)  // 为 Builder 提供全参构造函数
@Getter
public class Underwriting {
    @AggregateIdentifier
    private UnderwritingId                      underwritingId;
    private PolicyId                            policyId;
    private CustomerId                          customerId;
    private UnderwritingAmount                  amount;
    private String                              underwritingType;
    private UnderwritingEnum.UnderwritingStatus status;
    private String                              rejectReason;
    private String                              reviewComments;
    private LocalDateTime                       createdAt;
    private String                              createdBy;
    private LocalDateTime                       updatedAt;
    private String                              updatedBy;

    // Command Handlers
    @CommandHandler
    public Underwriting(CreateUnderwritingCommand command) {
        // Validate command
        validateCreateCommand(command);

        // Publish event
        AggregateLifecycle.apply(new UnderwritingCreatedEvent(command.underwritingId(), command.policyId(),
                command.customerId(), command.amount(), command.underwritingType(), LocalDateTime.now(),
                command.createdBy(), command.tenantId()));
    }

    @CommandHandler
    public void handle(UnderwriteCommand command) {
        // Validate command
        validateUnderwriteCommand(command);

        // Determine new status based on business rules
        UnderwritingEnum.UnderwritingStatus newStatus = determineUnderwritingStatus(command);
        UnderwritingEnum.UnderwritingStatus oldStatus = this.status;

        // Publish status changed event
        AggregateLifecycle.apply(new UnderwritingStatusChangedEvent(command.underwritingId(), oldStatus, newStatus,
                command.reason(), LocalDateTime.now(), command.processedBy(), command.tenantId()));
    }

    private UnderwritingEnum.UnderwritingStatus determineUnderwritingStatus(UnderwriteCommand command) {
        // Simple business rule: if amount exceeds 100000, require manual review
        if (command.amount().getAmount().compareTo(BigDecimal.valueOf(100000)) > 0) {
            return UnderwritingEnum.UnderwritingStatus.REVIEW;
        }
        // Otherwise, approve automatically
        return UnderwritingEnum.UnderwritingStatus.APPROVED;
    }

    @CommandHandler
    public void handle(ManualReviewCommand command) {
        // Validate command
        validateManualReviewCommand(command);

        // Change status to manual review
        UnderwritingEnum.UnderwritingStatus oldStatus = this.status;
        UnderwritingEnum.UnderwritingStatus newStatus = UnderwritingEnum.UnderwritingStatus.MANUAL_REVIEW;

        // Publish status changed event
        AggregateLifecycle.apply(new UnderwritingStatusChangedEvent(command.underwritingId(), oldStatus, newStatus,
                command.reviewComments(), LocalDateTime.now(), command.reviewedBy(), command.tenantId()));
    }

    // Event Sourcing Handlers
    @EventSourcingHandler
    public void on(UnderwritingCreatedEvent event) {
        this.underwritingId = event.underwritingId();
        this.policyId = event.policyId();
        this.customerId = event.customerId();
        this.amount = event.amount();
        this.underwritingType = event.underwritingType();
        this.status = UnderwritingEnum.UnderwritingStatus.PENDING;
        this.createdAt = event.createdAt();
        this.createdBy = event.createdBy();
        this.updatedAt = event.createdAt();
        this.updatedBy = event.createdBy();
    }

    @EventSourcingHandler
    public void on(UnderwritingStatusChangedEvent event) {
        this.status = event.newStatus();
        if (UnderwritingEnum.UnderwritingStatus.REJECTED.equals(event.newStatus())) {
            this.rejectReason = event.reason();
        }
        if (UnderwritingEnum.UnderwritingStatus.MANUAL_REVIEW.equals(event.newStatus())) {
            this.reviewComments = event.reason();
        }
        this.updatedAt = event.changedAt();
        this.updatedBy = event.changedBy();
    }

    // Business Logic
    private void validateCreateCommand(CreateUnderwritingCommand command) {
        if (command.underwritingId() == null) {
            throw new IllegalArgumentException("Underwriting ID must not be null");
        }
        if (command.policyId() == null) {
            throw new IllegalArgumentException("Policy ID must not be null");
        }
        if (command.customerId() == null) {
            throw new IllegalArgumentException("Customer ID must not be null");
        }
        if (command.amount() == null) {
            throw new IllegalArgumentException("Underwriting amount must not be null");
        }
        if (command.underwritingType() == null || command.underwritingType().trim().isEmpty()) {
            throw new IllegalArgumentException("Underwriting type must not be empty");
        }
        if (command.createdBy() == null || command.createdBy().trim().isEmpty()) {
            throw new IllegalArgumentException("Created by must not be empty");
        }
        if (command.tenantId() == null || command.tenantId().trim().isEmpty()) {
            throw new IllegalArgumentException("Tenant ID must not be empty");
        }
    }

    private void validateUnderwriteCommand(UnderwriteCommand command) {
        if (!this.underwritingId.equals(command.underwritingId())) {
            throw new IllegalArgumentException("Underwriting ID mismatch");
        }
        if (command.processedBy() == null || command.processedBy().trim().isEmpty()) {
            throw new IllegalArgumentException("Processed by must not be empty");
        }
        if (command.reason() == null || command.reason().trim().isEmpty()) {
            throw new IllegalArgumentException("Reason must not be empty");
        }
        if (command.tenantId() == null || command.tenantId().trim().isEmpty()) {
            throw new IllegalArgumentException("Tenant ID must not be empty");
        }
    }

    private void validateManualReviewCommand(ManualReviewCommand command) {
        if (!this.underwritingId.equals(command.underwritingId())) {
            throw new IllegalArgumentException("Underwriting ID mismatch");
        }
        if (command.reviewedBy() == null || command.reviewedBy().trim().isEmpty()) {
            throw new IllegalArgumentException("Reviewed by must not be empty");
        }
        if (command.reviewComments() == null || command.reviewComments().trim().isEmpty()) {
            throw new IllegalArgumentException("Review comments must not be empty");
        }
        if (command.tenantId() == null || command.tenantId().trim().isEmpty()) {
            throw new IllegalArgumentException("Tenant ID must not be empty");
        }
    }

    /**
     * 默认构造函数
     * <p>
     * 受保护的默认构造函数，用于 Axon Framework 的反序列化和实例化
     */
    protected Underwriting() {
        // Required by Axon Framework
    }


}
