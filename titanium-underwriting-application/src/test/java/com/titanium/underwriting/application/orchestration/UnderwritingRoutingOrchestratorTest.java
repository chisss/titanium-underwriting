package com.titanium.underwriting.application.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventsourcing.EventSourcedAggregate;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.axonframework.modelling.command.LockAwareAggregate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.titanium.common.exception.BusinessException;
import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.aggregate.Underwriting;
import com.titanium.underwriting.command.UnderwriteCommand;
import com.titanium.underwriting.common.enums.ProductConfigSource;
import com.titanium.underwriting.event.UnderwritingStatusChangedEvent;
import com.titanium.underwriting.port.product.ProductUnderwritingConfigPort;
import com.titanium.underwriting.port.product.ProductUnderwritingConfigPort.ProductUnderwritingConfig;
import com.titanium.underwriting.valueobject.UnderwritingAmount;
import com.titanium.underwriting.valueobject.UnderwritingId;

/**
 * 核保金额路由编排器测试（g02-02 / AC-03：阈值取产品配置并充实进命令）
 * <p>
 * 锁死三条语义：① 产品配置的阈值被读入并写入派发命令；② 产品未配置时命令携带 <b>null</b>——
 * 兜底值由聚合根给出，编排器<b>不做</b> null→默认值替换（否则产品值与兜底值在事件流/日志中不可区分）；
 * ③ 聚合不存在时转结构化业务错误，不静默走无产品依据的路径。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class UnderwritingRoutingOrchestratorTest {

    private static final UnderwritingId UNDERWRITING_ID = new UnderwritingId("UW-001");
    private static final String         TENANT_ID       = "TENANT-001";
    private static final String         PRODUCT_CODE    = "PRD-001";
    private static final BigDecimal     THRESHOLD       = BigDecimal.valueOf(500_000);

    @Mock
    private CommandGateway                        commandGateway;

    @Mock
    private ProductUnderwritingConfigPort         productUnderwritingConfigPort;

    @Mock
    private EventSourcingRepository<Underwriting> underwritingRepository;

    @InjectMocks
    private UnderwritingRoutingOrchestrator       orchestrator;

    @Captor
    private ArgumentCaptor<UnderwriteCommand>     commandCaptor;

    @Test
    void routePassesProductConfiguredThresholdIntoDispatchedCommand() {
        givenAggregateProductCode(PRODUCT_CODE);
        when(productUnderwritingConfigPort.fetchConfig(PRODUCT_CODE, TENANT_ID))
                .thenReturn(new ProductUnderwritingConfig(true, THRESHOLD, null, ProductConfigSource.CONFIGURED));
        UnderwritingStatusChangedEvent dispatched = new UnderwritingStatusChangedEvent(UNDERWRITING_ID, null,
                UnderwritingEnum.UnderwritingStatus.APPROVED, "自动核保",
                null, "system", TENANT_ID);
        when(commandGateway.sendAndWait(any(UnderwriteCommand.class))).thenReturn(dispatched);

        UnderwritingStatusChangedEvent result = orchestrator.route(underwriteCommand());

        assertSame(dispatched, result);
        verify(commandGateway).sendAndWait(commandCaptor.capture());
        UnderwriteCommand enriched = commandCaptor.getValue();
        assertEquals(THRESHOLD, enriched.manualReviewAmountThreshold());
        // 产品阈值是唯一被充实的字段，其余命令字段必须原样透传
        assertEquals(UNDERWRITING_ID, enriched.underwritingId());
        // 保额按原值透传（金额值对象统一保留 2 位小数，故按数值比较而非 scale 敏感的 equals）
        assertEquals(0, BigDecimal.valueOf(600_000).compareTo(enriched.amount().amount()));
        assertEquals("自动核保", enriched.reason());
        assertEquals("system", enriched.processedBy());
        assertEquals(TENANT_ID, enriched.tenantId());
    }

    @Test
    void routePassesNullThresholdWhenProductNotConfiguredSoAggregateOwnsTheFallback() {
        givenAggregateProductCode(null);
        when(productUnderwritingConfigPort.fetchConfig(null, TENANT_ID))
                .thenReturn(ProductUnderwritingConfig.defaultConfig(ProductConfigSource.NOT_CONFIGURED));

        orchestrator.route(underwriteCommand());

        verify(commandGateway).sendAndWait(commandCaptor.capture());
        // 🔴 编排器不得把 null 替换成兜底默认值：替换发生在此，产品配置与兜底值将不可区分
        assertNull(commandCaptor.getValue().manualReviewAmountThreshold());
    }

    @Test
    void routeFailsWithStructuredErrorWhenAggregateMissing() {
        when(underwritingRepository.load(UNDERWRITING_ID.value()))
                .thenThrow(new AggregateNotFoundException(UNDERWRITING_ID.value(), "聚合不存在"));

        assertThrows(BusinessException.class, () -> orchestrator.route(underwriteCommand()));
    }

    @SuppressWarnings("unchecked")
    private void givenAggregateProductCode(String productCode) {
        LockAwareAggregate<Underwriting, EventSourcedAggregate<Underwriting>> lockAware = mock(LockAwareAggregate.class);
        EventSourcedAggregate<Underwriting> eventSourced = mock(EventSourcedAggregate.class);
        when(underwritingRepository.load(UNDERWRITING_ID.value())).thenReturn(lockAware);
        when(lockAware.getWrappedAggregate()).thenReturn(eventSourced);
        when(eventSourced.getAggregateRoot()).thenReturn(Underwriting.builder().productCode(productCode).build());
    }

    private UnderwriteCommand underwriteCommand() {
        return new UnderwriteCommand(UNDERWRITING_ID, UnderwritingAmount.of(BigDecimal.valueOf(600_000),
                CurrencyEnum.CNY), "自动核保", "system", TENANT_ID, null);
    }
}
