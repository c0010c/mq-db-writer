# 计划 10：联调、压测、上线 Runbook

## 1. 目标
把 01~09 的能力收敛为可上线交付包：联调脚本、故障演练、运行手册、上线检查清单。

## 2. 边界
- 本计划不再新增核心功能，只做集成验证与运维交付。

## 3. 交付物
- `docs/runbook.md`（启停、回滚、恢复、扩缩容）
- `docs/sop-go-live.md`（全量+增量衔接与 offset 操作）
- `docs/troubleshooting.md`（高频故障诊断）
- 压测结果报告（topic 数、吞吐、延迟、失败恢复时间）

## 4. 实施步骤
1. 准备联调环境：RocketMQ、MySQL、Apollo（或替代配置中心）。
2. 场景验证矩阵：
   - 正常 c/u/d 写入
   - op=r/unknown/tombstone 忽略
   - SQL 失败触发重试
   - OFFSET_NOT_FOUND 与 START_FAILED 自动恢复
   - 热更新重建与 enabled=false 停机
   - 优雅停机和超时强停
3. 编写可重复执行的验收脚本（shell + SQL）。
4. 输出容量建议：topic 数、线程数、连接池、状态队列容量。
5. 输出风险控制：rawBody 敏感信息、标识符安全、人工 offset 误操作。

## 5. 验证命令（示例）
- `./mvnw -q test`
- `./scripts/e2e/run-all.sh`
- `./scripts/e2e/chaos-retry.sh`

## 6. DoD
- 全部必测场景通过并留存报告。
- 运维侧可按 Runbook 独立完成发布、回滚、恢复。
