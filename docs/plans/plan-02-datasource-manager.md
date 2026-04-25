# 计划 02：多数据源管理

## 1. 目标
实现 `DynamicDataSourceManager`：启动创建 datasource、支持新增 datasource 热创建、对不支持热更新项只告警。

## 2. 边界
- 支持：新增 datasource。
- 不支持：修改 url/username/password/driver/pool 后热替换。
- 初始化失败不导致应用退出，但引用 topic 要可判定为 `CONFIG_INVALID`。

## 3. 代码落点（建议）
- `.../datasource/DynamicDataSourceManager.java`
- `.../datasource/DataSourceFactory.java`
- `.../datasource/DataSourceRegistry.java`
- `.../datasource/PoolConfigMerger.java`
- `.../datasource/HealthProbeService.java`

## 4. 实施步骤
1. 定义 datasource 配置对象与默认池参数合并逻辑。
2. 工厂化创建 HikariDataSource，注册到并发 Map。
3. 提供 API：`Optional<JdbcTemplate> getJdbcTemplate(String name)`。
4. 启动时遍历所有 datasource 执行创建，失败写错误缓存（`failedDataSources`）。
5. 提供 `onConfigChanged(newConfig)`：
   - 新增 key -> 尝试创建并注册。
   - 已存在 key 且内容变更 -> 记录 warn：需重启生效。
6. 增加健康检查：验证 `statusDatasource` 可连通（后续计划会用）。

## 5. 验证命令
- `./mvnw -q -Dtest=*DataSource* test`
- `./mvnw -q test`

## 6. DoD
- 可以按名称拿到 JdbcTemplate。
- 新增 datasource 热创建成功。
- 修改已有 datasource 仅日志提示，不替换旧连接池。
