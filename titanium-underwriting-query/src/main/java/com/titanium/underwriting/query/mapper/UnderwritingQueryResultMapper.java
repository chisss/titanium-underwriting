package com.titanium.underwriting.query.mapper;

import java.time.Duration;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

import com.titanium.underwriting.query.result.UnderwritingQueryResult;
import com.titanium.underwriting.query.view.UnderwritingView;

/**
 * 核保读模型 → 查询结果映射器（MapStruct）
 * <p>
 * 将读模型实体 {@link UnderwritingView} 声明式映射为查询结果 DTO {@link UnderwritingQueryResult}，
 * 取代查询服务中逐字段 set（原 22 次 set 收敛为单个映射方法）。业务更新时间字段名差异
 * （View 基类 updateTime → Result.updatedAt）经 {@code @Mapping} 声明；其余字段同名自动映射，
 * 租户/乐观锁版本等基类字段（{@code tenantId}/{@code version}）一并自动拷贝。
 * </p>
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UnderwritingQueryResultMapper {

    /**
     * 读模型实体 → 查询结果 DTO
     *
     * @param view 核保读模型
     * @return 核保查询结果
     */
    @Mapping(target = "updatedAt", source = "updateTime")
    @Mapping(target = "underwritingStartTime", source = "createdAt")
    UnderwritingQueryResult toQueryResult(UnderwritingView view);

    /**
     * 派生核保时效（小时）= 完成时间 − 开始时间（D-501-44）
     * <p>
     * 🔴 <b>不落库</b>：时效由两个时间戳派生，写成读模型列会与源字段漂移（源改了派生值不跟）。
     * 二者任一缺失时不计算（保持空值，前端以 {@code -} 兜底），未决策的核保单即属此列。
     * </p>
     */
    @AfterMapping
    default void fillProcessingHours(@MappingTarget UnderwritingQueryResult result) {
        if (result.getUnderwritingStartTime() == null || result.getUnderwritingCompletedTime() == null) {
            return;
        }
        result.setProcessingHours(
                (int) Duration.between(result.getUnderwritingStartTime(), result.getUnderwritingCompletedTime())
                        .toHours());
    }
}
