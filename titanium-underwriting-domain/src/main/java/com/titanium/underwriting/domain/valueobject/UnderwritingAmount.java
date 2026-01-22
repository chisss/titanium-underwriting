package com.titanium.underwriting.domain.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Underwriting Amount Value Object
 */
@Getter
@EqualsAndHashCode
public class UnderwritingAmount {
    private final BigDecimal amount;
    private final String     currency;

    public UnderwritingAmount(BigDecimal amount, String currency) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Underwriting amount must be greater than or equal to zero");
        }
        if (currency == null || currency.trim().isEmpty()) {
            throw new IllegalArgumentException("Currency must not be empty");
        }
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        this.currency = currency;
    }

    public UnderwritingAmount(BigDecimal amount) {
        this(amount, "CNY"); // 默认人民币
    }

    public static UnderwritingAmount of(BigDecimal amount, String currency) {
        return new UnderwritingAmount(amount, currency);
    }

    public static UnderwritingAmount of(double amount, String currency) {
        return new UnderwritingAmount(BigDecimal.valueOf(amount), currency);
    }

    public UnderwritingAmount add(UnderwritingAmount other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot add amounts with different currencies");
        }
        return new UnderwritingAmount(this.amount.add(other.amount), this.currency);
    }

    @Override
    public String toString() {
        return amount + " " + currency;
    }
}
