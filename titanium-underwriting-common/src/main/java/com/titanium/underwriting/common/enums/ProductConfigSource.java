package com.titanium.underwriting.common.enums;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 产品核保配置来源枚举（m11-1404）
 * <p>
 * 核保取产品核保策略快照时有三种互不相同的处境，此前全部塌缩成同一个「默认配置」对象，
 * 导致「产品<b>没配</b>核保策略」与「产品域<b>取不到</b>配置」在系统内不可区分，更与「产品显式配置为
 * 允许加费」不可区分——核保可能在毫无产品策略依据的情况下产出加费承保结论，且事后无从审计。
 * </p>
 * <p>
 * 本枚举把该三态显式化，随配置快照一路带到决策命令与决策事件，使「本次核保是在产品未配置核保策略下
 * 做出的」成为可检索的事实。注意：<b>显式化语义不等于改变判定</b>——三态下 {@code surchargeAcceptable}
 * 仍沿用存量兼容取值（true），判定结果不变（见 {@code ProductUnderwritingConfig#defaultConfig}）。
 * </p>
 * <p>
 * 属核保域私有语义（描述的是本域取配置的处境，非产品域的业务属性），故按根规约 §3.4.2 定义在本模块
 * {@code common/enums}。字段遵循全项目枚举范式：{@code enumCode/code/name/desc} + {@code fromCode}。
 * </p>
 *
 * @author wei.sun
 * @since 2026/9/14
 */
@Getter
public enum ProductConfigSource implements BaseEnum {

    /** 已配置：产品域返回了该险种的核保策略配置，配置项取值真实可信 */
    CONFIGURED(1, "CONFIGURED", "已配置", "产品域返回了核保策略配置，各项取值可信"),
    /** 未配置：调用方未提供产品编码，连查询条件都不成立（「没配」） */
    NOT_CONFIGURED(2, "NOT_CONFIGURED", "未配置", "未提供产品编码，无从查询产品核保策略"),
    /** 不可用：产品域调用失败或返回不可用，配置取值未知（「取不到」，不等于产品没配） */
    UNAVAILABLE(3, "UNAVAILABLE", "不可用", "产品域返回不可用或调用异常，配置取值未知");

    private final Integer enumCode;
    private final String  code;
    private final String  name;
    private final String  desc;

    ProductConfigSource(Integer enumCode, String code, String name, String desc) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
        this.desc = desc;
    }

    /**
     * 该来源下配置取值是否可信（仅 {@link #CONFIGURED} 为是）。
     * <p>
     * 供消费方判断「本次决策是否有产品策略依据」，勿用 {@code != null} 之类的存在性判断替代——
     * 兜底配置对象永远非 null，正是本次要消除的语义盲区。
     * </p>
     *
     * @return true 表示产品域确实返回了配置
     */
    public boolean configured() {
        return this == CONFIGURED;
    }

    /**
     * 根据 code 反查枚举（统一范式入口，委托 {@link BaseEnum}）。
     *
     * @param code 来源代码
     * @return 匹配的枚举，未匹配返回 null
     */
    public static ProductConfigSource fromCode(String code) {
        return BaseEnum.fromCode(ProductConfigSource.class, code);
    }
}
