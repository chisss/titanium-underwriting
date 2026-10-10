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

    /**
     * 按租户与投保单号生成稳定的自动决策核保案件号（g02-04 / AC-05）。
     *
     * <p>
     * 🔴 <b>为何由业务键派生案件号</b>：粗粒度端点 {@code :auto-decide} 以投保单号为幂等键，承诺
     * 「同键重放不产生第二张核保单」。若案件号取 {@link #generate()} 的雪花号，幂等就只能靠 CQRS 读模型
     * 反查——而读模型由<b>异步投影</b>维护。首次调用返回后立即重放时投影尚未追平，反查落空，编排器遂判
     * 「无既有核保单」而<b>再建一张</b>（真机实测：210ms 间隔重放即产生两张核保单）。改由
     * <b>(租户, 投保单号) 派生</b>后，同键必得同号：幂等由<b>聚合标识本身</b>保证，不依赖投影、不依赖任何索引。
     * </p>
     *
     * <p>
     * 🔴 <b>并发同键也只可能有一张</b>：事件存储对 {@code (aggregate_identifier, sequence_number)} 有唯一约束
     * （{@code uk_dee_agg_seq}），两个并发请求派生出同一案件号时，只有一个能追加序号 0 的创建事件，落败方
     * <b>显式失败</b>而非静默产生第二张核保单。
     * </p>
     *
     * <p>
     * 与 {@link #forMaintenance(String, String)} <b>同型</b>——保全核保早已用同一手法解决同一问题，前缀
     * {@code AUW-} 与之区分。两者都不是雪花数字，故核保单号<b>不是</b>纯数字（已核验 policy / underwriting
     * 两域无 {@code parseLong} 类数字解析，跨域契约中一律为 {@code String}）。
     * </p>
     *
     * @param tenantId    租户ID
     * @param insuranceId 投保单号（幂等键）
     * @return 稳定案件号
     */
    public static UnderwritingId forAutoDecide(String tenantId, String insuranceId) {
        String source = tenantId + "|" + insuranceId;
        return new UnderwritingId("AUW-" + UUID.nameUUIDFromBytes(
                source.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public String toString() {
        return value;
    }
}
