# CDC 同步引擎实施计划总览（拆分版）

你要求“10 个计划，每个计划一个文件，并且细到可被 Codex 直接落地”。
本目录将原总计划拆分为 10 个独立执行文件：

1. [计划01-项目骨架与配置模型](plans/plan-01-project-bootstrap-and-config.md)
2. [计划02-多数据源管理](plans/plan-02-datasource-manager.md)
3. [计划03-Topic 校验与状态初始化](plans/plan-03-topic-validation-and-initial-status.md)
4. [计划04-Topic Worker 与顺序消费](plans/plan-04-topic-worker-and-orderly-consume.md)
5. [计划05-Debezium 解析与事件路由](plans/plan-05-debezium-parser-and-routing.md)
6. [计划06-动态 SQL 与写库执行器](plans/plan-06-dynamic-sql-and-writer.md)
7. [计划07-失败重试与恢复调度](plans/plan-07-retry-and-recovery.md)
8. [计划08-Apollo 热更新编排](plans/plan-08-apollo-hot-reload.md)
9. [计划09-状态异步落库与指标](plans/plan-09-status-pipeline-and-metrics.md)
10. [计划10-联调、压测、上线 Runbook](plans/plan-10-integration-and-runbook.md)

## 使用方式

- **执行顺序**：严格按 01 → 10 依次推进。
- **每个计划文件都包含**：
  - 目标与边界
  - 代码落点（建议目录/类）
  - 逐步实现任务（可直接分配给 Codex）
  - 命令级验证清单
  - 完成定义（DoD）
