package com.titanium.underwriting.valueobject;

/**
 * Customer ID Value Object
 */
public record CustomerId(String value) {

    public static CustomerId of(String value) {
        return new CustomerId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
