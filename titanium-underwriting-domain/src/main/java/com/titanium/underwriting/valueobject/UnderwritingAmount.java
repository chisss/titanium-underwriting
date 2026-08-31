package com.titanium.underwriting.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.metadata.errorcode.UnderwritingErrorCode;
import com.titanium.underwriting.exception.UnderwritingValidationException;

/**
 * Underwriting Amount Value Object
 */
public record UnderwritingAmount(BigDecimal amount, CurrencyEnum currency) {
    public UnderwritingAmount(BigDecimal amount, CurrencyEnum currency) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.UNDERWRITING_AMOUNT_NEGATIVE, "UnderwritingAmount", "amount");
        }
        if (currency == null) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.CURRENCY_REQUIRED, "UnderwritingAmount", "currency");
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
            throw new UnderwritingValidationException(UnderwritingErrorCode.CURRENCY_MISMATCH, "UnderwritingAmount", "currency");
        }
        return new UnderwritingAmount(this.amount.add(other.amount), this.currency);
    }

    @Override
    public String toString() {
        return amount + " " + currency;
    }
}
