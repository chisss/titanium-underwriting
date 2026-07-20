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
}
