package com.titanium.underwriting.valueobject;

import java.io.Serializable;
import java.math.BigDecimal;

import com.titanium.metadata.enums.underwriting.ExtraPremiumType;
import com.titanium.metadata.errorcode.UnderwritingErrorCode;
import com.titanium.underwriting.exception.UnderwritingValidationException;

/**
 * 加费值对象（核保次标准体承保的结构化加费明细）
 * <p>
 * 次标准体（{@code RiskLevel.SUB_STANDARD}）经核保「修改条件承保」（{@code ConclusionType.MODIFY}）时，
 * 以加费方式承保风险。替代原先仅 {@code UnderwritingStatus.RATED} 状态码——加费需结构化承载类型、
 * 幅度与期限，供 billing 域计算实收保费（标准保费 × (1 + 加费率) 或 标准保费 + 固定加费额）。
 * </p>
 * <p>
 * 充血不可变值对象：{@link #applyTo(BigDecimal)} 内聚「标准保费 → 加费后保费」的计算规则，
 * 按加费类型（比例/固定额）差异化计算，供定价编排调用。
 * </p>
 *
 * @param type       加费类型（比例加费/固定额加费/永久/临时）
 * @param ratio      加费率（比例加费时用，如 0.5 表示加费 50%；固定额加费时为 null）
 * @param fixedAmount 固定加费额（固定额加费时用；比例加费时为 null）
 * @param durationYears 加费期限（年，临时加费时用；永久加费为 null 表示全期加费）
 * @param reason     加费原因（如既往病史、BMI 异常、职业风险）
 */
public record ExtraPremium(ExtraPremiumType type, BigDecimal ratio, BigDecimal fixedAmount, Integer durationYears,
                           String reason)
        implements
            Serializable {

    public ExtraPremium {
        if (type == null) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.EXTRA_PREMIUM_TYPE_REQUIRED, "ExtraPremium");
        }
        if (type.isRatioBased()) {
            if (ratio == null || ratio.compareTo(BigDecimal.ZERO) <= 0) {
                throw new UnderwritingValidationException(UnderwritingErrorCode.EXTRA_PREMIUM_RATIO_POSITIVE,
                        "ExtraPremium");
            }
        } else if (fixedAmount == null || fixedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.EXTRA_PREMIUM_AMOUNT_POSITIVE,
                    "ExtraPremium");
        }
        if (durationYears != null && durationYears <= 0) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.EXTRA_PREMIUM_DURATION_POSITIVE,
                    "ExtraPremium");
        }
    }

    /**
     * 按比例加费构造（如加费 30%）。
     *
     * @param ratio 加费率
     * @param durationYears 加费期限（年，null 表示全期永久加费）
     * @param reason 加费原因
     * @return 比例加费
     */
    public static ExtraPremium ofRatio(BigDecimal ratio, Integer durationYears, String reason) {
        return new ExtraPremium(durationYears == null ? ExtraPremiumType.PERMANENT_RATIO
                : ExtraPremiumType.TEMPORARY_RATIO, ratio, null, durationYears, reason);
    }

    /**
     * 按固定额加费构造（如每年加费 500 元）。
     *
     * @param fixedAmount 固定加费额
     * @param durationYears 加费期限（年，null 表示全期永久加费）
     * @param reason 加费原因
     * @return 固定额加费
     */
    public static ExtraPremium ofFixed(BigDecimal fixedAmount, Integer durationYears, String reason) {
        return new ExtraPremium(ExtraPremiumType.FIXED_AMOUNT, null, fixedAmount, durationYears, reason);
    }

    /**
     * 计算加费后保费：比例加费 = 标准保费 ×(1+加费率)；固定额加费 = 标准保费 + 固定加费额。
     *
     * @param standardPremium 标准保费
     * @return 加费后应收保费
     */
    public BigDecimal applyTo(BigDecimal standardPremium) {
        if (standardPremium == null) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.STANDARD_PREMIUM_REQUIRED, "ExtraPremium");
        }
        if (type.isRatioBased()) {
            return standardPremium.multiply(BigDecimal.ONE.add(ratio));
        }
        return standardPremium.add(fixedAmount);
    }

    /**
     * 是否为临时加费（有期限，期满后恢复标准保费）。
     *
     * @return 临时加费返回 {@code true}
     */
    public boolean isTemporary() {
        return durationYears != null;
    }
}
