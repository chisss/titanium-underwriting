package com.titanium.underwriting.infrastructure.adapter.featurecenter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.titanium.featurecenter.api.FeatureCenterApi;
import com.titanium.featurecenter.api.request.FeatureExtractRequest;
import com.titanium.featurecenter.api.response.FeatureValueResponse;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.underwriting.port.featurecenter.FeatureCenterPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 特征中心提取适配器（driven adapter，位于 infrastructure，按对端域 featurecenter 拆子包）
 * <p>
 * 实现 {@link FeatureCenterPort}，经 Feign {@link FeatureCenterApi} 调用特征中心
 * {@code POST /api/v1/features/extract}（单特征单次调用契约），逐特征提取并聚合为映射。
 * 单个特征提取失败仅告警跳过（特征字典属丰富化输入），不阻断整体——规则引擎 G13 变量预检
 * 是缺失变量的最终防线（dev-505 / G21）。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeatureCenterServiceAdapter implements FeatureCenterPort {

    private final FeatureCenterApi featureCenterApi;

    @Override
    public Map<String, Object> extractFeatures(String tenantId, List<String> featureCodes,
                                               Map<String, Object> rawInput) {
        Map<String, Object> features = new HashMap<>();
        if (featureCodes == null || featureCodes.isEmpty()) {
            return features;
        }
        for (String featureCode : featureCodes) {
            extractOne(tenantId, featureCode, rawInput, features);
        }
        return features;
    }

    /**
     * 提取单个特征值并放入结果映射（失败告警跳过，不抛出）。
     *
     * @param tenantId    租户ID
     * @param featureCode 特征编码
     * @param rawInput    原始核保输入上下文
     * @param features    结果映射（featureCode → value）
     */
    private void extractOne(String tenantId, String featureCode, Map<String, Object> rawInput,
                            Map<String, Object> features) {
        try {
            FeatureExtractRequest request = new FeatureExtractRequest();
            request.setFeatureCode(featureCode);
            request.setContext(rawInput);
            ApiResponse<FeatureValueResponse> response = featureCenterApi.extract(request, tenantId);
            if (response != null && response.isSuccess() && response.getData() != null) {
                features.put(featureCode, response.getData().getValue());
            } else {
                String message = response != null ? response.getMessage() : "无响应";
                log.warn("[特征中心] 特征提取不可用，跳过该特征: featureCode={}, error={}", featureCode, message);
            }
        } catch (Exception ex) {
            log.warn("[特征中心] 特征提取失败，跳过该特征（规则引擎变量预检兜底）: featureCode={}, error={}",
                    featureCode, ex.getMessage());
        }
    }
}
