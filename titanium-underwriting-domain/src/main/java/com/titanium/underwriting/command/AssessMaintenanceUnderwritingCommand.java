package com.titanium.underwriting.command;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.underwriting.valueobject.MaintenanceRiskFieldChange;
import com.titanium.underwriting.valueobject.UnderwritingId;

/** 创建或重试保全核保评估。 */
public record AssessMaintenanceUnderwritingCommand(
        @TargetAggregateIdentifier UnderwritingId underwritingId,
        String tenantId,
        String maintenanceId,
        String policyId,
        Long policyBaselineVersion,
        String productId,
        String productVersion,
        String planVersion,
        String itemCode,
        String configurationVersion,
        String configurationContentHash,
        boolean configurationRequiresUnderwriting,
        List<MaintenanceRiskFieldChange> riskFieldChanges,
        String idempotencyKey,
        String payloadHash,
        String requestedBy) {

    public AssessMaintenanceUnderwritingCommand {
        riskFieldChanges = riskFieldChanges == null ? List.of() : List.copyOf(riskFieldChanges);
    }

    /** 以稳定字段顺序计算跨域请求摘要，核保聚合据此拒绝同键异载荷。 */
    public String calculatePayloadHash() {
        StringBuilder canonical = new StringBuilder();
        append(canonical, tenantId);
        append(canonical, maintenanceId);
        append(canonical, policyId);
        append(canonical, policyBaselineVersion == null ? null : policyBaselineVersion.toString());
        append(canonical, productId);
        append(canonical, productVersion);
        append(canonical, planVersion);
        append(canonical, itemCode);
        append(canonical, configurationVersion);
        append(canonical, configurationContentHash);
        append(canonical, Boolean.toString(configurationRequiresUnderwriting));
        append(canonical, idempotencyKey);
        riskFieldChanges.stream()
                .sorted(Comparator.comparing(MaintenanceRiskFieldChange::fieldCode)
                        .thenComparing(change -> nullToEmpty(change.objectId())))
                .forEach(change -> {
                    append(canonical, change.objectId());
                    append(canonical, change.fieldCode());
                    append(canonical, change.dataType());
                    append(canonical, change.beforeValue());
                    append(canonical, change.proposedValue());
                    append(canonical, change.changeTypeCode());
                });
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK缺少SHA-256算法", exception);
        }
    }

    private static void append(StringBuilder target, String value) {
        String normalized = value == null ? "" : value;
        target.append(normalized.length()).append(':').append(normalized).append('|');
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
