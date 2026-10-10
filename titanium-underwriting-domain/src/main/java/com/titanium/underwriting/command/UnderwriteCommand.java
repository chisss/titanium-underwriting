package com.titanium.underwriting.command;

import java.math.BigDecimal;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.underwriting.valueobject.UnderwritingAmount;
import com.titanium.underwriting.valueobject.UnderwritingId;

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
 */
public record UnderwriteCommand(
        @TargetAggregateIdentifier UnderwritingId underwritingId,
        UnderwritingAmount amount,
        String reason,
        String processedBy,
        String tenantId,
        BigDecimal manualReviewAmountThreshold
) {
}
