--liquibase formatted sql

-- D-501-44：核保「完成时间 / 处理耗时」恒显示 `-` —— 读模型连列都没有
--   决策事件载荷 UnderwritingDecidedEvent.decidedAt 100% 有值（77/77，阳性对照 decidedBy 77/77），
--   聚合回放亦用它重建状态（Underwriting.java:391 this.updateTime = event.decidedAt()），
--   但读模型侧无任何列承接 ⇒ 列表「核保完成时间」与详情「核保完成时间 / 处理耗时」恒空。
--   「核保开始时间」不新增列：由既有 created_at（核保单创建即核保流程启动）在查询映射层承接，见
--   UnderwritingQueryResultMapper（View.createdAt → Result.underwritingStartTime）。
--changeset weisun:underwriting-008
ALTER TABLE t_underwriting_view
    ADD COLUMN underwriting_completed_time DATETIME NULL COMMENT '核保完成时间(来源决策事件 decidedAt；未出具结论时为空)' AFTER risk_score;

--rollback ALTER TABLE t_underwriting_view DROP COLUMN underwriting_completed_time;
