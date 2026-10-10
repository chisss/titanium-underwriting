package com.titanium.underwriting.valueobject;

import java.io.Serializable;
import java.util.Objects;

import com.titanium.metadata.enums.underwriting.HealthDeclarationQuestion;

/**
 * 告知项答案值对象（G12/g12-02）
 * <p>
 * 产品自定义告知项的问-答对：{@code question} 为元数据侧问题编码（权威值域），{@code answer} 为
 * 客户作答载荷（跨域契约以字符串承载 {@code true/false}）。
 * </p>
 * <p>
 * 🔴 <b>核保域只承接「问了什么、答了什么」</b>，不承载产品侧的必答/处置配置——按配置项拦截出单
 * 是保单域出单校验（{@code ProductIssueRules.healthDeclarationItems}）的职责，本域不做第二权威。
 * </p>
 *
 * @param question 告知问题编码（非空；未知编码在装配边界已被跳过留痕）
 * @param answer   客户作答（字符串形态；{@code null}=契约给了空值，按未作答承接）
 * @author wei.sun
 * @since 2026/10/10
 */
public record HealthDeclarationAnswer(HealthDeclarationQuestion question, String answer) implements Serializable {

    public HealthDeclarationAnswer {
        Objects.requireNonNull(question, "告知问题编码不得为空");
    }
}
