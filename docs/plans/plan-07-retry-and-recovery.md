# 计划 07：失败重试与恢复调度

## 1. 目标
实现失败不跳过、无限顺序重试，以及 `OFFSET_NOT_FOUND/START_FAILED` 自动恢复。

## 2. 边界
- 不引入死信与跳过机制。
- 消费失败始终回滚当前批次并 `SUSPEND_CURRENT_QUEUE_A_MOMENT`。

## 3. 代码落点（建议）
- `.../sync/ConsumeFailureTracker.java`
- `.../recovery/RecoverableTopicScheduler.java`
- `.../recovery/RecoverableTopicService.java`
- `.../status/ErrorStateClassifier.java`

## 4. 实施步骤
1. 首次失败将 topic 状态置 `RETRYING` 并记录 `last_error_time`。
2. 按 topic 维度计算连续失败时长，超过阈值置 `CONSUME_FAILED`。
3. 增加定时任务（默认 60s）扫描 `OFFSET_NOT_FOUND` 与 `START_FAILED`。
4. 恢复流程：重新校验配置 → 重新检查 offset → 尝试启动 consumer。
5. 成功恢复后置 `RUNNING`，并保留历史错误信息。
6. 原因变化时允许状态切换（如 START_FAILED -> OFFSET_NOT_FOUND）。

## 5. 验证命令
- `./mvnw -q -Dtest=*RecoverableTopic* test`
- `./mvnw -q -Dtest=*ConsumeFailureTracker* test`

## 6. DoD
- 故障 topic 可自动周期性重试恢复。
- 失败消息不会被跳过或吞掉。
