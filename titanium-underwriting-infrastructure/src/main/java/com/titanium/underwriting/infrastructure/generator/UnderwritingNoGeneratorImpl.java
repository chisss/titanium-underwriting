package com.titanium.underwriting.infrastructure.generator;

import org.springframework.stereotype.Service;

import com.titanium.common.number.JdbcBusinessNumberGenerator;
import com.titanium.underwriting.generator.UnderwritingNoGenerator;

import lombok.RequiredArgsConstructor;

/**
 * 核保案号生成器实现。
 * <p>
 * 复用共享内核 {@link JdbcBusinessNumberGenerator} 的持久化原子发号能力
 * （公共流水表 {@code t_business_number_sequence}，序列按租户、号类型和业务日隔离），
 * 案号前缀 {@code UW}，与保单域 POL/INS/PRP 同源体系。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class UnderwritingNoGeneratorImpl implements UnderwritingNoGenerator {

    /** 号类型（公共流水表 number_type 列） */
    private static final String NUMBER_TYPE = "UNDERWRITING";
    /** 案号前缀（UW 开头，对齐保单号 POL 约定） */
    private static final String PREFIX = "UW";

    private final JdbcBusinessNumberGenerator businessNumberGenerator;

    @Override
    public String generateUnderwritingNo(String tenantId) {
        return businessNumberGenerator.next(tenantId, NUMBER_TYPE, PREFIX);
    }
}
