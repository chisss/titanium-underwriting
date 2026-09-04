package com.titanium.underwriting.port.featurecenter;

import java.util.List;
import java.util.Map;

/**
 * 特征中心提取出口端口（driven port，与聚合平级，按对端域 featurecenter 拆子包）
 * <p>
 * 规则条件引用的变量可能不是原始核保输入，而是特征中心定义的派生/外部特征（如职业风险等级、
 * 历史出险次数等）。本端口声明「按特征编码批量提取特征值」的能力，由 infrastructure 适配器
 * 经 Feign 调用特征中心实现；提取结果并入规则执行上下文（dev-505 / G21）。
 * </p>
 */
public interface FeatureCenterPort {

    /**
     * 按特征编码批量提取特征值。
     * <p>
     * 特征中心契约为单特征单次调用，适配器逐特征提取并聚合为映射返回；个别特征提取失败
     * 不阻断整体流程（特征字典属丰富化输入，规则引擎 G13 变量预检是最终防线）。
     * </p>
     *
     * @param tenantId     租户ID
     * @param featureCodes 特征编码清单（如产品配置/规则集变量中引用的特征）
     * @param rawInput     原始核保输入上下文（供特征直接取值或派生求值）
     * @return 特征编码 → 特征值 映射（提取失败的特征不包含在结果中）
     */
    Map<String, Object> extractFeatures(String tenantId, List<String> featureCodes, Map<String, Object> rawInput);
}
