package com.titanium.underwriting.domain.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.underwriting.domain.exception.UnderwritingValidationException;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Underwriting Amount Value Object
 */
@Getter
@EqualsAndHashCode
public class UnderwritingAmount {
    private final BigDecimal   amount;
    private final CurrencyEnum currency;

    public UnderwritingAmount(BigDecimal amount, CurrencyEnum currency) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new UnderwritingValidationException("UnderwritingAmount", "amount", "核保金额必须大于或等于零");
        }
        if (currency == null) {
            throw new UnderwritingValidationException("UnderwritingAmount", "currency", "币种不能为空");
        }
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        this.currency = currency;
    }

    public UnderwritingAmount(BigDecimal amount) {
        this(amount, CurrencyEnum.CNY); // 默认人民币
    }

    public static UnderwritingAmount of(BigDecimal amount, CurrencyEnum currency) {
        return new UnderwritingAmount(amount, currency);
    }

    public static UnderwritingAmount of(double amount, CurrencyEnum currency) {
        return new UnderwritingAmount(BigDecimal.valueOf(amount), currency);
    }

    public UnderwritingAmount add(UnderwritingAmount other) {
        // 枚举用 == 比较币种是否一致
        if (this.currency != other.currency) {
            throw new UnderwritingValidationException("UnderwritingAmount", "currency", "不能对不同币种的金额进行相加");
        }
        return new UnderwritingAmount(this.amount.add(other.amount), this.currency);
    }

    @Override
    public String toString() {
        return amount + " " + currency;
    }
}
