package com.titanium.underwriting.repository;

import java.util.Optional;

import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.springframework.stereotype.Repository;

import com.titanium.underwriting.event.UnderwritingDecidedEvent;
import com.titanium.underwriting.valueobject.UnderwritingId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 核保单事件流只读出口的 Axon 实现（{@code EventStore} 直读，无投影参与）。
 *
 * <p>
 * 🔴 <b>归属 infrastructure</b>：本类是 Port（{@link UnderwritingEventStreamRepository}，定义于 domain）的
 * Adapter，按根规约 §3.4.5 必须落在基础设施层，不得放 application。本域写侧是纯事件溯源聚合，故直接读事件流，
 * 不经任何 JPA 写表。
 * </p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class EventStoreUnderwritingEventStreamRepository implements UnderwritingEventStreamRepository {

    private final EventStore eventStore;

    /**
     * 判定核保单是否已在事件流中创建。
     *
     * <p>
     * 🔴 <b>降级方向是「按存在处理」</b>：事件流读不出时若判为不存在，编排器会静默<b>新建第二张核保单</b>
     * （业务上不可接受的重复核保）；判为存在则编排器走续跑，由后续命令以显式异常失败——调用方可重试，
     * 且不产生任何脏数据。宁可报错，不可静默重复。
     * </p>
     *
     * <p>
     * 依据 Axon {@code EventStore#readEvents} 契约：「若事件存储中没有该聚合的事件，返回<b>空流</b>」
     * ——故「不存在」由空流表达，{@code catch} 分支只兜基础设施故障（存储不可达等），不承担正常路径。
     * </p>
     */
    @Override
    public boolean exists(UnderwritingId underwritingId) {
        if (underwritingId == null || underwritingId.value() == null) {
            // ID 缺失是调用方缺陷，无聚合可言：判不存在比抛异常更贴近「没有这张单」，与 findLatestDecision 同口径
            return false;
        }
        String aggregateId = underwritingId.value();
        try {
            return eventStore.readEvents(aggregateId).hasNext();
        } catch (Exception ex) {
            log.warn("[核保事件流] 存在性判定失败，按「已存在」处理以避免重复建单: underwritingId={}, cause={}",
                    aggregateId, ex.getMessage());
            return true;
        }
    }

    /**
     * 取最近一次核保决策事件。
     *
     * <p>
     * 🔴 <b>降级方向是「返回空」</b>——与 {@link #exists} 的 {@code true} 形似矛盾、实则同一原则：两者都使
     * 编排器走「续跑 / 不新建」，即<b>永不静默产生重复核保单</b>（详见 Port 类注释）。
     * 读到中途失败时同样按「无结论」处理，理由相同。
     * </p>
     */
    @Override
    public Optional<UnderwritingDecidedEvent> findLatestDecision(UnderwritingId underwritingId) {
        if (underwritingId == null || underwritingId.value() == null) {
            return Optional.empty();
        }
        String aggregateId = underwritingId.value();
        try {
            DomainEventStream stream = eventStore.readEvents(aggregateId);
            UnderwritingDecidedEvent latest = null;
            while (stream.hasNext()) {
                if (stream.next().getPayload() instanceof UnderwritingDecidedEvent decided) {
                    latest = decided;
                }
            }
            return Optional.ofNullable(latest);
        } catch (Exception ex) {
            log.warn("[核保决策事件] 事件流读取失败，幂等复用旁路降级: underwritingId={}, cause={}",
                    aggregateId, ex.getMessage());
            return Optional.empty();
        }
    }
}
