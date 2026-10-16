package com.titanium.underwriting.application.orchestration;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventsourcing.EventSourcedAggregate;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.modelling.command.LockAwareAggregate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.aggregate.Underwriting;
import com.titanium.underwriting.application.config.UnderwritingParameterProperties;
import com.titanium.underwriting.command.DecideUnderwritingCommand;
import com.titanium.underwriting.common.enums.ProductConfigSource;
import com.titanium.underwriting.event.UnderwritingDecidedEvent;
import com.titanium.underwriting.port.featurecenter.FeatureCenterPort;
import com.titanium.underwriting.port.product.ProductUnderwritingConfigPort;
import com.titanium.underwriting.port.product.ProductUnderwritingConfigPort.ProductUnderwritingConfig;
import com.titanium.underwriting.port.ruleengine.RuleEngineServicePort;
import com.titanium.underwriting.service.RuleConclusionMappingService;
import com.titanium.underwriting.valueobject.UnderwritingAmount;
import com.titanium.underwriting.valueobject.UnderwritingId;
import com.titanium.underwriting.valueobject.UnderwritingParameter;

/**
 * 核保决策编排器测试（DSN-09 / m25-11：租户风险参数充实进决策命令）
 * <p>
 * 🔴 判别点：命令派发前必须把 {@code parameterProperties.resolve(tenantId)} 的租户参数写入
 * {@link DecideUnderwritingCommand#riskParameter()}——这是「web 决策入口 → 聚合」链路上租户
 * 覆盖生效的唯一通道（聚合对 null 只做 DEFAULT 回退，编排器不充实则租户覆盖静默失效）。
 * 规则引擎未接入（{@code defaultConfig}）时的内置评分路径为本测试面。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class UnderwritingDecisionOrchestratorTest {

    private static final UnderwritingId UNDERWRITING_ID = new UnderwritingId("UW-001");
    private static final String         TENANT_ID       = "TENANT-001";
    private static final String         PRODUCT_CODE    = "PRD-001";

    @Mock
    private CommandGateway                        commandGateway;

    @Mock
    private ProductUnderwritingConfigPort         productUnderwritingConfigPort;

    @Mock
    private RuleEngineServicePort                 ruleEngineServicePort;

    @Mock
    private FeatureCenterPort                     featureCenterPort;

    @Mock
    private RuleConclusionMappingService          ruleConclusionMappingService;

    @Mock
    private UnderwritingParameterProperties       parameterProperties;

    @Mock
    private EventSourcingRepository<Underwriting> underwritingRepository;

    @InjectMocks
    private UnderwritingDecisionOrchestrator      orchestrator;

    @Captor
    private ArgumentCaptor<DecideUnderwritingCommand> commandCaptor;

    @Test
    void decideEnrichesTenantRiskParameterIntoDispatchedCommand() {
        givenLoadedAggregate();
        when(productUnderwritingConfigPort.fetchConfig(PRODUCT_CODE, TENANT_ID))
                .thenReturn(ProductUnderwritingConfig.defaultConfig(ProductConfigSource.NOT_CONFIGURED));
        UnderwritingParameter tenantParameter = new UnderwritingParameter(21, 11, 16,
                new BigDecimal("18.0"), new BigDecimal("27"), new BigDecimal("31"), 9, 110, 28, 58, 88);
        when(parameterProperties.resolve(TENANT_ID)).thenReturn(tenantParameter);
        when(commandGateway.sendAndWait(any(DecideUnderwritingCommand.class)))
                .thenReturn(mock(UnderwritingDecidedEvent.class));

        orchestrator.decide(decideCommand(), PRODUCT_CODE);

        verify(commandGateway).sendAndWait(commandCaptor.capture());
        assertSame(tenantParameter, commandCaptor.getValue().riskParameter(),
                "决策命令须携带经租户解析的核保参数（缺失即租户覆盖在内置评分路径静默失效）");
    }

    @SuppressWarnings("unchecked")
    private void givenLoadedAggregate() {
        LockAwareAggregate<Underwriting, EventSourcedAggregate<Underwriting>> lockAware = mock(LockAwareAggregate.class);
        EventSourcedAggregate<Underwriting> eventSourced = mock(EventSourcedAggregate.class);
        when(underwritingRepository.load(UNDERWRITING_ID.value())).thenReturn(lockAware);
        when(lockAware.getWrappedAggregate()).thenReturn(eventSourced);
        Underwriting aggregate = mock(Underwriting.class);
        when(eventSourced.getAggregateRoot()).thenReturn(aggregate);
        when(aggregate.getAmount()).thenReturn(UnderwritingAmount.of(BigDecimal.valueOf(100_000), CurrencyEnum.CNY));
    }

    private DecideUnderwritingCommand decideCommand() {
        // 输入形态无关紧要：decide() 只透传 underwritingId/auditType/decidedBy/tenantId 四项，
        // 其余四项（surchargeAcceptable/ruleDecision/configSource/riskParameter）一律由编排器
        // 用产品配置与租户参数重算覆盖——本测试的判别点是输出命令的 riskParameter 已充实。
        // 输入取有值形态（而非占位空值）反而更强：若实现退化为「原样透传输入」，断言同样判红。
        // 🔴 不得回退为裸空值占位——连续空实参触发 NullAlignmentUsageGuardTest（红线 17 联动门禁）。
        return new DecideUnderwritingCommand(UNDERWRITING_ID, UnderwritingEnum.AuditType.AUTOMATIC, "system",
                TENANT_ID, Boolean.TRUE, null, ProductConfigSource.CONFIGURED, UnderwritingParameter.DEFAULT);
    }
}
