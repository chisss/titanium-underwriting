package com.titanium.underwriting.domain.valueobject;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Policy ID Value Object
 */
@Getter
@EqualsAndHashCode
public class PolicyId {
    private final String value;

    public PolicyId(String value) {
        this.value = value;
    }

    public static PolicyId of(String value) {
        return new PolicyId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
