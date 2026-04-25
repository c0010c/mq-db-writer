# 计划 03：Topic 校验与状态初始化

## 1. 目标
落地 topic 启动判定与状态机初值，确保每个 topic 都有确定状态。

## 2. 边界
- 启动阶段重复 topic：应用启动失败。
- `enabled=false/缺失`：直接 `STOPPED`。
- `enabled=true`：校验 datasource/table/primaryKey/唯一索引 + offset 存在性。

## 3. 代码落点（建议）
- `.../topic/TopicConfigValidator.java`
- `.../topic/ConsumerGroupNameGenerator.java`
- `.../status/TopicStatus.java`（枚举）
- `.../status/TopicStatusService.java`
- `.../db/metadata/MySqlMetadataInspector.java`
- `.../rocketmq/OffsetInspector.java`

## 4. 实施步骤
1. 定义状态枚举：`RUNNING/STOPPED/CONFIG_INVALID/OFFSET_NOT_FOUND/START_FAILED/RETRYING/CONSUME_FAILED`。
2. 启动时先做 topic 去重检查；重复则抛异常终止。
3. 实现 consumerGroup 生成器：`appName-normalizedTopic-sync-group`（含长度限制）。
4. 实现 MySQL 元数据检查：
   - 表存在
   - primaryKey 字段存在
   - primaryKey 为单字段 PK 或单字段 UNIQUE。
5. 接入 offset 检查接口：不存在时写 `OFFSET_NOT_FOUND`。
6. 将每个 topic 的判定结果写入状态服务（先内存，后续接状态表）。

## 5. 验证命令
- `./mvnw -q -Dtest=*TopicConfigValidator* test`
- `./mvnw -q -Dtest=*ConsumerGroup* test`

## 6. DoD
- 每个 topic 启动后都能得到确定状态。
- 重复 topic 可阻止应用启动。
