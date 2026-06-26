package com.titanium.underwriting.infrastructure.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.domain.aggregate.Underwriting;
import com.titanium.underwriting.domain.repository.UnderwritingRepository;
import com.titanium.underwriting.domain.valueobject.CustomerId;
import com.titanium.underwriting.domain.valueobject.PolicyId;
import com.titanium.underwriting.domain.valueobject.UnderwritingId;

import lombok.RequiredArgsConstructor;

/**
 * 核保仓储实现类
 */
@Repository
@RequiredArgsConstructor
public class UnderwritingRepositoryImpl implements UnderwritingRepository {

    @Override
    public Optional<Underwriting> findByIdAndTenantId(UnderwritingId underwritingId, String tenantId) {
        return Optional.empty();
    }

    @Override
    public Optional<Underwriting> findByPolicyIdAndTenantId(PolicyId policyId, String tenantId) {
        return Optional.empty();
    }

    @Override
    public Page<Underwriting> findByCustomerIdAndTenantId(CustomerId customerId, String tenantId, Pageable pageable) {
        return null;
    }

    @Override
    public Page<Underwriting> findByStatusAndTenantId(UnderwritingEnum.UnderwritingStatus status, String tenantId,
                                                      Pageable pageable) {
        return null;
    }

    @Override
    public Page<Underwriting> findByCustomerIdAndStatusAndTenantId(CustomerId customerId,
                                                                   UnderwritingEnum.UnderwritingStatus status,
                                                                   String tenantId, Pageable pageable) {
        return null;
    }

    @Override
    public Page<Underwriting> findByCreatedAtBetweenAndTenantId(LocalDateTime startDate, LocalDateTime endDate,
                                                                String tenantId, Pageable pageable) {
        return null;
    }

    @Override
    public Page<Underwriting> findByUnderwritingTypeAndTenantId(UnderwritingEnum.UnderwritingType underwritingType,
                                                                String tenantId, Pageable pageable) {
        return null;
    }

    @Override
    public long countByStatusAndTenantId(UnderwritingEnum.UnderwritingStatus status, String tenantId) {
        return 0;
    }

    @Override
    public <S extends Underwriting> S save(S entity) {
        return null;
    }

    @Override
    public <S extends Underwriting> Iterable<S> saveAll(Iterable<S> entities) {
        return null;
    }

    @Override
    public Optional<Underwriting> findById(UnderwritingId underwritingId) {
        return Optional.empty();
    }

    @Override
    public boolean existsById(UnderwritingId underwritingId) {
        return false;
    }

    @Override
    public Iterable<Underwriting> findAll() {
        return null;
    }

    @Override
    public Iterable<Underwriting> findAllById(Iterable<UnderwritingId> underwritingIds) {
        return null;
    }

    @Override
    public long count() {
        return 0;
    }

    @Override
    public void deleteById(UnderwritingId underwritingId) {

    }

    @Override
    public void delete(Underwriting entity) {

    }

    @Override
    public void deleteAllById(Iterable<? extends UnderwritingId> underwritingIds) {

    }

    @Override
    public void deleteAll(Iterable<? extends Underwriting> entities) {

    }

    @Override
    public void deleteAll() {

    }

    @Override
    public Iterable<Underwriting> findAll(Sort sort) {
        return null;
    }

    @Override
    public Page<Underwriting> findAll(Pageable pageable) {
        return null;
    }
}
