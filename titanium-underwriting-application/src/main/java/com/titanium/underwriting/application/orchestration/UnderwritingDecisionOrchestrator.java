package com.titanium.underwriting.application.orchestration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import com.titanium.underwriting.command.DecideUnderwritingCommand;
import com.titanium.underwriting.common.constant.UnderwritingConstants;
import com.titanium.underwriting.event.UnderwritingDecidedEvent;
import com.titanium.underwriting.port.featurecenter.FeatureCenterPort;
import com.titanium.underwriting.port.product.ProductUnderwritingConfigPort;
import com.titanium.underwriting.port.product.ProductUnderwritingConfigPort.ProductUnderwritingConfig;
import com.titanium.underwriting.port.ruleengine.RuleEngineServicePort;
import com.titanium.underwriting.service.RuleConclusionMappingService;
import com.titanium.underwriting.valueobject.HealthDeclaration;
import com.titanium.underwriting.valueobject.OccupationInfo;
import com.titanium.underwriting.valueobject.RuleExecutionResult;
import com.titanium.underwriting.valueobject.RuleUnderwritingDecision;
import com.titanium.underwriting.valueobject.UnderwritingAmount;
import com.titanium.underwriting.valueobject.UnderwritingInput;
import com.titanium.underwriting.valueobject.VehicleRiskInfo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 核保决策编排器（dev-505：产品核保配置 → 规则引擎规则集 → 核保域执行的同步命令式编排）
 * <p>
 * 编排职责（application/orchestration，无业务规则）：
 * <ol>
 *   <li>加载核保聚合（取险种编码与已提交的核保输入）；</li>
 *   <li>经 {@link ProductUnderwritingConfigPort} 取产品核保配置快照；</li>
 *   <li>配置接入规则引擎（{@code ruleSetCode} 非空）时：装配规则上下文 → 经
 *       {@link FeatureCenterPort} 提取派生特征并入上下文 → 经 {@link RuleEngineServicePort}
 *       执行规则集 → 经 {@link RuleConclusionMappingService}（纯领域服务）映射结论 →
 *       充实决策命令的 {@code ruleDecision} 后派发聚合根；</li>
 *   <li>未接入规则引擎时向后兼容：仅充实 {@code surchargeAcceptable}，聚合根走内置评分路径。</li>
 * </ol>
 * 业务判断（结论映射/加费决策）全部下沉领域服务与聚合根，本编排器零业务规则。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnderwritingDecisionOrchestrator {

    private final CommandGateway                  commandGateway;
    private final ProductUnderwritingConfigPort   productUnderwritingConfigPort;
    private final RuleEngineServicePort           ruleEngineServicePort;
    private final FeatureCenterPort               featureCenterPort;
    private final RuleConclusionMappingService    ruleConclusionMappingService;
    private final EventSourcingRepository<Underwriting> underwritingRepository;

    /**
     * 核保决策编排入口。
     *
     * @param command     核保决策命令（web 层构造，surchargeAcceptable/ruleDecision 待充实）
     * @param productCode 险种编码（可为空，为空时回退聚合携带的险种编码）
     * @return 核保决策完成事件（聚合根产出）
     */
    public UnderwritingDecidedEvent decide(DecideUnderwritingCommand command, String productCode) {
        UnderwritingSnapshot snapshot = loadAggregate(command);
        String effectiveProductCode = productCode != null && !productCode.isBlank()
                ? productCode
                : snapshot.productCode();
        ProductUnderwritingConfig config = productUnderwritingConfigPort.fetchConfig(effectiveProductCode,
                command.tenantId());

        RuleUnderwritingDecision ruleDecision = null;
        if (config.ruleEngineEnabled()) {
            log.info("[核保决策] 产品接入规则引擎，走规则集链路: ruleSetCode={}, underwritingId={}",
                    config.ruleSetCode(), command.underwritingId());
            ruleDecision = evaluateRuleSet(command, snapshot, config);
        } else {
            log.info("[核保决策] 产品未接入规则引擎，走内置评分路径: productCode={}", effectiveProductCode);
        }

        DecideUnderwritingCommand enriched = new DecideUnderwritingCommand(command.underwritingId(),
                command.auditType(), command.decidedBy(), command.tenantId(), config.surchargeAcceptable(),
                ruleDecision);
        return commandGateway.sendAndWait(enriched);
    }

    /**
     * 规则集链路评估：装配上下文 → 提取特征 → 执行规则集 → 映射核保决策。
     *
     * @param command  核保决策命令
     * @param snapshot 核保聚合字段快照（提供已提交输入）
     * @param config   产品核保配置快照（含规则集编码与加费许可）
     * @return 核保决策；规则结论 PASS 时返回 null（回退内置评分路径）
     */
    private RuleUnderwritingDecision evaluateRuleSet(DecideUnderwritingCommand command, UnderwritingSnapshot snapshot,
                                                     ProductUnderwritingConfig config) {
        // 1. 装配规则上下文：核保输入直接承载的字段
        Map<String, Object> context = buildRuleContext(snapshot);
        // 2. 特征中心提取派生特征并入上下文（失败跳过，G13 变量预检兜底）
        context.putAll(featureCenterPort.extractFeatures(command.tenantId(),
                UnderwritingConstants.UNDERWRITING_FEATURE_CODES, context));
        // 3. 执行规则集（首个命中即生效）
        RuleExecutionResult result = ruleEngineServicePort.executeRuleSet(command.tenantId(), config.ruleSetCode(),
                context);
        // 4. 结论映射（纯领域服务）：PASS 返回 null 回退内置评分
        return ruleConclusionMappingService.map(result, config.surchargeAcceptable());
    }

    /**
     * 装配规则上下文：把核保输入容器承载的字段平铺为规则变量（键名见 {@link UnderwritingConstants#RULE_VAR_SUM_INSURED} 等）。
     * <p>
     * 仅装配非空输入块；规则条件引用的字段若未提供，由规则引擎 G13 变量预检抛出结构化缺失错误。
     * </p>
     *
     * @param snapshot 核保聚合字段快照
     * @return 规则变量上下文
     */
    private Map<String, Object> buildRuleContext(UnderwritingSnapshot snapshot) {
        Map<String, Object> context = new HashMap<>();
        context.put(UnderwritingConstants.RULE_VAR_SUM_INSURED, snapshot.amount().amount());
        UnderwritingInput input = snapshot.input();
        if (input == null) {
            return context;
        }
        HealthDeclaration health = input.healthDeclaration();
        if (health != null) {
            context.put(UnderwritingConstants.RULE_VAR_MEDICAL_HISTORY, List.copyOf(health.medicalHistory()));
            context.put(UnderwritingConstants.RULE_VAR_SMOKING, health.smoking());
            context.put(UnderwritingConstants.RULE_VAR_BMI, health.bmi());
        }
        OccupationInfo occupation = input.occupationInfo();
        if (occupation != null) {
            context.put(UnderwritingConstants.RULE_VAR_OCCUPATION_CATEGORY, occupation.occupationCategory());
            context.put(UnderwritingConstants.RULE_VAR_OCCUPATION_RISK_FACTOR, occupation.riskFactor());
        }
        VehicleRiskInfo vehicle = input.vehicleRiskInfo();
        if (vehicle != null) {
            context.put(UnderwritingConstants.RULE_VAR_VEHICLE_AGE_YEARS, vehicle.vehicleAgeYears());
            context.put(UnderwritingConstants.RULE_VAR_VEHICLE_USAGE_NATURE, vehicle.usageNature().getCode());
            context.put(UnderwritingConstants.RULE_VAR_HISTORICAL_CLAIM_COUNT, vehicle.historicalClaimCount());
            context.put(UnderwritingConstants.RULE_VAR_NCD_FACTOR, vehicle.ncdFactor());
        }
        if (input.financialAssessment() != null) {
            context.put(UnderwritingConstants.RULE_VAR_ANNUAL_INCOME, input.financialAssessment().annualIncome());
            context.put(UnderwritingConstants.RULE_VAR_NET_WORTH, input.financialAssessment().netWorth());
            context.put(UnderwritingConstants.RULE_VAR_REQUESTED_SUM_INSURED,
                    input.financialAssessment().requestedSumInsured());
        }
        return context;
    }

    /**
     * 加载核保聚合并在 {@code UnitOfWork} 内提取字段快照（事件溯源仓储的 load 必须在
     * UnitOfWork 上下文中执行；快照在回滚前取出，聚合对象不跨 UoW 生命周期使用）。
     * 聚合不存在时转为结构化业务错误。
     *
     * @param command 核保决策命令（携带聚合标识）
     * @return 核保聚合字段快照
     */
    private UnderwritingSnapshot loadAggregate(DecideUnderwritingCommand command) {
        DefaultUnitOfWork<?> unitOfWork = DefaultUnitOfWork.startAndGet(null);
        try {
            LockAwareAggregate<Underwriting, EventSourcedAggregate<Underwriting>> loaded =
                    underwritingRepository.load(command.underwritingId().value());
            Underwriting aggregate = loaded.getWrappedAggregate().getAggregateRoot();
            return new UnderwritingSnapshot(aggregate.getProductCode(), aggregate.getAmount(),
                    aggregate.getUnderwritingInput());
        } catch (AggregateNotFoundException ex) {
            log.warn("[核保决策] 核保案件聚合不存在: underwritingId={}", command.underwritingId());
            throw new BusinessException("核保案件不存在: " + command.underwritingId(),
                    UnderwritingErrorCode.UNDERWRITING_CASE_NOT_FOUND);
        } finally {
            unitOfWork.rollback();
        }
    }

    /**
     * 核保聚合字段快照（编排器只读需求的最小集，UnitOfWork 内提取，避免跨 UoW 使用聚合）。
     *
     * @param productCode 险种编码
     * @param amount      保额
     * @param input       已提交的险种专属核保输入（未提交为 null）
     */
    private record UnderwritingSnapshot(String productCode, UnderwritingAmount amount, UnderwritingInput input) {
    }
}
