-- liquibase formatted sql

-- changeset underwriting-query:1 labels:underwriting-query-tables
-- comment: 创建核保查询相关数据表

-- 创建核保查询表
CREATE TABLE t_underwriting_query (
    underwriting_id VARCHAR(50) NOT NULL COMMENT '核保ID',
    policy_id VARCHAR(50) NOT NULL COMMENT '保单ID',
    customer_id VARCHAR(50) NOT NULL COMMENT '客户ID',
    amount DECIMAL(15,2) COMMENT '核保金额',
    underwriting_type VARCHAR(100) COMMENT '核保类型',
    status VARCHAR(50) NOT NULL COMMENT '核保状态',
    reject_reason VARCHAR(1000) COMMENT '拒绝原因',
    review_comments VARCHAR(2000) COMMENT '审核意见',

    -- 核保依据相关信息
    risk_level VARCHAR(50) COMMENT '风险等级',
    conclusion_type VARCHAR(50) COMMENT '核保结论类型',
    audit_type VARCHAR(50) COMMENT '核保类型（自动/人工/混合）',
    underwriter_id VARCHAR(50) COMMENT '核保员ID',
    underwriter_name VARCHAR(100) COMMENT '核保员姓名',

    -- 风险评估结果
    risk_factors VARCHAR(2000) COMMENT '风险因子',
    premium_surcharge_rate DECIMAL(5,4) COMMENT '加费比例',
    surcharge_reason VARCHAR(1000) COMMENT '加费原因',
    exclusions VARCHAR(2000) COMMENT '除外责任',
    postpone_period_months INT COMMENT '延期期限（月）',
    postpone_reason VARCHAR(1000) COMMENT '延期原因',

    -- 第三方数据相关
    medical_exam_id VARCHAR(50) COMMENT '体检报告ID',
    medical_exam_status VARCHAR(50) COMMENT '体检状态',
    investigation_result VARCHAR(2000) COMMENT '第三方调查结果',
    industry_decline_record VARCHAR(2000) COMMENT '同业拒保记录',

    -- 核保流程相关
    underwriting_start_time DATETIME COMMENT '核保开始时间',
    underwriting_completed_time DATETIME COMMENT '核保完成时间',
    processing_hours INT COMMENT '核保时效（小时）',
    requires_review BOOLEAN COMMENT '是否需要复核',
    reviewer_id VARCHAR(50) COMMENT '复核员ID',
    reviewer_comments VARCHAR(2000) COMMENT '复核意见',

    -- 保费相关
    base_premium DECIMAL(15,2) COMMENT '基准保费',
    additional_premium DECIMAL(15,2) COMMENT '附加保费',
    final_premium DECIMAL(15,2) COMMENT '最终保费',
    discount_amount DECIMAL(15,2) COMMENT '优惠金额',

    -- 系统字段
    created_at DATETIME NOT NULL COMMENT '创建时间',
    created_by VARCHAR(50) NOT NULL COMMENT '创建人',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    updated_by VARCHAR(50) NOT NULL COMMENT '更新人',
    tenant_id VARCHAR(50) NOT NULL COMMENT '租户ID',
    version BIGINT DEFAULT 0 COMMENT '版本号（用于乐观锁）',

    PRIMARY KEY (underwriting_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='核保查询表';

-- 创建核保统计表
CREATE TABLE t_underwriting_statistics (
    id BIGINT AUTO_INCREMENT COMMENT '主键ID',
    start_time DATETIME NOT NULL COMMENT '统计开始时间',
    end_time DATETIME NOT NULL COMMENT '统计结束时间',
    tenant_id VARCHAR(50) NOT NULL COMMENT '租户ID',

    -- 核保数量统计
    total_count BIGINT NOT NULL DEFAULT 0 COMMENT '总核保件数',
    standard_count BIGINT NOT NULL DEFAULT 0 COMMENT '标准承保件数',
    surcharge_count BIGINT NOT NULL DEFAULT 0 COMMENT '加费承保件数',
    exclusion_count BIGINT NOT NULL DEFAULT 0 COMMENT '除外承保件数',
    postpone_count BIGINT NOT NULL DEFAULT 0 COMMENT '延期承保件数',
    decline_count BIGINT NOT NULL DEFAULT 0 COMMENT '拒保件数',

    -- 核保金额统计
    total_amount DECIMAL(20,2) NOT NULL DEFAULT 0.00 COMMENT '总核保金额',
    standard_amount DECIMAL(20,2) NOT NULL DEFAULT 0.00 COMMENT '标准承保金额',
    surcharge_amount DECIMAL(20,2) NOT NULL DEFAULT 0.00 COMMENT '加费承保金额',
    exclusion_amount DECIMAL(20,2) NOT NULL DEFAULT 0.00 COMMENT '除外承保金额',
    postpone_amount DECIMAL(20,2) NOT NULL DEFAULT 0.00 COMMENT '延期承保金额',
    decline_amount DECIMAL(20,2) NOT NULL DEFAULT 0.00 COMMENT '拒保金额',

    -- 核保效率统计
    average_processing_hours DECIMAL(8,2) COMMENT '平均核保时效（小时）',
    auto_underwriting_count BIGINT NOT NULL DEFAULT 0 COMMENT '自动核保件数',
    manual_underwriting_count BIGINT NOT NULL DEFAULT 0 COMMENT '人工核保件数',
    hybrid_underwriting_count BIGINT NOT NULL DEFAULT 0 COMMENT '混合核保件数',

    -- 风险等级统计
    low_risk_count BIGINT NOT NULL DEFAULT 0 COMMENT '低风险件数',
    medium_risk_count BIGINT NOT NULL DEFAULT 0 COMMENT '中风险件数',
    high_risk_count BIGINT NOT NULL DEFAULT 0 COMMENT '高风险件数',

    -- 核保通过率统计
    overall_pass_rate DECIMAL(5,4) COMMENT '总通过率',
    standard_pass_rate DECIMAL(5,4) COMMENT '标准承保率',
    surcharge_pass_rate DECIMAL(5,4) COMMENT '加费承保率',
    exclusion_pass_rate DECIMAL(5,4) COMMENT '除外承保率',
    decline_rate DECIMAL(5,4) COMMENT '拒保率',

    -- 系统字段
    generated_at DATETIME NOT NULL COMMENT '统计生成时间',
    generated_by VARCHAR(50) NOT NULL COMMENT '统计生成人',

    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='核保统计表';

CREATE TABLE t_underwriting (
                                underwriting_id VARCHAR(36) NOT NULL,
                                policy_id VARCHAR(36) NOT NULL,
                                customer_id VARCHAR(36) NOT NULL,
                                amount DECIMAL(18,2) NOT NULL,
                                underwriting_type VARCHAR(50) NOT NULL,
                                status VARCHAR(20) NOT NULL,
                                review_result VARCHAR(20),
                                review_reason TEXT,
                                reviewer_id VARCHAR(36),
                                reviewed_at DATETIME,
                                tenant_id VARCHAR(36) NOT NULL,
                                created_by VARCHAR(50) NOT NULL,
                                created_at DATETIME NOT NULL,
                                updated_by VARCHAR(50) NOT NULL,
                                updated_at DATETIME NOT NULL,
                                PRIMARY KEY (underwriting_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='核保表';


-- rollback DROP TABLE underwriting_statistics;
-- rollback DROP TABLE underwriting_query;