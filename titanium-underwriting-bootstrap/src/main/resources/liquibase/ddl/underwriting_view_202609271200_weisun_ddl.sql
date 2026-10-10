--liquibase formatted sql

-- g02-04（AC-05）：核保读模型缺「投保单号」维度 —— 跨域幂等键无处落
--   成因：出单链路调核保时，`policyId` 字段里装的其实是**投保单号**（原 policy 侧
--   SyncUnderwritingDecisionAdapter 调 createUnderwriting 时 `createRequest.setPolicyId(request.insuranceId())`），
--   即本域长期把「投保单号」当「保单号」收下。后果有二：
--     ① 语义错位：核保记录上的 policyId 与 policy 域真保单号不是同一物，跨域对不上账；
--     ② 无幂等键：出单重试（超时重发、Saga 重放）时无从判断「这张投保单已经核保过了」，
--        只能再建一张核保单，同一投保单累积多条核保记录。
--   修法（用户裁示「加新字段不动旧字段」）：新增 `insurance_id` 列承载投保单号语义，与 `policy_id` **并存**，
--   不改动 `policy_id`（其既是 Kafka 分区键取值来源，也是有存量数据的旧列）。
--
--   🔴 为何不加 (insurance_id, tenant_id) 唯一索引：本域写侧是纯事件溯源聚合（EventSourcingRepository），
--      幂等判定发生在**聚合创建**（`CreateUnderwritingCommand` → 构造期处理器），读模型由投影**异步**维护。
--      唯一约束拦不住写侧创建，只会把「重复创建」恶化成「投影写入失败」——重复行至少还能查出来，
--      投影失败则读模型直接缺行，比重复更糟。故幂等由应用层「查读模型 → 复用/续跑」承担（见
--      AutoDecisionOrchestrator），本列只建**普通索引**供该查询使用。
--changeset weisun:underwriting-010
ALTER TABLE t_underwriting_view
    ADD COLUMN insurance_id VARCHAR(50) NULL COMMENT '投保单号(跨域幂等键；g02-04 新增，与 policy_id 并存，保全核保行为空)' AFTER policy_id;

ALTER TABLE t_underwriting_view
    ADD INDEX idx_uw_view_insurance_id (insurance_id, tenant_id);

--rollback ALTER TABLE t_underwriting_view DROP INDEX idx_uw_view_insurance_id;
--rollback ALTER TABLE t_underwriting_view DROP COLUMN insurance_id;
