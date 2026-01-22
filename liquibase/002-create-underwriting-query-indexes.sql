-- liquibase formatted sql

-- changeset underwriting-query:2 labels:underwriting-query-indexes
-- comment: 创建核保查询表的性能优化索引

-- 核保查询表索引
CREATE INDEX idx_underwriting_query_tenant_id ON t_underwriting_query(tenant_id);
CREATE INDEX idx_underwriting_query_policy_id ON t_underwriting_query(policy_id, tenant_id);
CREATE INDEX idx_underwriting_query_customer_id ON t_underwriting_query(customer_id, tenant_id);
CREATE INDEX idx_underwriting_query_status ON t_underwriting_query(status, tenant_id);
CREATE INDEX idx_underwriting_query_risk_level ON t_underwriting_query(risk_level, tenant_id);
CREATE INDEX idx_underwriting_query_underwriter ON t_underwriting_query(underwriter_id, tenant_id);
CREATE INDEX idx_underwriting_query_created_at ON t_underwriting_query(created_at, tenant_id);
CREATE INDEX idx_underwriting_query_audit_type ON t_underwriting_query(audit_type, tenant_id);

-- 复合索引用于多条件查询优化
CREATE INDEX idx_underwriting_query_multi_condition ON t_underwriting_query(
    status, risk_level, audit_type, underwriter_id, tenant_id, created_at
);

-- 待处理任务查询优化索引
CREATE INDEX idx_underwriting_query_pending_tasks ON t_underwriting_query(
    status, underwriter_id, tenant_id, created_at
) WHERE status IN ('PENDING', 'IN_REVIEW', 'WAITING_FOR_DOCUMENTS');

-- 核保统计表索引
CREATE INDEX idx_underwriting_statistics_tenant_id ON t_underwriting_statistics(tenant_id);
CREATE INDEX idx_underwriting_statistics_time_range ON t_underwriting_statistics(start_time, end_time, tenant_id);
CREATE INDEX idx_underwriting_statistics_generated_at ON t_underwriting_statistics(generated_at, tenant_id);

-- 唯一索引确保同一时间范围和租户的统计记录唯一性
CREATE UNIQUE INDEX uk_underwriting_statistics_time_tenant ON t_underwriting_statistics(start_time, end_time, tenant_id);

-- rollback DROP INDEX uk_underwriting_statistics_time_tenant ON underwriting_statistics;
-- rollback DROP INDEX idx_underwriting_statistics_generated_at ON underwriting_statistics;
-- rollback DROP INDEX idx_underwriting_statistics_time_range ON underwriting_statistics;
-- rollback DROP INDEX idx_underwriting_statistics_tenant_id ON underwriting_statistics;
-- rollback DROP INDEX idx_underwriting_query_pending_tasks ON underwriting_query;
-- rollback DROP INDEX idx_underwriting_query_multi_condition ON underwriting_query;
-- rollback DROP INDEX idx_underwriting_query_audit_type ON underwriting_query;
-- rollback DROP INDEX idx_underwriting_query_created_at ON underwriting_query;
-- rollback DROP INDEX idx_underwriting_query_underwriter ON underwriting_query;
-- rollback DROP INDEX idx_underwriting_query_risk_level ON underwriting_query;
-- rollback DROP INDEX idx_underwriting_query_status ON underwriting_query;
-- rollback DROP INDEX idx_underwriting_query_customer_id ON underwriting_query;
-- rollback DROP INDEX idx_underwriting_query_policy_id ON underwriting_query;
-- rollback DROP INDEX idx_underwriting_query_tenant_id ON underwriting_query;
-- 为 policy_id 字段创建索引
CREATE INDEX idx_underwriting_policy_id ON t_underwriting(policy_id);

-- 为 customer_id 字段创建索引
CREATE INDEX idx_underwriting_customer_id ON t_underwriting(customer_id);

-- 为 status 字段创建索引
CREATE INDEX idx_underwriting_status ON t_underwriting(status);

-- 为 tenant_id 字段创建索引
CREATE INDEX idx_underwriting_tenant_id ON t_underwriting(tenant_id);