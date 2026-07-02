package com.titanium.underwriting.domain.valueobject;

import java.util.UUID;

/**
 * Underwriting ID Value Object
 */
public record UnderwritingId(String value) {

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
