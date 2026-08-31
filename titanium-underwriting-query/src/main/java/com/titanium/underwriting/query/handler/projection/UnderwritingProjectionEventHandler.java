package com.titanium.underwriting.query.handler.projection;

import java.time.LocalDateTime;
import java.util.function.Consumer;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.common.jpa.BasePersistable;
import com.titanium.metadata.errorcode.UnderwritingErrorCode;
import com.titanium.underwriting.common.exception.UnderwritingException;
import com.titanium.underwriting.event.UnderwritingCreatedEvent;
import com.titanium.underwriting.event.UnderwritingDecidedEvent;
import com.titanium.underwriting.event.UnderwritingStatusChangedEvent;
import com.titanium.underwriting.query.mapper.UnderwritingViewMapper;
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
 * <b>处理组</b>：{@code underwriting-query-group}，与写侧隔离。 <b>幂等</b>：创建事件 saveOrUpdate；状态/决策事件查存量再更新，缺失时抛错进入重试或 DLQ，保证事件不会静默丢失。
 * </p>
 */
@Slf4j
@Component
@ProcessingGroup("underwriting-query-group")
@RequiredArgsConstructor
public class UnderwritingProjectionEventHandler {

    private final UnderwritingViewRepository underwritingViewRepository;
    private final UnderwritingViewMapper     underwritingViewMapper;

    /**
     * 投影核保创建事件：新建读模型记录，初始状态为待核保
     */
    @EventHandler
    @Transactional
    public void on(UnderwritingCreatedEvent event) {
        String underwritingId = event.underwritingId().value();
        log.info("[读模型投影] 核保创建: underwritingId={}, tenantId={}", underwritingId, event.tenantId());

        UnderwritingView view = underwritingViewRepository.findById(underwritingId).orElseGet(UnderwritingView::new);

        // 事件字段 → 读模型的结构映射收敛到 MapStruct（值对象拆包、初始状态 PENDING），消除逐字段 set
        underwritingViewMapper.applyCreated(view, event);
        stampAuditTime(view);

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

        // 事件 → 读模型的结构映射收敛到 MapStruct（状态/拒保原因/审核意见互斥由事件级空安全转换承载）
        applyUpdate(underwritingId, "状态变更", view -> underwritingViewMapper.applyStatusChanged(view, event));
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

        // UW-3：风险/结论/评分/状态及结构化加费随决策事件投影（映射器空安全转换 + IGNORE 保持既有值）
        applyUpdate(underwritingId, "核保决策", view -> underwritingViewMapper.applyDecided(view, event));
    }

    /**
     * 通用更新模板：查存量→应用变更→刷新更新时间→保存；缺失时抛错进入重试或 DLQ
     */
    private void applyUpdate(String underwritingId, String action, Consumer<UnderwritingView> mutator) {
        underwritingViewRepository.findById(underwritingId).ifPresentOrElse(view -> {
            mutator.accept(view);
            view.setUpdateTime(LocalDateTime.now());
            underwritingViewRepository.save(view);
        }, () -> {
            throw new UnderwritingException(UnderwritingErrorCode.PROJECTION_TARGET_MISSING,
                    "[读模型投影] " + action + " 失败：未找到读模型记录 underwritingId=" + underwritingId);
        });
    }

    /**
     * 统一填充读模型审计时间戳：createTime 仅首次创建时写入、updateTime 每次投影刷新。
     * <p>
     * 该逻辑含 {@code now()} 运行时副作用与"仅首次设置"语义，属投影处理器职责，不下沉 MapStruct 映射器。
     * {@code UnderwritingView} 继承 {@code BasePersistable}，以基类承接与账单域投影一致。
     * </p>
     */
    private void stampAuditTime(BasePersistable view) {
        LocalDateTime now = LocalDateTime.now();
        if (view.getCreateTime() == null) {
            view.setCreateTime(now);
        }
        view.setUpdateTime(now);
    }
}
