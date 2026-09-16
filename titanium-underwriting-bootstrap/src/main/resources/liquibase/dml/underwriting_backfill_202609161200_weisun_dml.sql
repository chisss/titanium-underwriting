--liquibase formatted sql

-- 存量回填（D-501-44）：核保完成时间
--   新列 underwriting_completed_time 对既有行全为空（见 underwriting_view_202609161200_weisun_ddl.sql），
--   而决策时间在事件流里 100% 有值 —— 直接从事件存储回填，避免"改造上线后老单子仍然显示 -"。
--
-- 🔴 载荷取值前必须 CONVERT(... USING utf8mb4)：axon_domain_event_entry.payload 是二进制列，
--    直接对 BLOB 调 JSON_EXTRACT 报 ERROR 3144（Cannot create a JSON value from a string with CHARACTER SET 'binary'）。
-- 🔴 LocalDateTime 在本域 Jackson 序列化下是"时间数组"（[2026,8,13,23,28,13,950891543]）而非 ISO 字符串，
--    故按 $[0]..$[5] 取年月日时分秒拼装；第 6 位纳秒不入库（列精度为秒）。
-- 🔴 一个核保单可能有多次决策（拒保后重投、保全加保重新核保），按 global_index 取**最后一次**。
--    幂等：仅补空值（WHERE ... IS NULL），重跑不覆盖投影已写入的值。
--changeset weisun:underwriting-009
UPDATE t_underwriting_view v
    JOIN (SELECT aggregate_identifier                                                                          AS uw_id,
                 STR_TO_DATE(CONCAT_WS(' ',
                                       CONCAT_WS('-', j ->> '$[0]', j ->> '$[1]', j ->> '$[2]'),
                                       CONCAT_WS(':', j ->> '$[3]', j ->> '$[4]', j ->> '$[5]')),
                             '%Y-%m-%d %H:%i:%s')                                                              AS decided_at,
                 ROW_NUMBER() OVER (PARTITION BY aggregate_identifier ORDER BY global_index DESC)              AS rn
          FROM (SELECT aggregate_identifier,
                       global_index,
                       JSON_EXTRACT(CONVERT(payload USING utf8mb4), '$.decidedAt') AS j
                FROM axon_domain_event_entry
                WHERE payload_type LIKE '%UnderwritingDecidedEvent%') s
          WHERE j IS NOT NULL) e
    ON e.uw_id = v.underwriting_id AND e.rn = 1
SET v.underwriting_completed_time = e.decided_at
WHERE v.underwriting_completed_time IS NULL;
