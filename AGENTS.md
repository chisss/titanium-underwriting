# Titanium 核保域 (titanium-underwriting) - 多 Agent 协作指南

> **版本**: V1.1
> **最后更新**: 2026-09-14（m10-1302 纠偏：入站链路定性由「缺失」正名为「同步 Feign 设计定位」；连带复核并修正全文件的类名/路径/数量——Provider 取代旧 Controller、命令 6/事件 5/查询方法 10）
> **配套文档**: [CLAUDE.md](./CLAUDE.md)（模块开发规约）、根 [AGENTS.md](../AGENTS.md)（全局协作）

本文档面向在核保域并行作业的多个 AI Agent，约定边界、交互点、文件锁定与协作检查清单，避免冲突与破坏性变更。

---

## 一、模块定位与边界

核保域处于「投保 → **核保** → 出单」链路中段，理论上下游：

| 方向 | 对象 | 当前代码实况 |
|------|------|------------|
| 上游（被触发） | policy 域投保单提交 | ✅ **已通（同步 Feign）**：policy 域 `IssuanceSaga` 收到 `InsuranceSubmittedForUnderwritingEvent` 后经 `UnderwritingDecisionGateway` 主动调用本域 |
| 下游（回传） | policy 域核保结论 | ✅ **已通（Kafka）**：`underwriting-decided` 经 `UnderwritingKafkaEventPublisher` 外发，policy 域 `UnderwritingDecidedEventListener` 消费 |
| 自身对外暴露 | `UnderwritingApi` Feign 接口 | ✅ 由 `web/provider/UnderwritingApiProvider` 实现（根规约 §3.4.10；`UnderwritingApiController` 旧实现类已删除） |
| 数据依赖 | product / ruleengine / featurecenter 域 | ✅ 各有 Adapter 实调对端 Feign（`infrastructure/adapter/{product,ruleengine,featurecenter}`）；仅持有 customer/clause 的 ID 值对象，无 Feign 调用 |

> 🔴 **关键边界事实（m10-1302 纠偏，2026-09-14）**：核保域的跨域入口**不是孤岛**——入站「投保单提交 → 核保」走 **policy 域主动发起的同步 Feign**，本域是 **Feign 服务端**，故全域（含测试）`@KafkaListener` 计数为 0 **是设计定位而非缺陷**。跨域出口走 Kafka：`underwriting-decided` 经 `UnderwritingKafkaEventPublisher` 外发，由 policy 域 `UnderwritingDecidedEventListener` 消费。
>
> 🔴 **取证判据**：判定「某跨域链路缺失」**必须从调用方侧核验**（谁发起、经何协议、对端扮演何角色），仅扫本域「有无 `@KafkaListener`」会把「同步 Feign 服务端」误判成「链路未通」——旧文（V1.0）即犯此错，已在 CLAUDE.md 第七节记录纠偏证据。

---

## 二、与其他域的交互点（基于真实代码）

### 2.1 对外接口（核保域提供）
- Feign 契约：`com.titanium.underwriting.api.UnderwritingApi`（`@FeignClient(name = "titanium-underwriting-service", path = "/underwriting/api")`，8 方法）
  - `POST /create` 创建核保 ｜ `GET /{underwritingId}` 按 ID 查 ｜ `GET /policy/{policyId}` 按保单查
  - `PUT /{underwritingId}/underwrite` 执行核保 ｜ `PUT /{underwritingId}/inputs` 提交结构化输入 ｜ `PUT /{underwritingId}/decide` 触发决策
  - `GET /status/{status}` 按状态分页查 ｜ `GET /all` 全量分页查
- 实现：`web/provider/UnderwritingApiProvider`（8 方法全部实调应用层门面，**已无桩方法**；另 `MaintenanceUnderwritingApiProvider` 实现保全核保契约）
- 🔴 本域是**被调用方**：policy 域 `SyncUnderwritingDecisionAdapter` 即通过上述契约（`create` → `inputs` → `decide`）完成一次核保。

### 2.2 领域事件（Kafka）
- 发布（**本域唯一跨域出口**）：`UnderwritingDecidedEvent` → topic `underwriting-decided`（`UnderwritingKafkaEventPublisher`，分区键 `policyId`）
- 消费：**本域无 Kafka 消费方（设计定位）**——入站走同步 Feign（见 §2.1），不消费 policy 域事件；`@KafkaListener` 全域计数为 0
- 域内投影：`UnderwritingProjectionEventHandler`（`query/handler/projection/`，处理组 `underwriting-query-group`）订阅域内事件填充读模型（5 个 `@EventHandler`）
- 信任包：`spring.json.trusted.packages = com.titanium.underwriting.event`（**出站发布器自用**，非入站消费）
- 🔴 `underwriting-created` / `underwriting-status-changed` / `underwriting-events` 三个常量与对应两个 `NewTopic` Bean 已于 m5-903 删除（声明起从无发布点，属死主题）。

### 2.3 与 policy 域的协作链路（已通，勿再按「待建设」施工）

| 方向 | 协议 | 发起方 | 本域角色 |
|------|------|--------|---------|
| 投保单提交 → 核保（入站） | 同步 Feign | policy 域 `IssuanceSaga` | **服务端**（`UnderwritingApiProvider`） |
| 核保结论 → 投保单（出站） | Kafka `underwriting-decided` | 本域 `UnderwritingKafkaEventPublisher` | 发布方 |

- 🔴 **不要再新建「监听 policy 投保单提交事件的消费者」**——该链路设计上就不走消息，本域 `infrastructure/client` 为空的成因是「对下游 Feign 直接扫码对端 `-api`」（`UnderwritingApplication.java:22-23`），不是「待补的消费者」。
- 若确需新增跨域交互，先按上述判据核验现有链路，再决定协议（Feign 同步 vs Kafka 异步）。

---

## 三、文件锁定建议（高频冲突区）

并行作业时，下列文件**同一时刻只允许一个 writer**：

| 锁定文件 | 冲突原因 |
|----------|---------|
| `domain/aggregate/Underwriting.java` | 聚合根，命令/事件/校验集中地，改动牵一发动全身 |
| `domain/command/*.java`（6 个） | 命令 record，签名变更连带聚合根+Provider+Mapper |
| `domain/event/*.java`（5 个） | 事件 record，连带投影器+EventSourcingHandler+Kafka 发布器 |
| `query/handler/query/UnderwritingQueryHandler.java` | 9 个 `@QueryHandler` 聚合于一类 |
| `query/handler/projection/UnderwritingProjectionEventHandler.java` | 唯一读模型投影器（5 个 `@EventHandler`），CQRS 读侧命脉 |
| `application/query/UnderwritingQueryAppService.java` | 10 个查询方法统一编排入口 |
| `bootstrap/.../application.yml` | 端口/库/Kafka/Axon 全局配置 |
| 各 `*Mapper.java`（共 5 个） | MapStruct 字段映射（web 3 + query 2），与实体同步修改 |

---

## 四、Agent 任务分工建议

| 角色 | 负责范围 | 不可触碰 |
|------|---------|---------|
| **Domain Agent** | 聚合根、命令、事件、值对象、领域服务 | query/web/infra 实现 |
| **Read-Model Agent** | query 子模块：QueryHandler、投影器、查询服务、缓存 | 写侧命令/聚合根 |
| **Infra Agent** | JPA 实体、仓储实现、Axon/Kafka 配置、跨域客户端 | 领域模型定义 |
| **Web/API Agent** | Controller、VO、Request/DTO、Feign 契约 | 领域与读模型内部逻辑 |

> 跨角色改动（如新增一个命令同时影响聚合根+投影+查询）须由 **Lead Agent** 串行编排，禁止多 Agent 同时写聚合根与其下游。

---

## 五、协作检查清单（改聚合根的连锁同步）

修改聚合根 / 命令 / 事件时，按下表逐项核对：

- [ ] **命令变更** → 同步 `Underwriting` 对应 `@CommandHandler` 校验、`UnderwritingApiController` 构建命令处、`UnderwritingMapper`
- [ ] **事件变更** → 同步聚合根 `@EventSourcingHandler` + `UnderwritingProjectionEventHandler` 投影 + 读模型实体 `UnderwritingQueryEntity` + Kafka topic（如新增事件类型）
- [ ] **新增状态** → 检查投影器状态分支（拒保原因 / 审核意见写入逻辑）是否覆盖
- [ ] **新增查询** → query 包定义 record + `UnderwritingConditionQueryHandler` 加 Handler + `UnderwritingQueryAppService` 加编排 + `UnderwritingQueryMapper` 转换；避免重蹈「`UnderwritingQuery` 无 Handler」覆辙
- [ ] **字段增减** → 五个 Mapper（app/infra×2/query/web）逐一核对
- [ ] **多租户** → 新命令/事件/查询/读模型字段含 `tenantId`，REST 入口含 `X-Tenant-ID`
- [ ] **测试同步** → 更新 `UnderwritingQueryServiceImplTest`、`UnderwritingControllerTest`、`UnderwritingWebMapperTest`，对 domain/application/infrastructure 新增逻辑补单元测试
- [ ] **跨域影响** → 若涉及 policy/customer/clause，确认 `infrastructure.client` 消费者/客户端与 `trusted.packages` 配置
- [ ] **端口** → 本地联调注意与 clause 域 8083 冲突

---

## 六、破坏性操作红线

- 修改 `Underwriting` 聚合根的 `@CommandHandler`/`@EventSourcingHandler` 方法签名前，须确认事件存储兼容（Event Sourcing 重放）
- 调整 Kafka topic 名 / partitions / replicas 前，须确认下游消费者与已有事件数据
- 删除或重命名查询 record 前，须全仓 grep 确认无 Feign/前端引用
- 任何涉及 `application.yml` 端口、数据库、Kafka 地址的改动，须在 PR 说明中显式标注
