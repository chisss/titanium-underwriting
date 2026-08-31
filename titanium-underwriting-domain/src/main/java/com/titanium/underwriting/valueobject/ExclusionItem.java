package com.titanium.underwriting.valueobject;

import java.io.Serializable;

import com.titanium.metadata.errorcode.UnderwritingErrorCode;
import com.titanium.underwriting.exception.UnderwritingValidationException;

/**
 * 除外责任值对象（核保修改条件承保的结构化除外明细）
 * <p>
 * 核保「修改条件承保」（{@code ConclusionType.MODIFY}）时，对特定既往症/高风险活动约定除外责任
 * ——该风险导致的保险事故不予赔付，以此换取承保。替代原先仅 {@code UnderwritingStatus.EXCLUDED}
 * 状态码：除外需结构化承载除外项与范围，供保单条款落地与理赔时责任判定。
 * </p>
 *
 * @param exclusionCode 除外项编码（如既往症 ICD 码、高风险活动码）
 * @param description 除外范围描述（如"甲状腺结节及其并发症相关疾病"）
 * @param permanent 是否永久除外（false 表示观察期后可申请恢复责任）
 */
public record ExclusionItem(String exclusionCode, String description, boolean permanent)
        implements
            Serializable {

    public ExclusionItem {
        if (description == null || description.isBlank()) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.EXCLUSION_DESCRIPTION_REQUIRED,
                    "ExclusionItem");
        }
    }
}
