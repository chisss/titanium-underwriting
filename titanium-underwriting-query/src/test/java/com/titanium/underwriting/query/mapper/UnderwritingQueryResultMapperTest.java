package com.titanium.underwriting.query.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.titanium.underwriting.query.result.UnderwritingQueryResult;
import com.titanium.underwriting.query.view.UnderwritingView;

/**
 * 核保读模型 → 查询结果映射器测试（D-501-44）。
 * <p>
 * 🔴 直接断言生成实现 {@link UnderwritingQueryResultMapperImpl}：映射器 {@code unmappedTargetPolicy}
 * 为 {@code IGNORE}，源字段写错时 MapStruct 只**静默漏字段**、不报编译错，契约字段将恒为空
 * （D-501-44 的成因即此类静默漏映射）。故对三个流程时间字段逐项断言。
 * </p>
 */
class UnderwritingQueryResultMapperTest {

    private final UnderwritingQueryResultMapper mapper = new UnderwritingQueryResultMapperImpl();

    /**
     * 核保开始时间 = 核保单创建时间（受理即开始）：契约字段名与读模型列名不同，靠显式 {@code @Mapping} 承接。
     */
    @Test
    void mapsBusinessCreatedAtToUnderwritingStartTime() {
        UnderwritingView view = new UnderwritingView();
        view.setCreatedAt(LocalDateTime.parse("2026-09-16T09:00:00"));

        UnderwritingQueryResult result = mapper.toQueryResult(view);

        assertEquals(LocalDateTime.parse("2026-09-16T09:00:00"), result.getUnderwritingStartTime(),
                "核保开始时间须由读模型 created_at 承接，否则详情页该行恒显示 -");
    }

    /**
     * 处理耗时由两个时间戳派生，**不落库**：落库会与源字段漂移。
     */
    @Test
    void derivesProcessingHoursFromStartAndCompletedTime() {
        UnderwritingView view = new UnderwritingView();
        view.setCreatedAt(LocalDateTime.parse("2026-09-16T09:00:00"));
        view.setUnderwritingCompletedTime(LocalDateTime.parse("2026-09-17T14:30:00"));

        UnderwritingQueryResult result = mapper.toQueryResult(view);

        assertEquals(29, result.getProcessingHours(), "29.5 小时按整点截断为 29");
    }

    /**
     * 未出具结论的核保单完成时间为空 ⇒ 时效同样为空（前端以 {@code -} 兜底），不得算出负数或 0。
     */
    @Test
    void leavesProcessingHoursNullWhenNotDecidedYet() {
        UnderwritingView view = new UnderwritingView();
        view.setCreatedAt(LocalDateTime.parse("2026-09-16T09:00:00"));

        UnderwritingQueryResult result = mapper.toQueryResult(view);

        assertNull(result.getUnderwritingCompletedTime());
        assertNull(result.getProcessingHours(), "未决策不得产出时效数值");
    }
}
