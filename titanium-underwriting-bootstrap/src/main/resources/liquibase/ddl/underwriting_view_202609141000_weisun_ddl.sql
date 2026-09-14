--liquibase formatted sql

-- m7-1001：读模型补齐两条缺失的投影链路
--  ① UnderwritingInputSubmittedEvent（险种专属核保输入提交）→ 支撑「已提交输入、待决策」工作台筛选与决策前风险预览；
--  ② MaintenanceUnderwritingAssessedEvent（保全核保评估）→ 保全核保路径以 CREATE_IF_MISSING 建立聚合，
--     该事件可能是核保单的首个事件，此前读侧无投影致保全核保单在读模型完全缺失、且后续状态/决策事件
--     因查不到记录抛 PROJECTION_TARGET_MISSING 进 DLQ。
--changeset weisun:underwriting-006
ALTER TABLE t_underwriting_view
    ADD COLUMN input_submitted TINYINT(1) NULL COMMENT '是否已提交险种专属核保输入(核保决策的前提条件)',
    ADD COLUMN input_risk_score INT NULL COMMENT '提交输入时的综合风险评分(决策前预览,与决策事件 risk_score 同源)',
    ADD COLUMN maintenance_id VARCHAR(50) NULL COMMENT '保全单ID(保全核保路径特有)',
    ADD COLUMN maintenance_item_code VARCHAR(50) NULL COMMENT '保全事项编码',
    ADD COLUMN maintenance_conclusion VARCHAR(50) NULL COMMENT '保全核保结论(NOT_REQUIRED/APPROVED/CONDITIONAL_APPROVED/MANUAL_REVIEW/REJECTED)',
    ADD COLUMN maintenance_summary VARCHAR(500) NULL COMMENT '保全核保结论摘要',
    ADD COLUMN maintenance_additional_conditions_json TEXT NULL COMMENT '保全核保附加条件(JSON 数组,空表示无条件)',
    ADD COLUMN maintenance_completed_at DATETIME NULL COMMENT '保全核保完成时间(转人工复核时为空)';

--rollback ALTER TABLE t_underwriting_view DROP COLUMN input_submitted, DROP COLUMN input_risk_score, DROP COLUMN maintenance_id, DROP COLUMN maintenance_item_code, DROP COLUMN maintenance_conclusion, DROP COLUMN maintenance_summary, DROP COLUMN maintenance_additional_conditions_json, DROP COLUMN maintenance_completed_at;

-- 保全核保由保单触发、无客户维度，customer_id 需放开非空约束，否则保全核保单投影插入失败
--changeset weisun:underwriting-007
ALTER TABLE t_underwriting_view
    MODIFY COLUMN customer_id VARCHAR(50) NULL COMMENT '客户ID(保全核保路径无客户维度,可为空)';

--rollback ALTER TABLE t_underwriting_view MODIFY COLUMN customer_id VARCHAR(50) NOT NULL COMMENT '客户ID';
