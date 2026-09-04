--liquibase formatted sql
--changeset weisun:underwriting-002

-- dev-505 补丁：结构化加费明细增加加费原因列（规则引擎加费结论的原因说明，随决策事件投影落库）
ALTER TABLE t_underwriting_view
    ADD COLUMN extra_premium_reason VARCHAR(500) NULL COMMENT '加费原因（规则引擎加费结论的原因说明）'
        AFTER extra_premium_fixed_amount;
