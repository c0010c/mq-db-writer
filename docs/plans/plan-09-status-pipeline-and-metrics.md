# 计划 09：状态异步落库与指标

## 1. 目标
实现状态异步更新管道与 Micrometer 指标，做到“观测能力不影响主链路”。

## 2. 边界
- 状态表更新不参与业务事务。
- 队列满可超时丢弃状态事件，但不能影响消费结果。

## 3. 代码落点（建议）
- `.../status/TopicStatusUpdateEvent.java`
- `.../status/StatusUpdateQueue.java`
- `.../status/StatusUpdateWorker.java`
- `.../status/TopicStatusRepository.java`
- `.../metrics/MetricsRecorder.java`

## 4. 实施步骤
1. 启动时校验 `cdc_sync_topic_status` 表存在，不存在则应用失败。
2. 消费线程在事务结束后投递状态事件（成功/失败/忽略等）。
3. Worker 按“每秒或满 100 条”批量刷新。
4. 同一 topic 在一个批次内仅保留最后事件再入库。
5. 队列满时 `offer(timeout)`，超时后记录 dropped 指标并告警。
6. 指标落地：成功/失败/忽略/耗时/状态更新失败与丢弃等。

## 5. 验证命令
- `./mvnw -q -Dtest=*StatusUpdate* test`
- `./mvnw -q -Dtest=*MetricsRecorder* test`

## 6. DoD
- 状态更新失败不影响消费 ACK 结果。
- 核心指标可用于告警与容量观察。
