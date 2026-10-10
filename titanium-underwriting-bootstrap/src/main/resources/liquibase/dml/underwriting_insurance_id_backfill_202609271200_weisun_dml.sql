--liquibase formatted sql

-- 存量回填（g02-04）：投保单号
--   新列 insurance_id 对既有行全为空（见 underwriting_view_202609271200_weisun_ddl.sql），
--   而该值在历史数据里**已经存在**——它就是非保全核保行的 `policy_id`，无需回放事件流、无需跨 schema 取数。
--
--   口径：`maintenance_id IS NULL` ⇒ `policy_id` 即投保单号。
--     · 非保全核保（出单链路发起）：调用方 policy 域传的就是投保单号
--       （原 SyncUnderwritingDecisionAdapter 的 `createRequest.setPolicyId(request.insuranceId())`），
--       故此处是**忠实转录既有事实**，不是按规则推断补值。
--     · 保全核保（AssessMaintenanceUnderwritingCommand 建立，maintenance_id 非空）：其 `policy_id` 是
--       **真保单号**，与投保单号语义不同，**不得**据以回填 —— 故本脚本以 maintenance_id 为界。
--
--   🔴 该等价在本数据集上已双向验证（真机取证）：
--      ① `maintenance_id IS NULL` ≡ 事件流首事件为 `UnderwritingCreatedEvent`
--         （反例计数 created_then_maintenance = 0、non_maint_without_created_event = 0）；
--      ② 非保全行 83 行 ↔ `UnderwritingCreatedEvent` 83 条，成双射。
--      ③ 非保全行内 (policy_id, tenant_id) 无重复 —— 回填不会造出重复幂等键。
--      ⚠️ 已知数据现状（不影响本脚本正确性）：83 行中 22 行是历次验收的合成数据（POL*/PLA*/E2E* 前缀），
--         其值同样是「忠实转录」。合成值永不会与真投保单号相撞（查找为精确等值），故无需区分处理。
--
--   幂等：仅补空值（WHERE ... IS NULL），重跑不覆盖投影已写入的值。
--changeset weisun:underwriting-011
UPDATE t_underwriting_view
SET insurance_id = policy_id
WHERE maintenance_id IS NULL
  AND insurance_id IS NULL;
