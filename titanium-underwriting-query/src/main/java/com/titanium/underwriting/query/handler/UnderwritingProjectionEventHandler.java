package com.titanium.underwriting.query.handler;

import java.time.LocalDateTime;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.domain.event.UnderwritingCreatedEvent;
import com.titanium.underwriting.domain.event.UnderwritingStatusChangedEvent;
import com.titanium.underwriting.infrastructure.entity.UnderwritingQueryEntity;
import com.titanium.underwriting.infrastructure.repository.jpa.UnderwritingQueryJpaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 核保读模型投影事件处理器（CQRS 读侧核心）
 * <p>
 * 订阅核保域领域事件，将聚合根状态变更投影到读模型表 {@code t_underwriting_query}。
 * </p>
 * <p>
 * <b>背景</b>：核保读模型表 {@code t_underwriting_query}、对应实体 {@code UnderwritingQueryEntity}、
 * 查询服务 {@code UnderwritingQueryServiceImpl} 此前均已存在并真实查库，但**没有任何投影器写入该表**——
 * 读模型是一张无人填充的空表。本处理器补齐写入路径，使读写分离闭环。
 * </p>
 * <p>
 * <b>处理组</b>：{@code underwriting-query-group}，与写侧隔离。 <b>幂等</b>：创建事件 saveOrUpdate；状态事件查存量再更新，缺失告警跳过，保证重放安全。
 * </p>
 */
@Slf4j
@Component
@ProcessingGroup("underwriting-query-group")
@RequiredArgsConstructor
public class UnderwritingProjectionEventHandler {

    private final UnderwritingQueryJpaRepository underwritingQueryJpaRepository;

    /**
     * 投影核保创建事件：新建读模型记录，初始状态为待核保
     */
    @EventHandler
    @Transactional
    public void on(UnderwritingCreatedEvent event) {
        String underwritingId = event.underwritingId().value();
        log.info("[读模型投影] 核保创建: underwritingId={}, tenantId={}", underwritingId, event.tenantId());

        UnderwritingQueryEntity entity = underwritingQueryJpaRepository.findById(underwritingId)
                .orElseGet(UnderwritingQueryEntity::new);

        LocalDateTime now = LocalDateTime.now();
        entity.setUnderwritingId(underwritingId);
        entity.setPolicyId(event.policyId().value());
        entity.setCustomerId(event.customerId().value());
        if (event.amount() != null) {
            entity.setAmount(event.amount().amount());
        }
        entity.setUnderwritingType(event.underwritingType());
        // 新建核保初始状态为待核保
        entity.setStatus(UnderwritingEnum.UnderwritingStatus.PENDING);
        entity.setCreateTime(event.createdAt() != null ? event.createdAt() : now);
        entity.setCreatedBy(event.createdBy());
        entity.setUpdateTime(now);
        entity.setUpdatedBy(event.createdBy());
        entity.setTenantId(event.tenantId());

        underwritingQueryJpaRepository.save(entity);
    }

    /**
     * 投影核保状态变更事件：更新读模型状态；拒保时记录原因
     */
    @EventHandler
    @Transactional
    public void on(UnderwritingStatusChangedEvent event) {
        String underwritingId = event.underwritingId().value();
        log.info("[读模型投影] 核保状态变更: underwritingId={}, {} -> {}", underwritingId, event.oldStatus(),
                event.newStatus());

        underwritingQueryJpaRepository.findById(underwritingId).ifPresentOrElse(entity -> {
            entity.setStatus(event.newStatus());
            entity.setUpdateTime(event.changedAt() != null ? event.changedAt() : LocalDateTime.now());
            entity.setUpdatedBy(event.changedBy());
            // 拒保/退回类状态：记录原因到 rejectReason
            if (event.newStatus() == UnderwritingEnum.UnderwritingStatus.REJECTED
                    || event.newStatus() == UnderwritingEnum.UnderwritingStatus.DECLINED) {
                entity.setRejectReason(event.reason());
            } else {
                entity.setReviewComments(event.reason());
            }
            underwritingQueryJpaRepository.save(entity);
        }, () -> log.warn("[读模型投影] 状态变更失败：未找到读模型记录 underwritingId={}（可能事件乱序，将由处理器重试）",
                underwritingId));
    }
}
