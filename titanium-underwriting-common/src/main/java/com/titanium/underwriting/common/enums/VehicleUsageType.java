package com.titanium.underwriting.common.enums;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 车辆使用性质枚举
 * <p>
 * 车险核保专属维度，区分车辆运营用途以驱动风险评分（营运车风险更高）。
 * metadata 无对应通用枚举，属核保域车险私有语义，故定义在本模块 common 层。
 * 字段遵循全项目枚举范式：{@code enumCode/code/name/desc} + {@code fromCode}（未匹配返回 null）。
 * </p>
 *
 * @author wei.sun
 * @since 2026/6/25
 */
@Getter
public enum VehicleUsageType implements BaseEnum {

    /** 家庭自用车 */
    FAMILY(1, "FAMILY", "家庭自用", "家庭自用车，非经营性使用"),
    /** 非营运车（单位/公务自用等非盈利用途） */
    NON_OPERATING(2, "NON_OPERATING", "非营运", "单位或个人非盈利性使用"),
    /** 营运车（出租、客运、货运等盈利性使用，风险最高） */
    OPERATING(3, "OPERATING", "营运", "出租、客运、货运等盈利性使用");

    private final Integer enumCode;
    private final String  code;
    private final String  name;
    private final String  desc;

    VehicleUsageType(Integer enumCode, String code, String name, String desc) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
        this.desc = desc;
    }

    /**
     * 根据 code 反查枚举（统一范式入口，委托 {@link BaseEnum}）。
     *
     * @param code 使用性质代码
     * @return 匹配的枚举，未匹配返回 null
     */
    public static VehicleUsageType fromCode(String code) {
        return BaseEnum.fromCode(VehicleUsageType.class, code);
    }
}
