package com.titanium.underwriting.generator;

/**
 * 核保域业务案号发号端口。
 * <p>
 * 编号分配属于需要持久化原子性的技术能力，领域层只依赖本端口，不保存进程内流水状态。
 * 与保单域 POL/INS/PRP 同源发号体系（公共流水表 {@code t_business_number_sequence}）。
 * </p>
 */
public interface UnderwritingNoGenerator {

    /**
     * 生成核保案号。
     *
     * @param tenantId 租户ID
     * @return 核保案号，例如 {@code UW202609090000001}
     */
    String generateUnderwritingNo(String tenantId);
}
