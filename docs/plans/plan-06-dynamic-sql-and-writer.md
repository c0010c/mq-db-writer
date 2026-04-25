# 计划 06：动态 SQL 与写库执行器

## 1. 目标
实现 MySQL 方言动态 SQL 生成、缓存与执行（upsert/delete）。

## 2. 边界
- `upsert` 使用 `INSERT ... ON DUPLICATE KEY UPDATE`。
- update 部分包含全部 `after` 字段（含主键）。
- 标识符统一反引号包裹。

## 3. 代码落点（建议）
- `.../sql/SqlDialect.java`
- `.../sql/MySqlDialect.java`
- `.../sql/DynamicSqlCache.java`
- `.../writer/DynamicTableWriter.java`
- `.../writer/DbWriteExecutor.java`

## 4. 实施步骤
1. 定义 SQL 生成接口：`buildUpsertSql(table, columns)`、`buildDeleteSql(table, pk)`。
2. 缓存 key：
   - upsert: `datasource + table + UPSERT + sortedColumns`
   - delete: `datasource + table + DELETE + pk`
3. 生成参数列表时保持与 SQL 列顺序一致。
4. 设置 query timeout（全局 `sqlQueryTimeoutSeconds`）。
5. 处理 delete 影响行数：0/1 都视为成功。
6. 添加 SQL/参数日志（debug 级别，生产默认关闭）。

## 5. 验证命令
- `./mvnw -q -Dtest=*MySqlDialect* test`
- `./mvnw -q -Dtest=*DynamicTableWriter* test`

## 6. DoD
- `c/u/d` 对应 SQL 均可正确生成并执行。
- 同列集合不同顺序时 SQL 缓存可复用。
