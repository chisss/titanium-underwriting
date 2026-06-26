package com.titanium.underwriting.domain.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.domain.aggregate.Underwriting;
import com.titanium.underwriting.domain.valueobject.CustomerId;
import com.titanium.underwriting.domain.valueobject.PolicyId;
import com.titanium.underwriting.domain.valueobject.UnderwritingId;

/**
 * Underwriting Repository Interface
 */
@Repository
public interface UnderwritingRepository
        extends PagingAndSortingRepository<Underwriting, UnderwritingId>, CrudRepository<Underwriting, UnderwritingId> {

    /**
     * Find underwriting by ID and tenant ID
     *
     * @param underwritingId the underwriting ID
     * @param tenantId the tenant ID
     * @return the underwriting
     */
    Optional<Underwriting> findByIdAndTenantId(UnderwritingId underwritingId, String tenantId);

    /**
     * Find underwriting by policy ID and tenant ID
     *
     * @param policyId the policy ID
     * @param tenantId the tenant ID
     * @return the underwriting
     */
    Optional<Underwriting> findByPolicyIdAndTenantId(PolicyId policyId, String tenantId);

    /**
     * Find underwritings by customer ID and tenant ID
     *
     * @param customerId the customer ID
     * @param tenantId the tenant ID
     * @param pageable pagination information
     * @return the page of underwritings
     */
    Page<Underwriting> findByCustomerIdAndTenantId(CustomerId customerId, String tenantId, Pageable pageable);

    /**
     * Find underwritings by status and tenant ID
     *
     * @param status the underwriting status
     * @param tenantId the tenant ID
     * @param pageable pagination information
     * @return the page of underwritings
     */
    Page<Underwriting> findByStatusAndTenantId(UnderwritingEnum.UnderwritingStatus status, String tenantId,
                                               Pageable pageable);

    /**
     * Find underwritings by customer ID, status and tenant ID
     *
     * @param customerId the customer ID
     * @param status the underwriting status
     * @param tenantId the tenant ID
     * @param pageable pagination information
     * @return the page of underwritings
     */
    Page<Underwriting> findByCustomerIdAndStatusAndTenantId(CustomerId customerId,
                                                            UnderwritingEnum.UnderwritingStatus status, String tenantId,
                                                            Pageable pageable);

    /**
     * Find underwritings created between two dates and tenant ID
     *
     * @param startDate the start date
     * @param endDate the end date
     * @param tenantId the tenant ID
     * @param pageable pagination information
     * @return the page of underwritings
     */
    Page<Underwriting> findByCreatedAtBetweenAndTenantId(LocalDateTime startDate, LocalDateTime endDate,
                                                         String tenantId, Pageable pageable);

    /**
     * Find underwritings by underwriting type and tenant ID
     *
     * @param underwritingType the underwriting type
     * @param tenantId the tenant ID
     * @param pageable pagination information
     * @return the page of underwritings
     */
    Page<Underwriting> findByUnderwritingTypeAndTenantId(UnderwritingEnum.UnderwritingType underwritingType,
                                                         String tenantId, Pageable pageable);

    /**
     * Count underwritings by status and tenant ID
     *
     * @param status the underwriting status
     * @param tenantId the tenant ID
     * @return the count
     */
    long countByStatusAndTenantId(UnderwritingEnum.UnderwritingStatus status, String tenantId);
}
