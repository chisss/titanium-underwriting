--liquibase formatted sql

--changeset weisun:underwriting-005
ALTER TABLE t_underwriting_view
    ADD COLUMN case_no VARCHAR(50) NULL COMMENT '核保案号（UW 前缀业务号，创建时由应用层发号生成）'
        AFTER underwriting_id;

--rollback ALTER TABLE t_underwriting_view DROP COLUMN case_no;
