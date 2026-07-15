# Titanium 核保域 (titanium-underwriting) - 模块开发规约

> **版本**: V1.0
> **最后更新**: 2026-06-23
> **定位**: 保险核心系统 - 核保域微服务
> **上级规约**: 见根目录 [CLAUDE.md](../CLAUDE.md)，本文档仅补充本模块差异化内容，通用规约不重复

---

## 一、模块概述

核保域负责保险业务全生命周期中的**风险评估与核保决策**环节，在业务链路中的定位：

```
投保单提交(policy域) → 【核保域：创建核保 → 风险评估 → 自动/人工核保】 → 核保结论回传(policy域) → 出单
```

### 核心业务职责
- **创建核保**：依据保单/客户信息建立核保单（初始状态 `PENDING`）
- **自动核保**：依据金额/风险规则自动给出 `APPROVED` / `REVIEW` 结论
- **人工核保**：金额或风险超阈值时转 `MANUAL_REVIEW`，由核保员处理
- **核保查询**：面向核保员工作台的多维度查询（按状态、风险等级、核保员、客户历史、统计等）

> ⚠️ 当前代码中「核保结论回传 policy 域」「监听 policy 域投保单提交事件」两条跨域链路**尚未实现**，详见第七节已知缺陷。

---

## 二、技术栈与端口

| 项目 | 值 | 备注 |
|------|----|----|
| JDK | Amazon Corretto 21 | 路径 `/Users/sunwei/Library/Java/JavaVirtualMachines/corretto-21.0.4/Contents/Home` |
| Spring Boot | 4.0.1 | ⚠️ README.md 旧文写的 3.2.x 已过期，以根 pom 为准 |
| Axon Framework | 4.10.0 | CQRS + Event Sourcing |
| Kafka | 4.0.1 | 事件源 / 跨服务事件 |
| 数据库 | MySQL，库名 **`titanium_underwriting`** | 见 `application.yml` |
| 服务名 | `titanium-underwriting-service` | Feign / 注册中心标识 |
| **HTTP 端口** | **8083** | 🔴 **与 clause 域共用 8083，本地同时启动会端口冲突** |

> 🔴 **端口冲突注意**：clause 域同样配置为 8083。本地联调若需同时运行核保域与条款域，必须用 `--server.port=` 覆盖其中一个；部署到容器/不同主机则无影响。

---

## 三、子模块分层结构

基于 `pom.xml` 实际 8 个 module：

| 子模块 | 职责 | 关键类 |
|--------|------|--------|
| `-common` | 常量、自定义异常 | `UnderwritingConstants`、`UnderwritingException` |
| `-domain` | 领域核心：聚合根、命令、事件、值对象、仓储接口、领域服务 | `Underwriting`、3 命令、2 事件、4 值对象 |
| `-infrastructure` | 配置与出口：Axon/Kafka 配置、事件发布（写侧纯事件溯源，无 JPA 写表/`*Entity`/`Jpa*Repository`） | `AxonConfig`、`KafkaConfig`、`UnderwritingKafkaEventPublisher` |
| `-application` | 命令/查询编排（CommandGateway / QueryGateway） | `UnderwritingCommandService`、`UnderwritingQueryAppService` |
| `-api` | Feign 接口定义、DTO、Request | `UnderwritingApi`、`UnderwritingDTO` |
| `-web` | REST Controller、VO、租户拦截器 | `UnderwritingApiController`、`UnderwritingController` |
| `-query` | CQRS 读侧：QueryHandler、读模型投影、查询服务、缓存 | `UnderwritingConditionQueryHandler`、`UnderwritingProjectionEventHandler` |
| `-bootstrap` | 启动类、`application.yml` | `UnderwritingApplication` |

> 注：根 CLAUDE.md 规约把 `query` 包归在 domain 层，本模块实际把查询定义拆在 `domain/query`（2 个）与 `query` 子模块的 `query` 包（9 个）两处，存在分散，详见第七节。

---

## 四、核心领域模型

### 4.1 聚合根 Underwriting

`domain/aggregate/Underwriting.java`，**充血模型**，共 **3 个 `@CommandHandler` + 2 个 `@EventSourcingHandler`**，校验逻辑内聚于聚合根内部私有方法：

| 方法 | 类型 | 说明 |
|------|------|------|
| `Underwriting(CreateUnderwritingCommand)` | @CommandHandler(构造) | 校验后 apply `UnderwritingCreatedEvent` |
| `handle(UnderwriteCommand)` | @CommandHandler | 依金额阈值(>100000转REVIEW，否则APPROVED) apply 状态变更事件 |
| `handle(ManualReviewCommand)` | @CommandHandler | 转 `MANUAL_REVIEW` 状态 |
| `on(UnderwritingCreatedEvent)` | @EventSourcingHandler | 重建状态，初始 `PENDING` |
| `on(UnderwritingStatusChangedEvent)` | @EventSourcingHandler | 重建状态，按新状态记录拒保原因/审核意见 |

### 4.2 命令（3 个，record + `@TargetAggregateIdentifier`）

- `CreateUnderwritingCommand` — 创建核保
- `UnderwriteCommand` — 执行核保（自动决策）
- `ManualReviewCommand` — 人工审核

### 4.3 事件（2 个，record）

- `UnderwritingCreatedEvent` — 核保创建
- `UnderwritingStatusChangedEvent` — 核保状态变更（含 old/new 状态、原因）

对应 Kafka topic（`KafkaConfig`）：`underwriting-created`、`underwriting-status-changed`（均 partitions=3, replicas=2）。

### 4.4 查询（共 11 个 record）

**domain/query 包（2 个）**：
- `UnderwritingQuery` — ⚠️ **无对应 QueryHandler，疑似死查询**
- `FindUnderwritingByPolicyIdQuery` — ⚠️ 与 query 子模块同名类重复

**query 子模块 query 包（9 个，均有 Handler）**：
- `FindUnderwritingByIdQuery` — 按 ID
- `FindUnderwritingByPolicyIdQuery` — 按保单 ID
- `FindUnderwritingsByStatusQuery` — 按状态
- `FindUnderwritingsByRiskLevelQuery` — 按风险等级
- `FindUnderwritingsByUnderwriterQuery` — 按核保员+时间范围
- `FindUnderwritingsByMultipleConditionsQuery` — 多条件组合
- `FindUnderwritingHistoryByCustomerQuery` — 客户核保历史
- `FindUnderwritingStatisticsQuery` — 核保统计
- `FindPendingUnderwritingTasksQuery` — 待处理任务

> 🔴 **CQRS 读写严重失衡**：写侧仅 3 命令，读侧 11 查询 / 9 Handler。读模型表 `t_underwriting_query` 由 `UnderwritingProjectionEventHandler`（处理组 `underwriting-query-group`）投影填充。

> **持久化选型（写侧纯事件溯源）**：`Underwriting` 聚合为 Axon 事件溯源（`EventSourcingRepository` + `@EventSourcingHandler`），写侧状态只在事件流，**无 JPA 写表 / `UnderwritingEntity` / `UnderwritingJpaRepository`**（原为死码，已删除）。JPA 仅承载 CQRS 读模型（`query.view` / `query.repository`）。若后续新增**状态存储聚合**需保留的持久化对象，一律命名 `XxxxDO`（禁用 `Entity` 后缀），读模型投影保留 `*View`。选型细则见根 `docs/技术文档/持久化选型规范(JPA与EventSourcing).md`。

---

## 五、编码规约（本模块实例）

继承根 CLAUDE.md，以下为本模块需重点遵守/修正项：

- **命令/查询用 record**：已遵守，新增命令/查询同样用 record。
- **构造器注入优先**：🔴 现状不一致——`UnderwritingCommandService`、`UnderwritingConditionQueryHandler` 仍用 `@Autowired` 构造器注入，应改为 `@RequiredArgsConstructor` + `final`（参考 `UnderwritingController`、`UnderwritingApiController` 的正确写法）。
- **MapStruct 转换**：跨层转换走 Mapper，本模块 web 层 `UnderwritingWebMapper`（Request/DTO↔Command/VO）。写侧已纯事件溯源，原「聚合根↔`UnderwritingEntity`」的 infra Mapper（`UnderwritingEntityMapper` 等）已随写侧 JPA 一并删除。
- **充血模型**：业务校验内聚到 `Underwriting` 聚合根，**禁止**把核保规则散落到 Service。注意 `UnderwritingDomainService.determineUnderwritingStatus` 与聚合根内的同名决策逻辑并存，新增规则前先消除重复（详见第七节）。
- **面向接口/多态替代分支**：🔴 `UnderwritingDomainService` 用 `switch(riskLevel)` 决策，违背根规约「策略替代 switch 类型分支」，重构时应改策略模式。
- **中文注释 + SLF4J 占位符**：投影器 `UnderwritingProjectionEventHandler` 是范本（`log.info("...{}", ...)`），新增日志照此办理。
- **多租户**：所有命令/事件/查询/读模型均带 `tenantId`，REST 入口统一 `@RequestHeader("X-Tenant-ID")`，新增接口不得遗漏。

---

## 六、构建与运行

```bash
# 环境变量
export JAVA_HOME=/Users/sunwei/Library/Java/JavaVirtualMachines/corretto-21.0.4/Contents/Home

# 在仓库根目录构建（核保域依赖 metadata 等模块，建议整仓 install）
cd /Users/sunwei/titanium-project
mvn -pl titanium-underwriting -am clean install -DskipTests

# 单独启动核保域服务（端口 8083）
cd titanium-underwriting/titanium-underwriting-bootstrap
mvn spring-boot:run

# 若与 clause 域端口冲突，临时改端口启动
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=18083
```

依赖中间件：MySQL（库 `titanium_underwriting`）、Kafka（`localhost:9092`）。`ddl-auto: update` 会自动建表（见缺陷第 4 条）。

---

## 七、已知缺陷与注意事项

> 以下均基于当前真实代码，调整前务必复核：

1. 🔴 **跨域事件链路缺失**：`@EnableFeignClients(basePackages="...infrastructure.client")` 指向的 `client` 包为**空目录**；模块内**没有任何监听 policy 域投保单提交事件**的消费者（无外部事件 `@EventHandler`/`@KafkaListener`）。「投保单提交→自动触发核保」「核保结论回传 policy」目前未打通。
2. 🔴 **Feign 自调用反模式**：唯一 `@FeignClient` 是 `UnderwritingApi`（name=`titanium-underwriting-service`，指向自己），且不在被扫描的 `client` 包内；`UnderwritingController` 注入 `UnderwritingApi` 调用本服务，属绕一圈自调用，应直接调 Application 层。
3. 🔴 **端口冲突**：8083 与 clause 域重复，见第二节。
4. 🔴 **未用 Liquibase**：`application.yml` 用 `ddl-auto: update` 自动建表，违背根规约「SQL 用 Liquibase 维护」；且 `hibernate.dialect` 配成 `MySQL5InnoDBDialect`（偏旧）。
5. ⚠️ **CQRS 读写失衡**：3 命令 vs 11 查询，读模型投影逻辑集中在单个 `UnderwritingProjectionEventHandler`，扩展状态时勿遗漏投影分支。
6. ⚠️ **死查询/重复查询**：`domain/query/UnderwritingQuery` 无 Handler；`FindUnderwritingByPolicyIdQuery` 在 domain 与 query 两处重名定义，易混淆。
7. ⚠️ **Controller 桩方法**：`UnderwritingController` 与 `UnderwritingApiController` 中按保单/状态/全量查询的方法为 TODO 桩（返回 `null`/`notFound`/空列表），未真正实现。
8. ⚠️ **重复决策逻辑**：聚合根 `determineUnderwritingStatus`（金额阈值）与 `UnderwritingDomainService.determineUnderwritingStatus`（风险等级 switch）两套并存且口径不一致，需统一。
9. ⚠️ **TenantContext 重复**：`web` 与 `infrastructure` 各有一份 `TenantContext`，租户上下文实现分散。
10. ⚠️ **README.md 过期**：旧 README 写 Spring Boot 3.2 / Axon 4.1 / PostgreSQL，与实际 SB4.0.1 / Axon4.10 / MySQL 不符，调整时一并更新。

---

**维护提示**：每次改动聚合根/命令/事件后，请同步检查投影器、QueryHandler、Mapper 与测试类，并参考 [AGENTS.md](./AGENTS.md) 的协作检查清单。
