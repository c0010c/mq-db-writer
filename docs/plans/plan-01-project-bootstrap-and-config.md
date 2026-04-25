# 计划 01：项目骨架与配置模型

## 1. 目标
建立可启动的 Spring Boot 工程，完成 `cdc-sync` 全量配置模型与配置摘要日志（脱敏）。

## 2. 边界
- 只做“配置加载 + 对象映射 + 启动日志”，不连 RocketMQ/DB。
- `enabled` 缺失视为 `false`。

## 3. 代码落点（建议）
- `src/main/java/.../CdcSyncApplication.java`
- `src/main/java/.../config/CdcSyncProperties.java`
- `src/main/java/.../config/model/*.java`（RocketMQ、Datasource、Topic）
- `src/main/java/.../startup/ConfigSummaryLogger.java`
- `src/test/java/.../config/CdcSyncPropertiesTest.java`

## 4. 实施步骤（可直接交给 Codex）
1. 初始化 Maven/Gradle 工程并引入依赖：Spring Boot、Validation、Lombok（可选）、Micrometer。
2. 创建 `@ConfigurationProperties(prefix = "cdc-sync")` 主配置对象。
3. 为 topic 配置建模：`topic/enabled/datasource/targetTable/primaryKey/batchSize/consumeThreadMin/consumeThreadMax/consumeFailureThresholdSeconds`。
4. 在配置归一化阶段补默认值：
   - `enabled=null -> false`
   - `batchSize/defaultConsumeThreadMin/defaultConsumeThreadMax/defaultConsumeFailureThresholdSeconds` 用全局默认。
5. 实现启动日志组件，打印：appName、nameServer、datasource 列表、topic 摘要；敏感字段输出 `******`。
6. 为配置绑定写单测：YAML -> Properties 对象，断言默认值生效。

## 5. 验证命令
- `./mvnw -q -DskipTests=false test`
- `./mvnw -q spring-boot:run -Dspring-boot.run.arguments=--spring.main.web-application-type=none`

## 6. 完成定义（DoD）
- 应用可启动。
- 配置绑定与默认值单测通过。
- 启动日志包含必需字段且无明文密码。
