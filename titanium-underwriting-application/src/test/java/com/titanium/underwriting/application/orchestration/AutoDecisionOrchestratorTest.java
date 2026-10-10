package com.titanium.underwriting.application.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.metadata.enums.customer.CustomerEnum.CustomerGender;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.UnderwritingStatus;
import com.titanium.underwriting.application.orchestration.assembler.AutoDecisionAssembler;
import com.titanium.underwriting.application.query.UnderwritingQueryAppService;
import com.titanium.underwriting.command.CreateUnderwritingCommand;
import com.titanium.underwriting.command.DecideUnderwritingCommand;
import com.titanium.underwriting.command.SubmitUnderwritingInputCommand;
import com.titanium.underwriting.common.enums.ProductConfigSource;
import com.titanium.underwriting.event.UnderwritingDecidedEvent;
import com.titanium.underwriting.generator.UnderwritingNoGenerator;
import com.titanium.underwriting.query.result.UnderwritingQueryResult;
import com.titanium.underwriting.repository.UnderwritingEventStreamRepository;
import com.titanium.underwriting.valueobject.AutoDecideRequest;
import com.titanium.underwriting.valueobject.AutoDecideResult;
import com.titanium.underwriting.valueobject.CustomerId;
import com.titanium.underwriting.valueobject.ExtraPremium;
import com.titanium.underwriting.valueobject.HealthDeclaration;
import com.titanium.underwriting.valueobject.InsuranceId;
import com.titanium.underwriting.valueobject.InsuredRiskFactors;
import com.titanium.underwriting.valueobject.PolicyId;
import com.titanium.underwriting.valueobject.UnderwritingAmount;
import com.titanium.underwriting.valueobject.UnderwritingId;

/**
 * 自动决策编排器测试（g02-04 / AC-05）
 * <p>
 * 锁死三分支与其边界：① <b>已出结论</b>（终态）→ 只读复用，<b>一个命令都不发</b>；② <b>在办</b>（非终态）→
 * 续跑既有单，<b>不新建</b>；③ <b>读模型查无此投保单</b> → 以确定性案件号在事件流上终判。
 * 且投保单号<b>同值双填</b>（{@code policyId} 保存量分区键语义 + {@code insuranceId} 正名）。
 * </p>
 * <p>
 * 🔴 <b>分支三的两个用例直接对应真机实测到的投影滞后窗口</b>——AC-05 的正向判据「同键重放不产生第二张
 * 核保单」正是被它们守住：读模型<b>有行但状态陈旧</b>（66ms 重放）与读模型<b>连行都没有</b>（210ms 重放）
 * 都会导致「误续跑 409」或「误新建双单」，两者都必须由事件流强一致判据兜住。
 * </p>
 * <p>
 * 装配器用 {@link Spy} 而非 mock：它是纯函数组件，「事件通道 / 读模型通道同形」是被测语义的一部分，
 * 用 mock 会把该语义回显掉。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class AutoDecisionOrchestratorTest {

    private static final String INSURANCE_ID = "INS-001";
    private static final String TENANT_ID    = "TENANT-001";
    private static final String OPERATOR_ID  = "SYSTEM_AUTO_UNDERWRITING";
    private static final String PRODUCT_CODE = "PRD-001";
    private static final String CASE_NO      = "UW20260927001";

    /** 分支三的强一致判据所围绕的确定性案件号，由 (租户, 投保单号) 派生 */
    private static final UnderwritingId DERIVED_ID = UnderwritingId.forAutoDecide(TENANT_ID, INSURANCE_ID);

    @Mock
    private CommandGateway                    commandGateway;

    @Mock
    private UnderwritingNoGenerator           underwritingNoGenerator;

    @Mock
    private UnderwritingQueryAppService       underwritingQueryAppService;

    @Mock
    private UnderwritingDecisionOrchestrator  underwritingDecisionOrchestrator;

    @Spy
    private AutoDecisionAssembler             autoDecisionAssembler = new AutoDecisionAssembler();

    @Mock
    private UnderwritingEventStreamRepository underwritingEventStreamRepository;

    @InjectMocks
    private AutoDecisionOrchestrator          orchestrator;

    @Captor
    private ArgumentCaptor<CreateUnderwritingCommand>        createCaptor;

    @Captor
    private ArgumentCaptor<SubmitUnderwritingInputCommand>   inputCaptor;

    @Captor
    private ArgumentCaptor<InsuranceId>                      insuranceIdCaptor;

    // ------------------------------------------------------------------ 分支一：终态复用

    @Test
    void reusesConcludedUnderwritingAndEmitsNoCommandAtAll() {
        givenExisting(readModel("UW-EXIST-1", UnderwritingStatus.DECLINED));

        AutoDecideResult result = orchestrator.autoDecide(request(null));

        // 幂等命中的主路径：结论直接来自读模型，且**不触发任何写操作**
        assertEquals("UW-EXIST-1", result.underwritingId());
        assertEquals(ConclusionType.REJECT, result.conclusionType());
        assertEquals(UnderwritingStatus.DECLINED, result.status());
        verifyNoInteractions(commandGateway, underwritingDecisionOrchestrator, underwritingNoGenerator);
    }

    @Test
    void prefersConcludedCaseOverOpenCaseWhenBothExistSoRetryStaysStable() {
        // 同一投保单在途多张（历史遗留 / 投影延迟补建）：终态优先，保证重复调用收敛到同一张
        givenExisting(List.of(readModel("UW-OPEN-1", UnderwritingStatus.MANUAL_REVIEW),
                readModel("UW-EXIST-1", UnderwritingStatus.APPROVED)));

        AutoDecideResult result = orchestrator.autoDecide(request(null));

        assertEquals("UW-EXIST-1", result.underwritingId());
        verifyNoInteractions(commandGateway, underwritingDecisionOrchestrator, underwritingNoGenerator);
    }

    // ------------------------------------------------------------------ 分支二：在办续跑 / 状态陈旧复用

    @Test
    void reusesEventStreamDecisionWhenReadModelStillShowsOpenCaseSoRetryNeverHitsTerminalGuard() {
        // 🔴 真机窗口一（AC-05）：首次调用返回后 66ms 重放，决策事件已落事件流但投影尚未追平，
        // 读模型仍显 PENDING。若据此续跑，聚合的终态守卫会拒绝并抛非法状态流转——上游 Saga 重试
        // 会拿到 409 而非同一结论，违反契约「重入安全」。此用例锁死「以事件流强一致复核」这条分支。
        givenExisting(readModel("UW-OPEN-1", UnderwritingStatus.PENDING));
        when(underwritingEventStreamRepository.findLatestDecision(new UnderwritingId("UW-OPEN-1")))
                .thenReturn(Optional.of(decidedEvent("UW-OPEN-1")));
        // lenient：本分支正确时决策编排器**不应被调用**，故此桩默认闲置。留着是为了让「误走续跑」
        // 表现为断言失败（verifyNoInteractions）而不是空返回值引发的 NPE——判据要可归因。
        lenient().when(underwritingDecisionOrchestrator.decide(any(), any())).thenReturn(decidedEvent("UW-OPEN-1"));

        AutoDecideResult result = orchestrator.autoDecide(request(null));

        assertEquals("UW-OPEN-1", result.underwritingId());
        assertEquals(ConclusionType.MODIFY, result.conclusionType());
        assertEquals(UnderwritingStatus.RATED, result.status());
        assertEquals(TENANT_ID, result.tenantId());
        // 幂等命中的本质是「零写操作」：一个命令都不发，否则终态守卫必然拒绝
        verifyNoInteractions(commandGateway, underwritingDecisionOrchestrator, underwritingNoGenerator);
    }

    @Test
    void resumesOpenUnderwritingInsteadOfCreatingSecondOne() {
        givenExisting(readModel("UW-OPEN-1", UnderwritingStatus.MANUAL_REVIEW));
        when(underwritingDecisionOrchestrator.decide(any(), any())).thenReturn(decidedEvent());

        orchestrator.autoDecide(request(null));

        verify(commandGateway).sendAndWait(inputCaptor.capture());
        assertEquals("UW-OPEN-1", inputCaptor.getValue().underwritingId().value());
        // 续跑前必须先经事件流强一致复核（否则读模型滞后时会误续跑）：本断言保证该分支确实在路径上
        verify(underwritingEventStreamRepository).findLatestDecision(new UnderwritingId("UW-OPEN-1"));
        // 续跑不新建：既不取号也不发创建命令，否则同一投保单会积累多张核保单
        verify(commandGateway, never()).sendAndWait(any(CreateUnderwritingCommand.class));
        verifyNoInteractions(underwritingNoGenerator);
    }

    // ------------------------------------------------------------------ 分支三：读模型无行时的强一致终判

    @Test
    void reusesEventStreamDecisionWhenProjectionHasNoRowAtAllSoFastRetryNeverCreatesSecondCase() {
        // 🔴 真机窗口二（AC-05 正向判据的直接守护）：210ms 间隔重放时投影**连行都还没建**，
        // 读模型返回空列表。若据此判「无既有核保单」，会**再建一张核保单**——真机实测产生两张
        // （229681988590632960 / 229681989689540608），违反「同键重放不产生第二张核保单」。
        // 本用例锁死「确定性案件号 + 事件流存在性判定」这条不依赖任何索引与投影的强一致分支。
        givenNoExisting();
        when(underwritingEventStreamRepository.exists(DERIVED_ID)).thenReturn(true);
        when(underwritingEventStreamRepository.findLatestDecision(DERIVED_ID))
                .thenReturn(Optional.of(decidedEvent(DERIVED_ID.value())));
        lenient().when(underwritingDecisionOrchestrator.decide(any(), any()))
                .thenReturn(decidedEvent(DERIVED_ID.value()));

        AutoDecideResult result = orchestrator.autoDecide(request(null));

        assertEquals(DERIVED_ID.value(), result.underwritingId());
        assertEquals(ConclusionType.MODIFY, result.conclusionType());
        assertEquals(UnderwritingStatus.RATED, result.status());
        // 同键重放必须是**零写操作**：新建即第二张核保单
        verifyNoInteractions(commandGateway, underwritingDecisionOrchestrator, underwritingNoGenerator);
    }

    @Test
    void resumesDerivedCaseThatWasCreatedButNotYetDecidedInsteadOfCreatingSecondOne() {
        // 首次调用在建单/提交输入后失败（如规则引擎不可用）⇒ 事件流已有该聚合但无结论。
        // 重放必须续跑同一张，而不是因为读模型还没投影出来就再建一张。
        givenNoExisting();
        when(underwritingEventStreamRepository.exists(DERIVED_ID)).thenReturn(true);
        when(underwritingEventStreamRepository.findLatestDecision(DERIVED_ID)).thenReturn(Optional.empty());
        when(underwritingDecisionOrchestrator.decide(any(), any())).thenReturn(decidedEvent(DERIVED_ID.value()));

        orchestrator.autoDecide(request(null));

        verify(commandGateway).sendAndWait(inputCaptor.capture());
        assertEquals(DERIVED_ID.value(), inputCaptor.getValue().underwritingId().value());
        verify(commandGateway, never()).sendAndWait(any(CreateUnderwritingCommand.class));
        verifyNoInteractions(underwritingNoGenerator);
    }

    @Test
    void createsUnderwritingWithInsuranceIdWrittenIntoBothFieldsWhenNoneExists() {
        givenNoExisting();
        when(underwritingNoGenerator.generateUnderwritingNo(TENANT_ID)).thenReturn(CASE_NO);
        when(underwritingDecisionOrchestrator.decide(any(), any())).thenReturn(decidedEvent());

        orchestrator.autoDecide(request(null));

        verify(commandGateway).sendAndWait(createCaptor.capture());
        CreateUnderwritingCommand command = createCaptor.getValue();
        // 🔴 同值双填：policyId 保存量语义（Kafka 分区键 underwriting-decided 固定取它，取值不得改变），
        // insuranceId 是 g02-04 的投保单号正名，二者并存
        assertEquals(INSURANCE_ID, command.policyId().value());
        assertEquals(INSURANCE_ID, command.insuranceId().value());
        assertEquals(CASE_NO, command.caseNo());
        assertEquals(TENANT_ID, command.tenantId());
        assertEquals(OPERATOR_ID, command.createdBy());
        assertEquals(PRODUCT_CODE, command.productCode());
        // 🔴 取号必须与幂等键绑定：案件号取雪花号会让同键两次调用得到两个号码，幂等随即退化为
        // 「靠读模型反查」，而反查在投影滞后时必然落空（真机 210ms 重放即建双单）
        assertEquals(DERIVED_ID, command.underwritingId());
    }

    @Test
    void queriesExistingCasesByInsuranceIdAndTenantBeforeAnyDecision() {
        givenNoExisting();
        when(underwritingNoGenerator.generateUnderwritingNo(TENANT_ID)).thenReturn(CASE_NO);
        when(underwritingDecisionOrchestrator.decide(any(), any())).thenReturn(decidedEvent());

        orchestrator.autoDecide(request(null));

        // 幂等键 = (投保单号, 租户)：查询维度错则重试必然建新单，是幂等失效最隐蔽的成因
        verify(underwritingQueryAppService).findUnderwritingsByInsuranceId(insuranceIdCaptor.capture(), any());
        assertEquals(INSURANCE_ID, insuranceIdCaptor.getValue().value());
    }

    // ------------------------------------------------------------------ 输入提交与要素透传

    @Test
    void alwaysSubmitsInputContainerEvenWhenNoRiskFactorProvided() {
        givenNoExisting();
        when(underwritingNoGenerator.generateUnderwritingNo(TENANT_ID)).thenReturn(CASE_NO);
        when(underwritingDecisionOrchestrator.decide(any(), any())).thenReturn(decidedEvent());

        orchestrator.autoDecide(request(null));

        // 🔴 要素全空也必须提交：聚合的决策处理器直接取 underwritingInput.aggregateRiskScore()，
        // 少这一步就是决策期 NPE（四步路径正是靠「总是先提交输入」规避）
        verify(commandGateway).sendAndWait(inputCaptor.capture());
        assertNull(inputCaptor.getValue().underwritingInput().insuredRiskFactors());
    }

    @Test
    void passesProvidedRiskFactorsIntoSubmittedInput() {
        givenNoExisting();
        when(underwritingNoGenerator.generateUnderwritingNo(TENANT_ID)).thenReturn(CASE_NO);
        when(underwritingDecisionOrchestrator.decide(any(), any())).thenReturn(decidedEvent());
        InsuredRiskFactors factors = new InsuredRiskFactors(45, CustomerGender.MALE, 4, new BigDecimal("29"));

        orchestrator.autoDecide(request(factors));

        verify(commandGateway).sendAndWait(inputCaptor.capture());
        assertEquals(factors, inputCaptor.getValue().underwritingInput().insuredRiskFactors());
    }

    @Test
    void passesProvidedHealthDeclarationIntoSubmittedInput() {
        givenNoExisting();
        when(underwritingNoGenerator.generateUnderwritingNo(TENANT_ID)).thenReturn(CASE_NO);
        when(underwritingDecisionOrchestrator.decide(any(), any())).thenReturn(decidedEvent());
        HealthDeclaration declaration = new HealthDeclaration(List.of("高血压"), List.of("糖尿病家族史"), true,
                new BigDecimal("175"), new BigDecimal("80"));

        orchestrator.autoDecide(request(null, declaration));

        // G12/g12-01 AC-01：告知要素必须随输入提交抵达聚合（此前该块在链路中途丢失，
        // 核保域只能按「无告知」评分——本次改造禁止的正是这种静默降级）
        verify(commandGateway).sendAndWait(inputCaptor.capture());
        assertEquals(declaration, inputCaptor.getValue().underwritingInput().healthDeclaration());
    }

    @Test
    void leavesHealthDeclarationNullWhenUpstreamDidNotProvideIt() {
        givenNoExisting();
        when(underwritingNoGenerator.generateUnderwritingNo(TENANT_ID)).thenReturn(CASE_NO);
        when(underwritingDecisionOrchestrator.decide(any(), any())).thenReturn(decidedEvent());

        orchestrator.autoDecide(request(null));

        // 「未提供」与「提供了低风险告知」必须在核保入参层可区分：未提供时保持 null，
        // 编排器不得代填默认告知块（代填会把「没告知」变成「低风险告知」）
        verify(commandGateway).sendAndWait(inputCaptor.capture());
        assertNull(inputCaptor.getValue().underwritingInput().healthDeclaration());
    }

    // ------------------------------------------------------------------ 出参装配（事件通道）

    @Test
    void returnsConclusionAssembledFromDecidedEventIncludingAuditTypeAndTenant() {
        givenNoExisting();
        when(underwritingNoGenerator.generateUnderwritingNo(TENANT_ID)).thenReturn(CASE_NO);
        when(underwritingDecisionOrchestrator.decide(any(), any())).thenReturn(decidedEvent());

        AutoDecideResult result = orchestrator.autoDecide(request(null));

        assertEquals("UW-NEW-1", result.underwritingId());
        assertEquals(INSURANCE_ID, result.insuranceId());
        assertEquals(ConclusionType.MODIFY, result.conclusionType());
        assertEquals(UnderwritingStatus.RATED, result.status());
        assertEquals(UnderwritingEnum.AuditType.AUTOMATIC, result.auditType());
        assertEquals(TENANT_ID, result.tenantId());
        assertEquals(OPERATOR_ID, result.decidedBy());
        // 加费明细原样回流（出单 Saga 据此并入保费）
        assertEquals(new BigDecimal("0.30"), result.extraPremiumRatio());
        assertEquals("高血压加费", result.extraPremiumReason());
        // 🔴 reason 按状态分派：RATED 既非拒保也非转人工/除外，三列必须全空——
        // 单一 reason 无差别灌进三列会让核保结论在展示侧自相矛盾
        assertNull(result.rejectReason());
        assertNull(result.reviewComments());
        assertNull(result.exclusionReason());
    }

    @Test
    void dispatchesDecisionAgainstTheSameCaseItJustCreatedOrResumed() {
        givenExisting(readModel("UW-OPEN-1", UnderwritingStatus.PENDING));
        when(underwritingDecisionOrchestrator.decide(any(), any())).thenReturn(decidedEvent());

        orchestrator.autoDecide(request(null));

        ArgumentCaptor<DecideUnderwritingCommand> decideCaptor = ArgumentCaptor.forClass(DecideUnderwritingCommand.class);
        verify(underwritingDecisionOrchestrator).decide(decideCaptor.capture(), any());
        // 决策必须落在本次解析出的那一张上（否则会给出另一张核保单的结论）
        assertEquals("UW-OPEN-1", decideCaptor.getValue().underwritingId().value());
        assertEquals(UnderwritingEnum.AuditType.AUTOMATIC, decideCaptor.getValue().auditType());
    }

    // ------------------------------------------------------------------ 夹具

    private void givenNoExisting() {
        givenExisting(List.of());
    }

    private void givenExisting(UnderwritingQueryResult result) {
        givenExisting(List.of(result));
    }

    private void givenExisting(List<UnderwritingQueryResult> results) {
        when(underwritingQueryAppService.findUnderwritingsByInsuranceId(any(), any())).thenReturn(results);
    }

    private UnderwritingQueryResult readModel(String underwritingId, UnderwritingStatus status) {
        UnderwritingQueryResult result = new UnderwritingQueryResult();
        result.setUnderwritingId(underwritingId);
        result.setInsuranceId(INSURANCE_ID);
        result.setStatus(status);
        result.setConclusionType(status.isRejected() ? ConclusionType.REJECT : ConclusionType.ACCEPT);
        result.setTenantId(TENANT_ID);
        return result;
    }

    /** 事件通道的出参：加费承保，reason 非空以验证三列分派 */
    private UnderwritingDecidedEvent decidedEvent() {
        return decidedEvent("UW-NEW-1");
    }

    /** 事件通道的出参（指定核保单ID，供「事件流复用」分支对齐既有单） */
    private UnderwritingDecidedEvent decidedEvent(String underwritingId) {
        return new UnderwritingDecidedEvent(new UnderwritingId(underwritingId), PolicyId.of(INSURANCE_ID),
                UnderwritingEnum.RiskLevel.SUB_STANDARD, ConclusionType.MODIFY, UnderwritingEnum.AuditType.AUTOMATIC,
                UnderwritingStatus.PENDING, UnderwritingStatus.RATED, 55,
                ExtraPremium.ofRatio(new BigDecimal("0.30"), null, "高血压加费"), LocalDateTime.now(), OPERATOR_ID,
                TENANT_ID, "加费原因说明", ProductConfigSource.CONFIGURED, InsuranceId.of(INSURANCE_ID));
    }

    private AutoDecideRequest request(InsuredRiskFactors riskFactors) {
        return request(riskFactors, null);
    }

    private AutoDecideRequest request(InsuredRiskFactors riskFactors, HealthDeclaration healthDeclaration) {
        return new AutoDecideRequest(InsuranceId.of(INSURANCE_ID), CustomerId.of("CUST-001"),
                UnderwritingAmount.of(BigDecimal.valueOf(500_000), CurrencyEnum.CNY),
                UnderwritingEnum.UnderwritingType.NEW_BUSINESS, PRODUCT_CODE, riskFactors, OPERATOR_ID, TENANT_ID,
                healthDeclaration);
    }
}
