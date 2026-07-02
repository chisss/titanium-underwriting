package com.titanium.underwriting.domain.valueobject;

/**
 * Policy ID Value Object
 */
public record PolicyId(String value) {

    public static PolicyId of(String value) {
        return new PolicyId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
