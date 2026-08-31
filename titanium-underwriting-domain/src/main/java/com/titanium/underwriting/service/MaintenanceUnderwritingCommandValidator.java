package com.titanium.underwriting.service;

import com.titanium.metadata.errorcode.UnderwritingErrorCode;
import com.titanium.underwriting.command.AssessMaintenanceUnderwritingCommand;
import com.titanium.underwriting.exception.UnderwritingValidationException;

/**
 * 保全核保命令校验器（独立校验器，红线 22）
 * <p>
 * 从 {@code Underwriting} 聚合根中抽取的保全核保命令参数校验链（原 14 条 if 校验），
 * 独立成纯领域校验器：无 CommandGateway、无外部 Port、无基础设施依赖，仅对命令
 * record 做参数完整性校验并抛携带 {@link UnderwritingErrorCode} 枚举码的领域异常。
 * 幂等载荷一致性等依赖聚合状态的校验仍留在聚合根命令处理器内。
 * </p>
 *
 * @author wei.sun
 * @since 2026/8/31
 */
public class MaintenanceUnderwritingCommandValidator {

    /** 命令名（用于校验异常上下文） */
    private static final String COMMAND_NAME = "AssessMaintenanceUnderwritingCommand";

    /** 载荷摘要格式：64 位小写十六进制（SHA-256） */
    private static final String PAYLOAD_HASH_PATTERN = "[0-9a-f]{64}";

    /**
     * 校验保全核保命令参数完整性，不通过即抛携带标准错误码的领域异常
     *
     * @param command 保全核保评估命令
     */
    public void validate(AssessMaintenanceUnderwritingCommand command) {
        if (command.underwritingId() == null) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.MAINTENANCE_CASE_ID_REQUIRED,
                    COMMAND_NAME, "underwritingId");
        }
        requireText("tenantId", command.tenantId());
        requireText("maintenanceId", command.maintenanceId());
        requireText("policyId", command.policyId());
        requireText("productId", command.productId());
        requireText("productVersion", command.productVersion());
        requireText("itemCode", command.itemCode());
        requireText("configurationVersion", command.configurationVersion());
        requireText("configurationContentHash", command.configurationContentHash());
        requireText("idempotencyKey", command.idempotencyKey());
        requireText("payloadHash", command.payloadHash());
        requireText("requestedBy", command.requestedBy());
        if (command.policyBaselineVersion() == null || command.policyBaselineVersion() < 0) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.BASELINE_VERSION_INVALID,
                    COMMAND_NAME, "policyBaselineVersion");
        }
        if (!command.payloadHash().matches(PAYLOAD_HASH_PATTERN)
                || !command.payloadHash().equals(command.calculatePayloadHash())) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.PAYLOAD_HASH_MISMATCH,
                    COMMAND_NAME, "payloadHash");
        }
    }

    /**
     * 必填文本字段校验
     *
     * @param fieldName 字段名
     * @param value     字段值
     */
    private void requireText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.FIELD_REQUIRED, COMMAND_NAME, fieldName);
        }
    }
}
