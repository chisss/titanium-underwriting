# Titanium 核保域 (titanium-underwriting) - 多 Agent 协作指南

> **版本**: V1.0
> **最后更新**: 2026-06-23
> **配套文档**: [CLAUDE.md](./CLAUDE.md)（模块开发规约）、根 [AGENTS.md](../AGENTS.md)（全局协作）

本文档面向在核保域并行作业的多个 AI Agent，约定边界、交互点、文件锁定与协作检查清单，避免冲突与破坏性变更。

---

## 一、模块定位与边界

核保域处于「投保 → **核保** → 出单」链路中段，理论上下游：

| 方向 | 对象 | 当前代码实况 |
|------|------|------------|
| 上游（被触发） | policy 域投保单提交 | ⚠️ **未实现**：无监听 policy 事件的消费者 |
| 下游（回传） | policy 域核保结论 | ⚠️ **未实现**：无回传 policy 的 Feign/事件发布 |
| 自身对外暴露 | `UnderwritingApi` Feign 接口 | 已定义，但被本服务 Controller 自调用（反模式） |
| 数据依赖 | customer 域（CustomerId）、clause 域（条款/险种） | 仅持有 ID 值对象，无实际 Feign 调用代码 |

> 🔴 **关键边界事实**：核保域目前是「**孤岛**」——既不监听 policy 域事件，也不向外发布跨域调用。所有 Kafka topic（`underwriting-created`、`underwriting-status-changed`）与事件投影都在域**内部**闭环。涉及跨域链路的任务，必须先确认是否需要新建消费者/Feign 客户端，而非假设已存在。

---

## 二、与其他域的交互点（基于真实代码）

### 2.1 对外接口（核保域提供）
- Feign 契约：`com.titanium.underwriting.api.UnderwritingApi`
  - `POST /underwriting/api/create` 创建核保
  - `GET /underwriting/api/{underwritingId}` 按 ID 查询
  - `GET /underwriting/api/policy/{policyId}` 按保单查询
  - `PUT /underwriting/api/{underwritingId}/underwrite` 执行核保
  - 注：`status/all` 接口在实现类中为桩方法
- 实现类：`UnderwritingApiController`（`@RequestMapping("/underwritings/web")`）

### 2.2 领域事件（Kafka）
- 发布：`UnderwritingCreatedEvent` → topic `underwriting-created`
- 发布：`UnderwritingStatusChangedEvent` → topic `underwriting-status-changed`
- 消费：仅域内 `UnderwritingProjectionEventHandler`（处理组 `underwriting-query-group`）订阅上述两事件投影读模型
- 信任包：`spring.json.trusted.packages = com.titanium.underwriting.event`

### 2.3 与 policy 域的协作链路（待建设）
若任务要求打通「投保单提交 → 核保」：
- 需在 `infrastructure.client` 包新建监听 policy 投保单提交事件的消费者（当前该包为空）
- 需在核保完成后新增向 policy 回传结论的 Feign 客户端或事件发布
- 需扩展 `spring.json.trusted.packages` 以信任 policy 域事件包

---

## 三、文件锁定建议（高频冲突区）

并行作业时，下列文件**同一时刻只允许一个 writer**：

| 锁定文件 | 冲突原因 |
|----------|---------|
| `domain/aggregate/Underwriting.java` | 聚合根，命令/事件/校验集中地，改动牵一发动全身 |
| `domain/command/*.java`（3 个） | 命令 record，签名变更连带聚合根+Controller+Mapper |
| `domain/event/*.java`（2 个） | 事件 record，连带投影器+EventSourcingHandler |
| `query/handler/UnderwritingConditionQueryHandler.java` | 9 个 QueryHandler 聚合于一类 |
| `query/handler/UnderwritingProjectionEventHandler.java` | 唯一读模型投影器，CQRS 读侧命脉 |
| `application/.../UnderwritingQueryAppService.java` | 11 查询统一编排入口 |
| `bootstrap/.../application.yml` | 端口/库/Kafka/Axon 全局配置 |
| 各 `*Mapper.java`（共 5 个） | MapStruct 字段映射，与实体同步修改 |

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
