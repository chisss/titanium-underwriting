package com.titanium.underwriting.domain.valueobject;

import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Underwriting ID Value Object
 */
@Getter
@EqualsAndHashCode
public class UnderwritingId {
    private final String value;

    public UnderwritingId(String value) {
        this.value = value;
    }

    public static UnderwritingId generate() {
        return new UnderwritingId(UUID.randomUUID().toString());
    }

    public static UnderwritingId of(String value) {
        return new UnderwritingId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
