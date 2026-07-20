--liquibase formatted sql

-- 核保读模型表：字段严格对齐 UnderwritingView.java（继承 BaseView：tenant_id/create_time/update_time/version）
--changeset weisun:underwriting-view-1
CREATE TABLE IF NOT EXISTS t_underwriting_view (
    underwriting_id  VARCHAR(50)   NOT NULL COMMENT '核保ID(聚合根ID,读模型主键)',
    policy_id        VARCHAR(50)   NOT NULL COMMENT '保单ID',
    customer_id      VARCHAR(50)   NOT NULL COMMENT '客户ID',
    amount           DECIMAL(18,2)          COMMENT '核保金额',
    underwriting_type VARCHAR(50)           COMMENT '核保类型(新单/续保/保全/复效)',
    status           VARCHAR(50)   NOT NULL COMMENT '核保状态',
    reject_reason    VARCHAR(1000)          COMMENT '拒保原因',
    review_comments  VARCHAR(2000)          COMMENT '审核意见',
    risk_level       VARCHAR(50)            COMMENT '风险等级',
    conclusion_type  VARCHAR(50)            COMMENT '核保结论类型',
    audit_type       VARCHAR(50)            COMMENT '核保方式(自动/人工/混合)',
    underwriter_id   VARCHAR(50)            COMMENT '核保员ID',
    risk_score       INT                    COMMENT '综合风险评分',
    created_by       VARCHAR(50)            COMMENT '业务创建人',
    updated_by       VARCHAR(50)            COMMENT '业务更新人',
    created_at       DATETIME               COMMENT '业务创建时间(来源事件)',
    tenant_id        VARCHAR(32)   NOT NULL COMMENT '租户ID',
    create_time      DATETIME      NOT NULL COMMENT '投影创建时间',
    update_time      DATETIME      NOT NULL COMMENT '投影更新时间',
    version          BIGINT                 COMMENT '乐观锁版本号',
    PRIMARY KEY (underwriting_id),
    KEY idx_uw_view_tenant_id (tenant_id),
    KEY idx_uw_view_policy_id (policy_id, tenant_id),
    KEY idx_uw_view_customer_id (customer_id, tenant_id),
    KEY idx_uw_view_status (status, tenant_id),
    KEY idx_uw_view_risk_level (risk_level, tenant_id),
    KEY idx_uw_view_underwriter (underwriter_id, tenant_id),
    KEY idx_uw_view_audit_type (audit_type, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='核保读模型表';
--rollback DROP TABLE IF EXISTS t_underwriting_view;

-- UW-3：结构化加费字段（核保域 ExtraPremium 投影，供 policy 出单读取并入保费）
--changeset weisun:underwriting-view-2
ALTER TABLE t_underwriting_view
    ADD COLUMN extra_premium_type VARCHAR(50) COMMENT '加费类型(PERMANENT_RATIO/TEMPORARY_RATIO/FIXED_AMOUNT)',
    ADD COLUMN extra_premium_ratio DECIMAL(10,4) COMMENT '加费率(比例加费时用,如0.30表示加费30%)',
    ADD COLUMN extra_premium_fixed_amount DECIMAL(18,2) COMMENT '固定加费额(固定额加费时用)';
--rollback ALTER TABLE t_underwriting_view DROP COLUMN extra_premium_type, DROP COLUMN extra_premium_ratio, DROP COLUMN extra_premium_fixed_amount;
