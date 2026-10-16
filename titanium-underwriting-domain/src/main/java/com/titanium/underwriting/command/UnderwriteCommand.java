package com.titanium.underwriting.command;

import java.math.BigDecimal;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.underwriting.valueobject.UnderwritingAmount;
import com.titanium.underwriting.valueobject.UnderwritingId;
import com.titanium.underwriting.valueobject.UnderwritingParameter;

/**
 * Underwrite Command
 *
 * @param underwritingId               核保聚合标识
 * @param amount                       保额（金额路由判定的比较基准）
 * @param reason                       执行原因
 * @param processedBy                  操作人
 * @param tenantId                     租户ID
 * @param manualReviewAmountThreshold  人工复核金额阈值（产品核保配置，g02-02）
 *                                     <p>
 *                                     🔴 由 application 层编排器经 {@code ProductUnderwritingConfigPort}
 *                                     取产品配置后充实；web/api 侧一律置 {@code null}
 *                                     （命令派发前必经编排器，直连不构成合法路径，同
 *                                     {@code DecideUnderwritingCommand.surchargeAcceptable} 先例）。
 *                                     {@code null} = 产品未配置或取不到 ⇒ 聚合根回退兜底默认阈值，
 *                                     **不表示「不限」**（与决策链路 {@code ProductUnderwritingConfig}
 *                                     的 null 语义相反，见该类 javadoc）。
 *                                     </p>
 * @param riskParameter                核保风险参数（评分数值外部化，DSN-09 / m25-11）
 *                                     <p>
 *                                     🔴 由 application 层金额路由编排器经配置
 *                                     （{@code titanium.underwriting.risk-parameter}，支持租户级覆盖）
 *                                     解析后充实；web/api 侧一律置 {@code null}
 *                                     （命令派发前必经编排器，直连不构成合法路径，同
 *                                     {@code manualReviewAmountThreshold} 先例）。
 *                                     {@code null} ⇒ 聚合根回退 {@link UnderwritingParameter#DEFAULT}
 *                                     （与改造前硬编码行为一致）。
 *                                     </p>
 */
public record UnderwriteCommand(
        @TargetAggregateIdentifier UnderwritingId underwritingId,
        UnderwritingAmount amount,
        String reason,
        String processedBy,
        String tenantId,
        BigDecimal manualReviewAmountThreshold,
        UnderwritingParameter riskParameter
) {
}
