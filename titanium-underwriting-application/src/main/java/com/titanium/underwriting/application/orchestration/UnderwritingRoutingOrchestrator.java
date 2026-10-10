package com.titanium.underwriting.application.orchestration;

import java.math.BigDecimal;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventsourcing.EventSourcedAggregate;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.axonframework.modelling.command.LockAwareAggregate;
import org.springframework.stereotype.Component;

import com.titanium.common.exception.BusinessException;
import com.titanium.metadata.errorcode.UnderwritingErrorCode;
import com.titanium.underwriting.aggregate.Underwriting;
import com.titanium.underwriting.command.UnderwriteCommand;
import com.titanium.underwriting.event.UnderwritingStatusChangedEvent;
import com.titanium.underwriting.port.product.ProductUnderwritingConfigPort;
import com.titanium.underwriting.port.product.ProductUnderwritingConfigPort.ProductUnderwritingConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 核保执行金额路由编排器（g02-02：人工复核金额阈值权威化到产品配置）
 * <p>
 * 编排职责（application/orchestration，无业务规则——判定仍全部在聚合根
 * {@code Underwriting#determineUnderwritingStatus}）：
 * <ol>
 *   <li>加载核保聚合，取险种编码（命令不携带，仅聚合持有）；</li>
 *   <li>经 {@link ProductUnderwritingConfigPort} 取产品核保配置，读其
 *       {@code manualReviewAmountThreshold}；</li>
 *   <li>阈值缺失（产品未配置 / 产品域取不到 / 无险种编码）时 <b>显式告警</b>——本次判定将落在
 *       核保域兜底默认阈值上，该情形必须可从结构化日志检索；</li>
 *   <li>充实 {@link UnderwriteCommand} 后派发聚合根。</li>
 * </ol>
 * </p>
 * <p>
 * 🔴 <b>null 阈值 ≠ 「不限」</b>：本域 {@code ProductUnderwritingConfig#manualReviewAmountThreshold}
 * 的 null 语义是「未配置」（见该类 javadoc），聚合根据此回退兜底默认阈值。刻意<b>不在此处</b>做
 * null→默认值的替换——替换若发生在此，产品配置与兜底值在事件流与日志中不可区分；放在聚合根则
 * 「判定入口唯一」，本编排器只负责取数、透传与留痕。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnderwritingRoutingOrchestrator {

    private final CommandGateway                            commandGateway;
    private final ProductUnderwritingConfigPort             productUnderwritingConfigPort;
    private final EventSourcingRepository<Underwriting>     underwritingRepository;

    /**
     * 金额路由编排入口：取产品阈值 → 充实命令 → 派发执行核保。
     *
     * @param command 执行核保命令（web/api 层构造，manualReviewAmountThreshold 待充实）
     * @return 核保状态变更事件（聚合根产出）
     */
    public UnderwritingStatusChangedEvent route(UnderwriteCommand command) {
        String productCode = loadProductCode(command);
        ProductUnderwritingConfig config = productUnderwritingConfigPort.fetchConfig(productCode, command.tenantId());
        BigDecimal manualReviewAmountThreshold = config.manualReviewAmountThreshold();
        if (manualReviewAmountThreshold == null) {
            // 兜底启用显式留痕：产品未配置阈值时风控按兜底默认执行（沿用 m11-1404「来源显式化 + warn」先例）
            log.warn("[核保路由] 产品未提供人工复核金额阈值，本次判定回退核保域兜底默认阈值: configSource={}, "
                    + "productCode={}, underwritingId={}", config.configSource(), productCode,
                    command.underwritingId());
        } else {
            log.info("[核保路由] 人工复核金额阈值取自产品配置: threshold={}, configSource={}, productCode={}, "
                    + "underwritingId={}", manualReviewAmountThreshold, config.configSource(), productCode,
                    command.underwritingId());
        }

        UnderwriteCommand enriched = new UnderwriteCommand(command.underwritingId(), command.amount(),
                command.reason(), command.processedBy(), command.tenantId(), manualReviewAmountThreshold);
        return commandGateway.sendAndWait(enriched);
    }

    /**
     * 加载核保聚合并取其险种编码（事件溯源仓储的 load 必须在 UnitOfWork 上下文中执行；
     * 聚合对象不跨 UoW 生命周期使用，故只取出不可变的险种编码字符串）。
     * 聚合不存在时转为结构化业务错误。
     *
     * @param command 执行核保命令（携带聚合标识）
     * @return 险种编码（产品未记录险种编码时为 null，由 adapter 归为「未配置」）
     */
    private String loadProductCode(UnderwriteCommand command) {
        DefaultUnitOfWork<?> unitOfWork = DefaultUnitOfWork.startAndGet(null);
        try {
            LockAwareAggregate<Underwriting, EventSourcedAggregate<Underwriting>> loaded =
                    underwritingRepository.load(command.underwritingId().value());
            return loaded.getWrappedAggregate().getAggregateRoot().getProductCode();
        } catch (AggregateNotFoundException ex) {
            log.warn("[核保路由] 核保案件聚合不存在: underwritingId={}", command.underwritingId());
            throw new BusinessException("核保案件不存在: " + command.underwritingId(),
                    UnderwritingErrorCode.UNDERWRITING_CASE_NOT_FOUND);
        } finally {
            unitOfWork.rollback();
        }
    }
}
