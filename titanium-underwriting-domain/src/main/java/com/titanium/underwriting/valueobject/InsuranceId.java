package com.titanium.underwriting.valueobject;

/**
 * Insurance ID Value Object（投保单号）
 * <p>
 * 🔴 g02-04 跨域标识正名新增：核保域此前只有 {@link PolicyId} 一个关联键字段，出单主链路发起的新单核保
 * 把**投保单号**写进了 {@code policyId}（见 policy 域 {@code SyncUnderwritingDecisionAdapter} 改造前实现），
 * 而保全核保路径写入的 {@code policyId} 是**真实保单号**——同一列承载两种语义，按它做幂等键会错配。
 * </p>
 * <p>
 * 本值对象承载核保件的**投保单维度**关联键（新单核保的来源单据号），与 {@code policyId} **并存**：
 * 存量字段保留且继续填充（兼容既有事件流与读模型列），新字段表达真实语义。
 * 核保域不感知「保单」与「投保单」的领域差异，只保证两个维度各自可被独立检索。
 * </p>
 *
 * @param value 投保单号
 */
public record InsuranceId(String value) {

    public static InsuranceId of(String value) {
        return new InsuranceId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
