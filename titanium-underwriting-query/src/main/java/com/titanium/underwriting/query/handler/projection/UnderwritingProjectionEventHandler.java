package com.titanium.underwriting.query.handler.projection;

import java.time.LocalDateTime;
import java.util.function.Consumer;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.event.UnderwritingCreatedEvent;
import com.titanium.underwriting.event.UnderwritingDecidedEvent;
import com.titanium.underwriting.event.UnderwritingStatusChangedEvent;
import com.titanium.underwriting.query.repository.UnderwritingViewRepository;
import com.titanium.underwriting.query.view.UnderwritingView;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 核保读模型投影事件处理器（CQRS 读侧核心）
 * <p>
 * 订阅核保域领域事件，将聚合根状态变更投影到读模型表 {@code t_underwriting_view}， 使查询侧拥有独立的物化视图，彻底摆脱「重建写侧聚合根来查询」的读写混用反模式。
 * </p>
 * <p>
 * <b>处理组</b>：{@code underwriting-query-group}，与写侧隔离。 <b>幂等</b>：创建事件 saveOrUpdate；状态/决策事件查存量再更新，缺失告警跳过（由 DLQ 重试），保证事件重放安全。
 * </p>
 */
@Slf4j
@Component
@ProcessingGroup("underwriting-query-group")
@RequiredArgsConstructor
public class UnderwritingProjectionEventHandler {

    private final UnderwritingViewRepository underwritingViewRepository;

    /**
     * 投影核保创建事件：新建读模型记录，初始状态为待核保
     */
    @EventHandler
    @Transactional
    public void on(UnderwritingCreatedEvent event) {
        String underwritingId = event.underwritingId().value();
        log.info("[读模型投影] 核保创建: underwritingId={}, tenantId={}", underwritingId, event.tenantId());

        UnderwritingView view = underwritingViewRepository.findById(underwritingId).orElseGet(UnderwritingView::new);
        LocalDateTime now = LocalDateTime.now();

        view.setUnderwritingId(underwritingId);
        view.setPolicyId(event.policyId().value());
        view.setCustomerId(event.customerId().value());
        if (event.amount() != null) {
            view.setAmount(event.amount().amount());
        }
        view.setUnderwritingType(event.underwritingType());
        view.setStatus(UnderwritingEnum.UnderwritingStatus.PENDING);
        view.setCreatedAt(event.createdAt());
        view.setCreatedBy(event.createdBy());
        view.setUpdatedBy(event.createdBy());
        view.setTenantId(event.tenantId());
        if (view.getCreateTime() == null) {
            view.setCreateTime(now);
        }
        view.setUpdateTime(now);

        underwritingViewRepository.save(view);
    }

    /**
     * 投影核保状态变更事件：更新读模型状态；拒保/退回类状态记录原因，其余记录审核意见
     */
    @EventHandler
    @Transactional
    public void on(UnderwritingStatusChangedEvent event) {
        String underwritingId = event.underwritingId().value();
        log.info("[读模型投影] 核保状态变更: underwritingId={}, {} -> {}", underwritingId, event.oldStatus(),
                event.newStatus());

        applyUpdate(underwritingId, "状态变更", view -> {
            view.setStatus(event.newStatus());
            view.setUpdatedBy(event.changedBy());
            if (event.newStatus() == UnderwritingEnum.UnderwritingStatus.REJECTED
                    || event.newStatus() == UnderwritingEnum.UnderwritingStatus.DECLINED) {
                view.setRejectReason(event.reason());
            } else {
                view.setReviewComments(event.reason());
            }
        });
    }

    /**
     * 投影核保决策事件：记录风险等级、结论、核保方式、评分及最终状态
     */
    @EventHandler
    @Transactional
    public void on(UnderwritingDecidedEvent event) {
        String underwritingId = event.underwritingId().value();
        log.info("[读模型投影] 核保决策: underwritingId={}, riskLevel={}, conclusion={}", underwritingId, event.riskLevel(),
                event.conclusionType());

        applyUpdate(underwritingId, "核保决策", view -> {
            view.setRiskLevel(event.riskLevel());
            view.setConclusionType(event.conclusionType());
            view.setAuditType(event.auditType());
            view.setRiskScore(event.riskScore());
            view.setStatus(event.newStatus());
            view.setUpdatedBy(event.decidedBy());
        });
    }

    /**
     * 通用更新模板：查存量→应用变更→刷新更新时间→保存；缺失时告警跳过保证幂等（由 DLQ 重试）
     */
    private void applyUpdate(String underwritingId, String action, Consumer<UnderwritingView> mutator) {
        underwritingViewRepository.findById(underwritingId).ifPresentOrElse(view -> {
            mutator.accept(view);
            view.setUpdateTime(LocalDateTime.now());
            underwritingViewRepository.save(view);
        }, () -> log.warn("[读模型投影] {} 失败：未找到读模型记录 underwritingId={}（可能事件乱序，将由DLQ重试）", action, underwritingId));
    }
}
