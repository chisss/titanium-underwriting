package com.titanium.underwriting.valueobject;

import com.titanium.underwriting.exception.UnderwritingValidationException;

/** 核保域接收的规范化保全字段差异。 */
public record MaintenanceRiskFieldChange(
        String objectId,
        String fieldCode,
        String dataType,
        String beforeValue,
        String proposedValue,
        String changeTypeCode) {

    public MaintenanceRiskFieldChange {
        objectId = normalize(objectId);
        fieldCode = requireText(fieldCode, "fieldCode");
        dataType = requireText(dataType, "dataType");
        changeTypeCode = requireText(changeTypeCode, "changeTypeCode");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new UnderwritingValidationException(
                    "MaintenanceRiskFieldChange", fieldName, "字段不能为空");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
