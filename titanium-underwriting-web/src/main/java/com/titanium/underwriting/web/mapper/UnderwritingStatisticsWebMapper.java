package com.titanium.underwriting.web.mapper;

import org.mapstruct.Mapper;

import com.titanium.underwriting.api.response.UnderwritingStatisticsResponse;
import com.titanium.underwriting.query.result.UnderwritingStatisticsResult;

/**
 * 核保统计 Web 层对象映射器（MapStruct）
 * <p>
 * 将读侧统计结果 {@link UnderwritingStatisticsResult} 声明式映射为对外远程契约
 * {@link UnderwritingStatisticsResponse}。业务统计字段同名同类型自动映射，内部字段
 * （startTime/endTime/tenantId/generatedAt）不进入响应契约。
 * </p>
 */
@Mapper(componentModel = "spring")
public interface UnderwritingStatisticsWebMapper {

    /** 读侧统计结果 → 对外统计响应（业务字段同名自动映射） */
    UnderwritingStatisticsResponse toResponse(UnderwritingStatisticsResult result);
}
