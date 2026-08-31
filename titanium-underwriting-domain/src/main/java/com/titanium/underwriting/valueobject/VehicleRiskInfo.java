package com.titanium.underwriting.valueobject;

import java.io.Serializable;
import java.math.BigDecimal;

import com.titanium.metadata.errorcode.UnderwritingErrorCode;
import com.titanium.underwriting.common.enums.VehicleUsageType;
import com.titanium.underwriting.exception.UnderwritingValidationException;

/**
 * 车辆风险信息值对象
 * <p>
 * 车险专属核保输入，对应 {@code UnderwritingInputType.VEHICLE_RISK}。
 * 采集车龄、使用性质、历史出险次数及无赔款优待（NCD）系数， 值对象内聚车辆风险评分逻辑（充血模型）。
 * </p>
 *
 * @param vehicleAgeYears 车龄（年）
 * @param usageNature 使用性质（家用、非营运、营运），驱动风险评分
 * @param historicalClaimCount 历史出险次数（近年累计）
 * @param ncdFactor NCD 无赔款优待系数（通常 0.6-2.0，越低代表历史越好）
 * @author wei.sun
 * @since 2026/6/23
 */
public record VehicleRiskInfo(int vehicleAgeYears, VehicleUsageType usageNature, int historicalClaimCount,
                              BigDecimal ncdFactor)
        implements
            Serializable {

    /**
     * 值对象名（用于校验异常上下文）
     */
    private static final String VO_NAME = "VehicleRiskInfo";

    public VehicleRiskInfo {
        if (vehicleAgeYears < 0) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.VEHICLE_AGE_NEGATIVE, VO_NAME, "vehicleAgeYears");
        }
        if (usageNature == null) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.USAGE_NATURE_REQUIRED, VO_NAME, "usageNature");
        }
        if (historicalClaimCount < 0) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.HISTORICAL_CLAIM_NEGATIVE, VO_NAME, "historicalClaimCount");
        }
        if (ncdFactor == null || ncdFactor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.NCD_FACTOR_POSITIVE, VO_NAME, "ncdFactor");
        }
    }

    /**
     * 车辆风险评分（0-100，越高风险越大）
     * <p>
     * 综合历史出险次数（主因子）、车龄、NCD 系数及营运性质加权评分（充血模型）。
     * </p>
     *
     * @return 车辆风险评分
     */
    public int riskScore() {
        int score = 0;
        // 历史出险：每次 +20
        score += historicalClaimCount * 20;
        // 车龄超过 8 年 +15
        if (vehicleAgeYears > 8) {
            score += 15;
        }
        // NCD 系数 >=1.5（历史赔付差）+15
        if (ncdFactor.compareTo(BigDecimal.valueOf(1.5)) >= 0) {
            score += 15;
        }
        // 营运车辆风险更高 +15（枚举用 == 比较）
        if (VehicleUsageType.OPERATING == usageNature) {
            score += 15;
        }
        return Math.min(score, 100);
    }
}
