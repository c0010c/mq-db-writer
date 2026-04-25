# 计划 08：Apollo 热更新编排

## 1. 目标
支持 topic 配置热更新，并严格遵守“可热更/不可热更”边界与 pending rebuild 语义。

## 2. 边界
- 可热更：新增 topic、enabled、datasource/targetTable/primaryKey、failureThreshold、新增 datasource。
- 不可热更：batchSize、RocketMQ 连接配置、已存在 datasource 连接参数。

## 3. 代码落点（建议）
- `.../apollo/ApolloConfigChangeListener.java`
- `.../apollo/TopicConfigDiffCalculator.java`
- `.../sync/PendingRebuildStore.java`
- `.../sync/TopicRebuildOrchestrator.java`

## 4. 实施步骤
1. 实现 old/new topic 配置 diff：新增、删除、修改、重复项。
2. 热更遇重复 topic：仅忽略重复项并告警，其余项继续应用。
3. 处理 `enabled=false`：立即优雅停 consumer，状态 `STOPPED`。
4. 处理核心配置变更：
   - 先校验新配置
   - 若通过，写入 pending rebuild
   - 当前批次完成后再重建。
5. pending rebuild 期间再次变更：仅保留最后一份配置。
6. pending rebuild 期间若变为 `enabled=false`：取消 pending 并停机。

## 5. 验证命令
- `./mvnw -q -Dtest=*TopicConfigDiff* test`
- `./mvnw -q -Dtest=*TopicRebuildOrchestrator* test`

## 6. DoD
- 热更新行为与规范一致，不会破坏顺序重试中的批次一致性。
