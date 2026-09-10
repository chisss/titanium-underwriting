--liquibase formatted sql
--changeset weisun:underwriting-003

-- N2 补丁：读模型增加除外原因列（规则引擎除外承保结论的原因说明，随决策事件投影落库）
ALTER TABLE t_underwriting_view
    ADD COLUMN exclusion_reason VARCHAR(2000) NULL COMMENT '除外原因（规则引擎除外承保结论的原因说明）'
        AFTER review_comments;

--rollback ALTER TABLE t_underwriting_view DROP COLUMN exclusion_reason;
