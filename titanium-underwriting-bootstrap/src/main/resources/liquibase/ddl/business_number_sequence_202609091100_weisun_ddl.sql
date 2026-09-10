--liquibase formatted sql
-- 公共业务号发号表（共享内核 JdbcBusinessNumberGenerator 依赖）：序列按租户、号类型和业务日隔离
-- 核保案号（number_type=UNDERWRITING，前缀 UW）与保单 POL/INS/PRP 同源发号体系

--changeset weisun:underwriting-004
CREATE TABLE IF NOT EXISTS t_business_number_sequence (
    tenant_id      VARCHAR(64) NOT NULL COMMENT '租户ID',
    number_type    VARCHAR(32) NOT NULL COMMENT '号类型，如 UNDERWRITING/CUSTOMER/CLAUSE',
    business_date  DATE        NOT NULL COMMENT '业务日期',
    last_sequence  BIGINT      NOT NULL COMMENT '最近一次已分配流水',
    create_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (tenant_id, number_type, business_date),
    CONSTRAINT ck_business_number_sequence_positive CHECK (last_sequence >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户级业务号原子流水表';
--rollback DROP TABLE IF EXISTS t_business_number_sequence;
