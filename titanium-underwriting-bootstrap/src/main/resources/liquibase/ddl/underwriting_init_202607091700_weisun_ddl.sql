--liquibase formatted sql
-- 说明：以下三张表在 titanium-underwriting 中无对应 JPA 实体，按《全域DDL重建方案清单》§3.6
-- 字段清单 + 多租户七件套建表（健康告知 / 体检记录 / 核保决策），供后续实体落地时对齐。

-- 健康告知表
--changeset weisun:underwriting-init-1
CREATE TABLE IF NOT EXISTS t_health_notice (
    id              VARCHAR(32)  NOT NULL COMMENT '主键(雪花)',
    underwriting_id VARCHAR(50)  NOT NULL COMMENT '核保ID',
    question_code   VARCHAR(64)           COMMENT '告知问题编码',
    answer          VARCHAR(512)          COMMENT '告知答复',
    remark          VARCHAR(1000)         COMMENT '备注',
    tenant_id       VARCHAR(32)  NOT NULL COMMENT '租户ID',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by      VARCHAR(32)  NOT NULL DEFAULT 'system' COMMENT '创建人',
    updated_by      VARCHAR(32)  NOT NULL DEFAULT 'system' COMMENT '更新人',
    is_deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除(0否1是)',
    PRIMARY KEY (id),
    KEY idx_health_notice_tenant (tenant_id),
    KEY idx_health_notice_uw (underwriting_id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='健康告知表(无实体,按方案清单建)';
--rollback DROP TABLE IF EXISTS t_health_notice;

-- 体检记录表
--changeset weisun:underwriting-init-2
CREATE TABLE IF NOT EXISTS t_medical_exam (
    id              VARCHAR(32)  NOT NULL COMMENT '主键(雪花)',
    underwriting_id VARCHAR(50)  NOT NULL COMMENT '核保ID',
    exam_item       VARCHAR(128)          COMMENT '体检项目',
    result          VARCHAR(512)          COMMENT '体检结果',
    abnormal_flag   TINYINT      NOT NULL DEFAULT 0 COMMENT '异常标识(0正常1异常)',
    tenant_id       VARCHAR(32)  NOT NULL COMMENT '租户ID',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by      VARCHAR(32)  NOT NULL DEFAULT 'system' COMMENT '创建人',
    updated_by      VARCHAR(32)  NOT NULL DEFAULT 'system' COMMENT '更新人',
    is_deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除(0否1是)',
    PRIMARY KEY (id),
    KEY idx_medical_exam_tenant (tenant_id),
    KEY idx_medical_exam_uw (underwriting_id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='体检记录表(无实体,按方案清单建)';
--rollback DROP TABLE IF EXISTS t_medical_exam;

-- 核保决策表
--changeset weisun:underwriting-init-3
CREATE TABLE IF NOT EXISTS t_underwriting_decision (
    id                VARCHAR(32)  NOT NULL COMMENT '主键(雪花)',
    underwriting_id   VARCHAR(50)  NOT NULL COMMENT '核保ID',
    conclusion        VARCHAR(32)           COMMENT '核保结论(标准/加费/除外/延期/拒保)',
    extra_premium_rate DECIMAL(5,4)         COMMENT '加费比例',
    exclusion_content VARCHAR(2000)         COMMENT '除外责任内容',
    tenant_id         VARCHAR(32)  NOT NULL COMMENT '租户ID',
    create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by        VARCHAR(32)  NOT NULL DEFAULT 'system' COMMENT '创建人',
    updated_by        VARCHAR(32)  NOT NULL DEFAULT 'system' COMMENT '更新人',
    is_deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除(0否1是)',
    PRIMARY KEY (id),
    KEY idx_uw_decision_tenant (tenant_id),
    KEY idx_uw_decision_uw (underwriting_id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='核保决策表(无实体,按方案清单建)';
--rollback DROP TABLE IF EXISTS t_underwriting_decision;
