package com.titanium.underwriting.domain.valueobject;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Customer ID Value Object
 */
@Getter
@EqualsAndHashCode
public class CustomerId {
    private final String value;

    public CustomerId(String value) {
        this.value = value;
    }

    public static CustomerId of(String value) {
        return new CustomerId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
