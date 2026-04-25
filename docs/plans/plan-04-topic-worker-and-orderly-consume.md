# 计划 04：Topic Worker 与顺序消费

## 1. 目标
实现“每 Topic 一个 Consumer”的顺序消费运行时框架。

## 2. 边界
- 必须 `MessageListenerOrderly`。
- 批内严格顺序处理。
- 一次 `consumeMessage(List<MessageExt>)` 共用一个 DB 事务。

## 3. 代码落点（建议）
- `.../sync/TopicSyncManager.java`
- `.../sync/TopicSyncWorker.java`
- `.../sync/ConsumeExecutionTemplate.java`
- `.../sync/GracefulShutdownCoordinator.java`

## 4. 实施步骤
1. 设计 `TopicSyncWorker` 生命周期：`start/stop/rebuild`。
2. 创建 RocketMQ consumer 时绑定 topic、group、线程参数、batchSize。
3. 实现消费模板：
   - begin tx
   - for msgs 顺序处理
   - 全成功 commit + 返回 SUCCESS
   - 任一失败 rollback + suspend + 返回 SUSPEND。
4. 记录当前批次上下文（topic/msgId/queueOffset/index）供日志和状态更新使用。
5. 实现优雅停机：停止拉取新消息并等待当前批次（超时强停）。

## 5. 验证命令
- `./mvnw -q -Dtest=*TopicSyncWorker* test`
- `./mvnw -q -Dtest=*GracefulShutdown* test`

## 6. DoD
- 顺序消费框架可启动与停机。
- 批内任一异常可触发整批回滚。
