# Titanium 核保域 (titanium-underwriting) - 模块开发规约

> **版本**: V1.3
> **最后更新**: 2026-09-14（m11-1404 产品核保配置来源显式化：新增 `ProductConfigSource` 三态标记并随命令/事件全链传递，见第七节；同时保留 m10-1302 入站链路定性纠偏——判据=定性入站链路必须从调用方侧核验）
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

> **跨域链路现状（实读核对，2026-09-14；入站定性已于 m10-1302 纠偏）**：
> - ✅ **出站「核保结论回传 policy 域」走 Kafka**：`UnderwritingKafkaEventPublisher.java:51,57-58,70`（处理组 `underwriting-kafka-group`，订阅 `UnderwritingDecidedEvent` 后按 `policyId` 分区键发 `underwriting-decided`），policy 侧由 `titanium-policy-infrastructure/.../messaging/UnderwritingDecidedEventListener.java:35,47` 的 `@KafkaListener` 消费并按投保单回写聚合。
> - ✅ **入站「投保单提交 → 触发核保」走同步 Feign（由 policy 主动调用，本域是服务端）**：policy 域 `IssuanceSaga` 在 `InsuranceSubmittedForUnderwritingEvent` 到达时发起——`IssuanceSaga.java:79`（`@Saga`）、`:91`（`transient UnderwritingDecisionGateway`）、`:223-224`（`@SagaEventHandler(associationProperty = "insuranceId")` + `on(InsuranceSubmittedForUnderwritingEvent)`）、`:235`（`underwritingDecisionGateway.requestDecision(request)`）、`:237`（`commandGateway.sendAndWait(new ReceiveUnderwritingResultCommand(...))`）；下行由 policy 侧 `SyncUnderwritingDecisionAdapter.java:42,44,49,52`（`@Component implements UnderwritingDecisionGateway`，注入 `UnderwritingApi`）经本域 Feign 契约完成「创建核保 → 提交结构化输入 → 触发决策 → 回传结论」四步，服务端实现为 `web/provider/UnderwritingApiProvider`。
> - 🔴 **故「本域零 `@KafkaListener`」是设计定位，不是缺陷**：本域对 policy 的角色是 **Feign 服务端**（入站即 HTTP 请求），不存在需要消费的 policy 事件。m9-1204 曾据此记为「入站跨域链路缺失」，属**只看本域取证、未从调用方侧核验**导致的定性错误，已于 m10-1302 纠正（详见第七节）。
> - 📌 **两处空目录亦非缺陷**：`infrastructure/client` 空——本域对下游（product/ruleengine/featurecenter）的 Feign 客户端直接扫码对端 `-api` 包（`UnderwritingApplication.java:22-23` 的 `@EnableFeignClients(basePackages = {...})`），Adapter 注入的正是对端 `ProductApi`/`RuleEngineApi`/`FeatureCenterApi`；`infrastructure/projection` 空——投影属 CQRS 读侧，按根规约 §3.4.9 落在 `query/handler/projection`（`UnderwritingProjectionEventHandler`，5 个 `@EventHandler`）。

---

## 二、技术栈与端口

| 项目 | 值 | 备注 |
|------|----|----|
| JDK | Amazon Corretto 21 | 路径 `/Users/sunwei/Library/Java/JavaVirtualMachines/corretto-21.0.4/Contents/Home` |
| Spring Boot | 4.0.1 | 与根 pom 一致；模块 README 已统一为 SB 4.0.1 / Axon 4.10 / MySQL 8（`README.md:8-9,61-63`，`1d3637d`） |
| Axon Framework | 4.10.0 | CQRS + Event Sourcing |
| Kafka | 4.0.1 | 事件源 / 跨服务事件 |
| 数据库 | MySQL，库名 **`titanium_underwriting`** | 见 `application.yml` |
| 服务名 | `titanium-underwriting-service` | Feign / 注册中心标识 |
| **HTTP 端口** | **8083** | 🔴 **与 clause 域共用 8083，本地同时启动会端口冲突** |

> 🔴 **端口冲突注意**：clause 域同样配置为 8083。本地联调若需同时运行核保域与条款域，必须用 `--server.port=` 覆盖其中一个；部署到容器/不同主机则无影响。

---

## 三、子模块分层结构

基于 `pom.xml` 实际 8 个 module：

| 子模块 | 职责 | 关键类（实读清点） |
|--------|------|--------|
| `-common` | 常量、本域枚举、自定义异常 | `UnderwritingConstants`、`UnderwritingException`、`enums/{MaintenanceRiskClassification,VehicleUsageType}` |
| `-domain` | 领域核心：聚合根、命令、事件、值对象、出口 Port、生成器、纯领域服务 | `Underwriting`；**6 命令 / 5 事件 / 15 值对象**；`port/{product,ruleengine,featurecenter}` 三 Port；`generator/UnderwritingNoGenerator`；`service/`：`RuleConclusionMappingService`、`MaintenanceUnderwritingCommandValidator` |
| `-infrastructure` | 配置与出口适配：Axon/Kafka 配置、跨域出站发布、三个外部服务 Adapter、生成器实现（写侧纯事件溯源，无 JPA 写表/`*Entity`/`Jpa*Repository`——已实读确认为零残留） | `config/{AxonConfig,KafkaConfig,TenantContext}`、`event/UnderwritingKafkaEventPublisher`、`adapter/{product,ruleengine,featurecenter}/*Adapter`、`generator/UnderwritingNoGeneratorImpl` |
| `-application` | 命令/查询入口门面 + 决策编排 | `service/UnderwritingCommandService`、`query/UnderwritingQueryAppService`、`orchestration/UnderwritingDecisionOrchestrator` |
| `-api` | Feign 契约（入参 `Request`/出参 `Response`，**已无 DTO**，按业务主题拆子包） | `UnderwritingApi`、`MaintenanceUnderwritingApi`、`UnderwritingBaseApi`、`request/{underwriting,maintenance}/`、`response/{underwriting,maintenance}/` |
| `-web` | REST Controller、DTO/VO、契约 Provider、Mapper、Assembler、租户拦截器 | `controller/UnderwritingController`、`provider/{UnderwritingApiProvider,MaintenanceUnderwritingApiProvider}`、`mapper/`×3、`assembler/UnderwritingWebAssembler`、`interceptor/TenantInterceptor` |
| `-query` | CQRS 读侧：QueryHandler、读模型投影、查询服务、缓存、DLQ 重投 | `handler/query/UnderwritingQueryHandler`（9 个 `@QueryHandler`）、`handler/projection/UnderwritingProjectionEventHandler`、`service/UnderwritingQueryService(Impl)`、`view/UnderwritingView`、`scheduled/DeadLetterQueueService` |
| `-bootstrap` | 启动类、`application.yml`、Liquibase 脚本 | `UnderwritingApplication` |

> 🔴 **读侧投影表名是 `t_underwriting_view`**（`UnderwritingView.java:32` + `liquibase/ddl/underwriting_view_202607091700_weisun_ddl.sql:5`），旧文写的 `t_underwriting_query` 系笔误，已纠正；处理组 `underwriting-query-group`（`UnderwritingQueryHandler.java:34`、`UnderwritingProjectionEventHandler.java:37` 两处声明一致）。

> 注：**查询定义已全部收敛到 `query` 子模块的 `query` 包（9 个 `Find*Query`），`domain` 层不再有 `query` 子包**——旧文所述「拆在 `domain/query` 与 query 子模块两处、存在分散」的旧结构已随 `65634e6`（2026-07-09）包结构重构消除。

---

## 四、核心领域模型

### 4.1 聚合根 Underwriting

`domain/aggregate/Underwriting.java`，**充血模型**，共 **6 个 `@CommandHandler` + 5 个 `@EventSourcingHandler`**，校验逻辑内聚于聚合根内部私有方法（`Underwriting.java:115,126,153,190,214,320` / `:336,353,367,374,395`）：

| 方法 | 类型 | 说明 |
|------|------|------|
| `Underwriting(CreateUnderwritingCommand)` | @CommandHandler(构造) | 校验后 `apply` `UnderwritingCreatedEvent`（唯一用 `apply` 的处理器） |
| `handle(AssessMaintenanceUnderwritingCommand)` | @CommandHandler | 保全核保路径（带 `maintenanceId` + `policyId`），`@CreationPolicy(CREATE_IF_MISSING)` + 幂等键/payload 哈希预检，**返回** `MaintenanceUnderwritingAssessedEvent` |
| `handle(UnderwriteCommand)` | @CommandHandler | 决策顺序：**先**按险种专属输入评估的风险等级判定（`underwritingInput.assessRiskLevel()` → `mapRiskLevelToStatus`），**无输入时回退**金额阈值（>100000 转 `REVIEW`，否则 `APPROVED`）；返回 `UnderwritingStatusChangedEvent` |
| `handle(SubmitUnderwritingInputCommand)` | @CommandHandler | 提交险种专属核保输入，返回 `UnderwritingInputSubmittedEvent` |
| `handle(DecideUnderwritingCommand)` | @CommandHandler | 出具核保结论，返回 `UnderwritingDecidedEvent` |
| `handle(ManualReviewCommand)` | @CommandHandler | 转 `MANUAL_REVIEW` 状态（唯一返回 `void` 的处理器） |
| `on(...)` × 5 | @EventSourcingHandler | 分别重建 `Created`/`StatusChanged`/`InputSubmitted`/`Decided`/`MaintenanceAssessed` 状态 |

> 🔴 **同步返回事件 + 终态保护（实读补充）**：除构造器外，各处理器一律「`AggregateLifecycle.apply(event)` 后 `return event`」，便于调用方直接拿到结论（见 `UnderwritingSynchronousCommandTest`）；写入型处理器（`UnderwriteCommand`/`SubmitUnderwritingInputCommand`）前置 `requireNotTerminal(...)`，**已出具结论的核保单不得再被改写**（`4a26fa9`，回归见 `UnderwritingTerminalStateGuardTest`）。

### 4.2 命令（6 个，record + `@TargetAggregateIdentifier`）

- `CreateUnderwritingCommand` — 创建核保
- `AssessMaintenanceUnderwritingCommand` — 保全核保评估
- `UnderwriteCommand` — 执行核保（自动决策）
- `SubmitUnderwritingInputCommand` — 提交险种专属核保输入
- `DecideUnderwritingCommand` — 出具核保结论
- `ManualReviewCommand` — 人工审核

### 4.3 事件（5 个，record）

- `UnderwritingCreatedEvent` — 核保创建
- `UnderwritingStatusChangedEvent` — 核保状态变更（含 old/new 状态、原因）
- `UnderwritingInputSubmittedEvent` — 核保输入提交
- `UnderwritingDecidedEvent` — 核保决策完成（含结论/风险等级/加费明细/`policyId`，**跨域异步回流 policy 的载荷**；`reason`（dev-505）与 `configSource`（m11-1404）均为尾部追加字段，旧事件 JSON 缺字段时 Jackson 取 null，向后兼容）
- `MaintenanceUnderwritingAssessedEvent` — 保全核保评估完成

对应 Kafka topic（`KafkaConfig`，partitions=3, replicas=2）：**仅 `underwriting-decided` 一个**（本域唯一跨域出口，见 `UnderwritingKafkaEventPublisher`）。原 `underwriting-created`/`underwriting-status-changed` 两个主题与常量已于 m5-903 删除（声明起从无发布点，属死主题）；`UnderwritingCreatedEvent`/`UnderwritingStatusChangedEvent` 只在本域事件流与投影内使用，不外发。

🔴 **`underwriting-decided` 的分区键固定为 `policyId`（m0-713 起）**：消费端 policy 域按**投保单**维度回写聚合，而同一投保单会产生**多次**核保决策（拒保后重投、保全加保的重新核保），只有分区键一致，Kafka 的「同分区内保序」才能兑现为「同投保单内保序」。**不得改回 `underwritingId`，更不得为 null**（null key 轮询分区）；`policyId` 缺失时退化按 `underwritingId` 分区并 `log.warn` 暴露数据异常。该 topic 的 `NewTopic` 显式声明 3 分区是保序前提，不可删除。回归用例：`UnderwritingKafkaEventPublisherTest`。

### 4.4 查询（共 9 个 record，全部在 query 子模块 `query` 包）

- `FindUnderwritingByIdQuery` — 按 ID
- `FindUnderwritingByPolicyIdQuery` — 按保单 ID
- `FindUnderwritingsByStatusQuery` — 按状态
- `FindUnderwritingsByRiskLevelQuery` — 按风险等级
- `FindUnderwritingsByUnderwriterQuery` — 按核保员+时间范围
- `FindUnderwritingsByMultipleConditionsQuery` — 多条件组合
- `FindUnderwritingHistoryByCustomerQuery` — 客户核保历史
- `FindUnderwritingStatisticsQuery` — 核保统计
- `FindPendingUnderwritingTasksQuery` — 待处理任务

> 🔴 **CQRS 读写失衡**：写侧 6 命令 vs 读侧 9 查询，全部 9 个 `@QueryHandler` 集中在一个类 `UnderwritingQueryHandler`（`query/handler/query/`，处理组 `underwriting-query-group`），扩展状态时勿遗漏投影分支。读模型表 **`t_underwriting_view`** 由 `UnderwritingProjectionEventHandler`（同处理组）投影填充。

> 旧文所载「`domain/query` 包 2 个查询（`UnderwritingQuery` 死查询、`FindUnderwritingByPolicyIdQuery` 重名）」**已不存在**：该包连同查询定义已在 `65634e6`（2026-07-09）删除，查询全部收敛到 `query` 子模块。

> **持久化选型（写侧纯事件溯源）**：`Underwriting` 聚合为 Axon 事件溯源（`EventSourcingRepository` + `@EventSourcingHandler`），写侧状态只在事件流，**无 JPA 写表 / `UnderwritingEntity` / `UnderwritingJpaRepository`**（原为死码，已删除）。JPA 仅承载 CQRS 读模型（`query.view` / `query.repository`）。若后续新增**状态存储聚合**需保留的持久化对象，一律命名 `XxxxDO`（禁用 `Entity` 后缀），读模型投影保留 `*View`。选型细则见根 `docs/技术文档/持久化选型规范(JPA与EventSourcing).md`。

---

## 五、编码规约（本模块实例）

继承根 CLAUDE.md，以下为本模块需重点遵守/修正项：

- **命令/查询用 record**：已遵守，新增命令/查询同样用 record（6 命令 / 5 事件 / 9 查询均为 record）。
- **构造器注入优先**：✅ 已达标——全模块 `src/main` 零 `@Autowired`；`UnderwritingCommandService.java:35-40` 为 `@RequiredArgsConstructor` + `final` 字段，controller/provider 同型。（旧文所记「仍用 `@Autowired`」的状态已随模块重构消除。）
- **MapStruct 转换**：跨层转换走 Mapper，本模块 web 层三个 `*WebMapper` + query 层两个 `*Mapper`。写侧已纯事件溯源，原「聚合根↔`UnderwritingEntity`」的 infra Mapper 已随写侧 JPA 一并删除（实读确认 infra 层零 `*Entity`/`*Mapper`/`*JpaRepository` 残留）。
- **充血模型**：业务校验内聚到 `Underwriting` 聚合根，**禁止**把核保规则散落到 Service。⚠️ 旧文点名的 `UnderwritingDomainService` **已删除**（全仓 Java 零引用；删除提交 `65634e6`），其决策职责现由聚合根承担——金额阈值/风险等级判定见 `Underwriting.java:60,170-180`，风险等级→结论→状态的两级映射见 `Underwriting.java:286,301`。现行 `domain/service` 只剩两类**纯领域**逻辑：`RuleConclusionMappingService`（规则引擎结论→领域决策映射，`impl/RuleConclusionMappingServiceImpl.java:44`）与 `MaintenanceUnderwritingCommandValidator`（保全核保命令校验），均无 Port / 无 CommandGateway，符合根规约 §3.4.4。
- **面向接口/多态替代分支**：✅ 现存 `switch` 均为**枚举穷尽映射**（`Underwriting.java:286,301,436`、`RuleConclusionMappingServiceImpl.java:44`），属 Java 惯用且与 m7-1002「穷尽 switch 强制表态」口径一致，**非**「按类型分支」违例；新增的**类型**分派仍须用多态/策略。旧文所指 `UnderwritingDomainService` 的 `switch(riskLevel)` 违例随该类删除而消解。
- **中文注释 + SLF4J 占位符**：投影器 `UnderwritingProjectionEventHandler` 是范本（`log.info("...{}", ...)`），新增日志照此办理。
- **多租户**：所有命令/事件/查询/读模型均带 `tenantId`，REST 入口统一 `@RequestHeader("X-Tenant-ID")`，新增接口不得遗漏。

---

## 六、构建与运行

```bash
# 环境变量
export JAVA_HOME=/Users/sunwei/Library/Java/JavaVirtualMachines/corretto-21.0.4/Contents/Home

# 构建核保域（域目录即 Maven reactor；依赖 metadata 等须已在本地仓库）
cd /Users/sunwei/titanium-project/titanium-underwriting
mvn clean install -DskipTests

# 单独启动核保域服务（端口 8083）
cd titanium-underwriting-bootstrap
mvn spring-boot:run

# 若与 clause 域端口冲突，临时改端口启动
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=18083
```

依赖中间件：MySQL（库 `titanium_underwriting`）、Kafka（`localhost:9092`）。`spring.jpa.hibernate.ddl-auto: none`，建表一律经 Liquibase（`bootstrap/src/main/resources/liquibase/changelog-master.xml`）——**旧文所记 `ddl-auto: update` 自动建表已不成立**，见 `application.yml` 的 `jpa`/`liquibase` 两段。

---

## 七、已知缺陷与注意事项

> 以下均基于当前真实代码逐条实读核对（2026-09-14，每条附『文件:行号』证据）；调整前务必复核。

1. ✅ **原「入站跨域链路缺失」已纠正为设计定位（m10-1302，2026-09-14；**勿再按旧文记为在逃缺陷**）**：旧文据「本域零 `@KafkaListener`、`infrastructure/client` 为空目录、`infrastructure/projection` 为空」判定「投保单提交 → 自动触发核保」未打通，属**定性错误**——该链路走 **policy 域主动发起的同步 Feign**，本域是 Feign 服务端（`UnderwritingApi` 由 `web/provider/UnderwritingApiProvider` 实现），本就无需 Kafka 消费方。三处「空缺」逐一正名：① `infrastructure/client` 为空**非缺陷**——本域对下游的 Feign 客户端直接扫码对端 `-api` 包（`UnderwritingApplication.java:22-23`）；② 零 `@KafkaListener` 说明**不消费消息**，不等于入站链路未通；③ `infrastructure/projection` 为空**符合规范**——投影按根规约 §3.4.9 归 `query/handler/projection`（`UnderwritingProjectionEventHandler`，5 个 `@EventHandler`）。完整证据链见 §一「跨域链路现状」。
   > 🔴 **取证方法教训**：判定「某跨域链路缺失」**必须从调用方侧核验**（谁发起、经何协议、对端扮演何角色），仅扫本域「有无 `@KafkaListener`」会把「同步 Feign 服务端」误判成「链路未通」。
   > **出站回流已通（勿再记为缺失）**：`UnderwritingKafkaEventPublisher.java:51,57-58,70`（`@ProcessingGroup("underwriting-kafka-group")`，`tracking` + DLQ，见 `application.yml`）订阅 `UnderwritingDecidedEvent` 外发 `underwriting-decided`，policy 域 `UnderwritingDecidedEventListener.java:35,47` 已订阅并按 `policyId` 回写投保单聚合。分区键与保序约束见 §4.3（m0-713）。
2. 🔴 **端口冲突**：`server.port: 8083`（`bootstrap/.../application.yml`），与 clause 域相同（`titanium-clause-bootstrap/.../application.yml:2`），本地同时启动会冲突，见第二节。
3. ⚠️ **CQRS 读写失衡**：**6 命令 vs 9 查询**，读模型投影逻辑集中在单个 `UnderwritingProjectionEventHandler`，扩展状态时勿遗漏投影分支。
4. ⚠️ **TenantContext 重复**：`web/config/TenantContext.java` 与 `infrastructure/config/TenantContext.java` 各有一份，租户上下文实现分散。

> 另记（非缺陷，供后续改造参考）：`determineUnderwritingStatus` 的金额阈值分支带 `TODO 规则引擎接入`（`Underwriting.java:177-178`）——阈值应改由 `titanium-rule-engine` 按险种/租户配置，当前为硬编码回退规则。

> 已修复缺口（m0-713，2026-09-11）：
> - ✅ **`underwriting-decided` 分区有序性**：发布器原以 `underwritingId` 作分区键、为空时发 null key。但消费端 policy 域按**投保单**维度回写，同一投保单会有多次决策（拒保后重投、保全加保重新核保），用核保单作键会把它们散到不同分区，Kafka 的「同分区内保序」落空，后到的旧结论可能覆盖新结论。
>   修复：分区键改为 `policyId`（缺失时退化 `underwritingId` 并 `log.warn`）；`KafkaConfig` 补 `underwritingDecidedTopic`（3 分区，原缺失、依赖 broker 自动建主题则分区数不可控）。新增 `UnderwritingKafkaEventPublisherTest` 3 用例锁死该语义。

> 已修复缺口（m8-1103，2026-09-14）：
> - ✅ **保全核保结论枚举跨域重复定义**：`MaintenanceUnderwritingConclusion` 原在**本域 `domain/valueobject`** 与**保全域 `common/enums/workflow`** 各存一份同名枚举，常量虽同而口径方法分散（本域版带 `toUnderwritingStatus()`，保全域版带 `BaseEnum` 四属性与 `accepted()`），任一侧新增结论即漏改；且落在 `valueobject` 包**直接违反根规约 §3.4.2**「枚举只允许存在于 metadata 与 `{domain}-common` 两处」。
>   修复：按「跨域共享枚举入 metadata」上提至 `com.titanium.metadata.enums.underwriting.MaintenanceUnderwritingConclusion`（单一定义，合并两侧全部口径：四属性 + `completed()` + `accepted()` + `toUnderwritingStatus()` + `fromCode()`），两域 26 个引用点改 import、删除两处本地定义。
>   🔴 **跨域兼容性判据**：Feign 契约 `MaintenanceUnderwritingResponse.conclusion` 是 **String**（`MaintenanceUnderwritingWebMapper.conclusionName(...)` 取 `name()`），两版常量名逐字相同 ⇒ 收敛**不改变跨域传输形态**，存量无兼容问题。反之，枚举的常量名即跨域契约，**一经发布不可改名**。
>   **测试**：metadata 新增 `MaintenanceUnderwritingConclusionTest` 7 例（跨域契约 `code == name()`、数字码唯一且不重排、未知码显式失败、`completed`/`accepted`/`toUnderwritingStatus` 逐项断言、常量计数防新增漏判）。
>   **门禁**：metadata 41 例 / maintenance 554 例 / underwriting 124 例，三域 `mvn -B clean install` 全绿。

### 已修复缺口（m8-1104，2026-09-14）

- ✅ **规则引擎执行审计的业务上下文透传**：本域调规则引擎时**不携带业务单号**，导致规则引擎侧 `t_rule_execution_log` 的 `business_id`/`business_type` 恒为空，**无法按核保单反查「某次核保用了哪条规则集、命中了什么」**。
  修复：`RuleEngineServicePort.executeRuleSet` 末参追加 `businessId`，`UnderwritingDecisionOrchestrator` 传 `command.underwritingId().value()`；`RuleEngineServiceAdapter` 以常量 `BUSINESS_TYPE = BusinessDomainType.UNDERWRITING.getCode()` 上报业务域类型。
  🔴 **契约走可选请求头** `X-Business-Id`/`X-Business-Type`（与既有 `X-Tenant-Id` 同构），**不进请求体**——规则执行入口的 body 是裸 `Map<String,Object>` 规则变量，塞业务字段会污染规则变量命名空间。
  🔴 `businessType` 是**端口固有属性**（由 adapter 常量决定，非调用方逐次传入）；`businessId` 一律**追加为最后一个参数**以最小化既有参数语义扰动。
  回归：`RuleEngineServiceAdapterTest` 新增 `missingBusinessIdStillReportsBusinessDomainType`（业务单号缺失不阻断执行，域类型仍上报）。

### 已修复缺口（m9-1204 实读纠偏，2026-09-14）

> 本节记录本次逐条实读中**已失效**的旧缺陷条目与其修复证据。凡标注「已修复」者，均为对照当前代码复核确认，勿再按旧文行事。

- ✅ **Feign 自调用反模式**（旧缺陷 2）：`UnderwritingController` 曾注入自身服务的 `UnderwritingApi` 绕圈自调用（`65634e6^` 版本第 17、34 行），且契约实现类 `web/controller/api/UnderwritingApiController` 与 Controller 职责混淆。现 controller 只注入应用层门面（`UnderwritingController.java:61-65`：`UnderwritingCommandService`/`UnderwritingQueryAppService`/`UnderwritingWebAssembler`/两个 Mapper），Feign 契约由 `web/provider/UnderwritingApiProvider.java:49`、`MaintenanceUnderwritingApiProvider.java:20` 实现（符合根规约 §3.4.10「契约实现落 web/provider、controller 不得 implements Api」）。修复提交 `65634e6`（2026-07-09）。
- ✅ **未用 Liquibase**（旧缺陷 4）：`application.yml` 现为 `ddl-auto: none` + `spring.liquibase.enabled: true`（`change-log: classpath:liquibase/changelog-master.xml`），`bootstrap/src/main/resources/liquibase/ddl/` 下 8 个脚本管理 6 张 `axon_*` 系统表 + `t_underwriting_view` + `t_health_notice`/`t_medical_exam`/`t_underwriting_decision`/`t_business_number_sequence`。旧文所记 `MySQL5InnoDBDialect` 已随 Hibernate 7 移除，方言由 JDBC 自动探测（yml 内注释说明）。修复提交 `aa22bd8`（2026-07-15）。
- ✅ **死查询/重复查询**（旧缺陷 6）：`domain/query` 包连同 `UnderwritingQuery`（无 Handler）、`FindUnderwritingByPolicyIdQuery`（与 query 子模块重名）已在 `65634e6` 随包结构重构**整体删除**，现全仓仅 9 个 `Find*Query`，全部位于 `titanium-underwriting-query/.../query/`。
- ✅ **Controller 桩方法**（旧缺陷 7）：修复前 `UnderwritingController` 的「按保单/按状态/全量」三个查询为 TODO 桩（`return ResponseEntity.notFound().build()` / `List.of()`，见 `65634e6^` 版本第 90-91、104-105、115-116 行）；现 `/search`、`/pending`、`/statistics` 三端点均真调 `underwritingQueryAppService` 并回读读模型（`UnderwritingController.java:165-227`）。修复提交 `65634e6`；读/写契约面另经 `564c897`（2026-09-14）补齐分页参数。
- ✅ **重复决策逻辑**（旧缺陷 8）：`UnderwritingDomainService` 已删除（全仓 Java 零引用，`git log -S` 定位到 `65634e6`），「金额阈值 vs 风险等级 switch 两套并存」的口径冲突随之消解——现决策唯一入口是聚合根 `determineUnderwritingStatus`（`Underwriting.java:170-180`，输入优先、金额回退）。
- ✅ **README.md 过期**（旧缺陷 10）：模块 README 已统一改写，现为 Spring Boot 4.0.1 / Axon 4.10 / Kafka 4.0 / MySQL 8（`README.md:8-9,61-63`），无 3.2.x / 4.1 / PostgreSQL 残留。修复提交 `1d3637d`（2026-08-27）。

### 定性纠偏（m10-1302，2026-09-14）

> 本节记录**结论被推翻**的旧条目。与上节「已修复」性质不同：那些确属缺陷且已修，本节这条**本就不是缺陷**，只是被误判。凡入此节者，勿再按旧文行事。

- ✅ **「入站跨域链路缺失」正名为设计定位**（原「在逃缺陷 1」，见本节第 1 条）——取证路径全在**调用方侧**：
  - `titanium-policy-application/.../saga/IssuanceSaga.java:79`（`@Saga`）、`:91`（`transient UnderwritingDecisionGateway`）、`:223-224`（`@SagaEventHandler(associationProperty = "insuranceId")` 收 `InsuranceSubmittedForUnderwritingEvent`）、`:235`（`requestDecision(request)`）、`:237`（`sendAndWait(new ReceiveUnderwritingResultCommand(...))`）——链路由 **policy 域发起**；
  - `titanium-policy-infrastructure/.../adapter/underwriting/SyncUnderwritingDecisionAdapter.java:42,44,49,52`（`@Component implements UnderwritingDecisionGateway`，注入 `UnderwritingApi`）——下行经本域 Feign 契约完成「创建核保 → 提交结构化输入 → 触发决策 → 回传结论」四步；
  - `titanium-policy-infrastructure/pom.xml:105` 依赖 `titanium-underwriting-api`——**本域是被调用方（服务端）**，非发起方。
  - 本域 Kafka 角色**仅为出站发布方**（`underwriting-decided`，见《跨域事件目录-2026-09.md》第 101 行，状态「闭环」）；全域（含测试）`@KafkaListener` 计数为 0。
  🔴 **判据**：本域「零 `@KafkaListener`」只证明**不消费消息**，不等于「入站链路未通」——定性入站链路必须结合**对端如何调用**，仅看本域取证会把「同步 Feign 服务端」误判成「链路缺失」。

---

### 已修复缺口（m11-1404，2026-09-14）

- ✅ **产品核保配置来源不可区分**：核保决策前经 `ProductUnderwritingConfigPort` 取产品核保策略，取不到时 adapter 三处兜底**一律返回同一个无来源标记的默认配置**（`surchargeAcceptable=true`、无金额阈值、无规则集编码）。后果有三：①「产品**没配**核保策略」与「产品域**取不到**配置」在系统内不可区分；② 二者又与「产品**显式配置**为允许加费」不可区分；③ 核保可能在**毫无产品策略依据**的情况下产出**加费承保**结论，且事后无从审计——事件里只有 `surchargeAcceptable=true`，看不出是产品配置还是兜底。
  **修复**：新增来源枚举 `common/enums/ProductConfigSource`（`CONFIGURED`/`NOT_CONFIGURED`/`UNAVAILABLE`；按根规约 §3.4.2 落本域 `common`——它描述的是**本域取配置的处境**，非产品域的业务属性），随配置快照 → `DecideUnderwritingCommand` → `UnderwritingDecidedEvent` 全链传递；adapter 三处兜底分标 `NOT_CONFIGURED`（无产品编码，查询条件都不成立）与 `UNAVAILABLE`（调用失败或返回不可用）；编排器在 `!config.configured()` 时 `log.warn` 显式告警，使「本次决策无产品策略依据」同时落在**事件流**与**结构化日志**两个可检索通道。
  🔴 **只显式化语义，不改判定**：三态下 `surchargeAcceptable` 仍为 `true`（存量兼容取值，`ProductUnderwritingConfig#defaultConfig` javadoc 明写「过渡期兼容决策、非业务判断」）——改值会令存量「加费承保」翻转为「拒保」，属破坏性变更。回归保护见 `ProductUnderwritingConfigTest#defaultConfigKeepsLegacyValues`。
  🔴 **来源缺失从构造期堵死**：`ProductUnderwritingConfig` 紧凑构造器加 `Objects.requireNonNull(configSource, ...)`；`defaultConfig()` 无参版本删除、改为 `defaultConfig(ProductConfigSource)`，强制调用方逐次表态处境。
  🔴 **跨域兼容已从调用方侧核验**：`UnderwritingDecidedEvent` 尾部追加 `configSource`，旧事件 JSON 无此字段时 Jackson 取 null；下游 policy 侧为 fastjson2 防腐 record（未声明字段自动忽略），同 dev-505 `reason` 尾部追加先例。**Axon 事件加字段只能尾部追加，不得改动既有字段顺序或类型**。
  **测试**：domain 新增 `ProductUnderwritingConfigTest` 5 例（来源缺失拒绝构造 / 仅 `CONFIGURED` 计为有依据 / 兜底取值不变 / 规则集空白判定 / 枚举反查）；infra `ProductUnderwritingConfigAdapterTest` +4 例（成功路径 `CONFIGURED`、无产品编码 `NOT_CONFIGURED` 且不发远程调用、产品域返回失败 `UNAVAILABLE`、调用异常 `UNAVAILABLE`）；domain `UnderwritingSynchronousCommandTest` 增断言锁死**命令 → 事件**的来源透传。
  **门禁**：underwriting 全域 `mvn -B clean install` 全绿——**134 例**（domain 41 / infrastructure 21 / query 8 / application 2 / web 17 / bootstrap 45，其中 archunit 43 例含 11 跳过）。

---

**维护提示**：每次改动聚合根/命令/事件后，请同步检查投影器、QueryHandler、Mapper 与测试类，并参考 [AGENTS.md](./AGENTS.md) 的协作检查清单。
