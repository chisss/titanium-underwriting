package com.titanium.underwriting.valueobject;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.titanium.common.util.SnowflakeIdGenerator;

/**
 * Underwriting ID Value Object
 */
public record UnderwritingId(String value) {

    public static UnderwritingId generate() {
        return new UnderwritingId(SnowflakeIdGenerator.generate());
    }

    public static UnderwritingId of(String value) {
        return new UnderwritingId(value);
    }

    /** 按租户和调用方幂等键生成稳定的保全核保案件号。 */
    public static UnderwritingId forMaintenance(String tenantId, String idempotencyKey) {
        String source = tenantId + "|" + idempotencyKey;
        return new UnderwritingId("MUW-" + UUID.nameUUIDFromBytes(
                source.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public String toString() {
        return value;
    }
}
