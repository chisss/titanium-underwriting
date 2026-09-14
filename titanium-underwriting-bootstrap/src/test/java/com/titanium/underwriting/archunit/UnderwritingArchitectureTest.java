package com.titanium.underwriting.archunit;

import org.junit.jupiter.api.Test;

import com.titanium.buildtools.archunit.AbstractArchitectureGuardTest;

/**
 * 核保域架构守护测试：继承共享基类，仅提供本域根包。
 * DDD 分层/命名/依赖注入规则由 {@link AbstractArchitectureGuardTest} 提供，
 * 规则一处维护、各域复用，杜绝测试代码复制粘贴漂移。
 * <p>
 * 本域已完成 api/web 两层整改（api 契约实现落 web/provider、Controller 不 implements Api、
 * application 门面入参即领域命令、DTO→Command 翻译下沉 web），故在此 {@code @Override} 启用基类
 * 默认 {@code @Disabled} 的 4 条 api/web 边界断言。
 * </p>
 * <p>
 * 注意：本方案 web 层需依赖 domain 的 command（Controller 经 WebMapper 直转命令），故
 * <b>不启用</b> {@code webShouldNotDependOnDomainCommandsOrAggregates}（该规则要求 web 经 Request/VO
 * 完全隔离 command，与本方案「web 直转 Command」冲突，保持基类 {@code @Disabled}）。
 * </p>
 */
class UnderwritingArchitectureTest extends AbstractArchitectureGuardTest {

    @Override
    protected String basePackage() {
        return "com.titanium.underwriting";
    }

    @Test
    @Override
    protected void applicationMustNotDependOnApiDto() {
        super.applicationMustNotDependOnApiDto();
    }

    @Test
    @Override
    protected void apiContractImplMustResideInProviderPackage() {
        super.apiContractImplMustResideInProviderPackage();
    }

    @Test
    @Override
    protected void controllerMustNotImplementApi() {
        super.controllerMustNotImplementApi();
    }

    @Test
    @Override
    protected void apiInterfacesMustBeNamedByAggregate() {
        super.apiInterfacesMustBeNamedByAggregate();
    }

    @Test
    @Override
    protected void apiLayerUsesRequestResponseNotDto() {
        super.apiLayerUsesRequestResponseNotDto();
    }

    @Test
    @Override
    protected void webLayerUsesDtoVoNotRequest() {
        super.webLayerUsesDtoVoNotRequest();
    }

    /**
     * 启用「api.request 按业务主题拆子包、顶层清零」（分包规则·批次 2）。
     * <p>
     * 核保域 api.request 已按业务主题二分：{@code request.underwriting}（创建 / 提交输入 / 决策 /
     * 执行四步主流程）与 {@code request.maintenance}（保全核保评估），顶层零类。
     * </p>
     * <p>
     * 🔴 <b>跨域引用面</b>：本包是 Feign 契约，被 policy（{@code UnderwritingServiceAdapter} /
     * {@code SyncUnderwritingDecisionAdapter} 及其测试）与 maintenance（{@code MaintenanceUnderwritingAdapter}
     * 及其测试）import，须先 install 本域 api 再编译下游。
     * </p>
     */
    @Test
    @Override
    protected void apiRequestShouldNotContainFlatClasses() {
        super.apiRequestShouldNotContainFlatClasses();
    }

    /**
     * 启用「api.response 按业务主题拆子包、顶层清零」（分包规则·批次 2）。
     * <p>
     * 与 request 同构二分：{@code response.underwriting}（核保结论 / 统计）与
     * {@code response.maintenance}（保全核保结果），顶层零类。
     * </p>
     * <p>
     * 🔴 <b>跨域引用面</b>：被 policy（两个 Adapter 及测试）、maintenance（{@code MaintenanceUnderwritingAdapter}）、
     * admin（{@code UnderwritingServiceClient} / {@code BusinessProxyService} / {@code DashboardController}）
     * 三域 import。
     * </p>
     */
    @Test
    @Override
    protected void apiResponseShouldNotContainFlatClasses() {
        super.apiResponseShouldNotContainFlatClasses();
    }

    // 注：web.dto（4 类：创建 / 提交输入 / 决策 / 执行）为「核保案件全流程」单一业务主题集中包，
    // 按《包结构分包规范与执行方案-2026-09》§一判据表豁免，不启用 webDtoShouldNotContainFlatClasses。
}
