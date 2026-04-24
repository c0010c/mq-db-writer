# Debezium JSON → RocketMQ → MySQL 同步程序需求规格说明书

## 1. 项目目标

实现一个基于 **Spring Boot + RocketMQ + Apollo + JdbcTemplate** 的 CDC 同步程序。

程序从 RocketMQ 的多个 Topic 中消费 **Debezium 风格 JSON 消息**，解析后写入指定 MySQL 目标表。

核心目标：

```text
1. 每个 RocketMQ Topic 对应一张目标表。
2. 每个 Topic 可配置目标 datasource、targetTable、primaryKey。
3. 支持多实例集群消费。
4. 支持按业务唯一键顺序消费。
5. 支持 c/u 消息 upsert，d 消息物理删除。
6. 支持 Apollo 动态管理 Topic 配置。
7. 失败时不跳过消息，依赖 RocketMQ 顺序重试，人工修复后自动恢复。
8. 第一版只整理为同步引擎，不做复杂字段转换、数据修复、管理后台。
```

---

## 2. 第一版范围

### 2.1 支持能力

第一版支持：

```text
1. 多 Topic 消费。
2. 每 Topic 一个 RocketMQ Consumer。
3. 每 Topic 自动生成独立 ConsumerGroup。
4. 多实例集群消费。
5. RocketMQ 顺序消费。
6. Apollo 配置 Topic、datasource、targetTable、primaryKey。
7. 多 datasource 写入。
8. MySQL 8.0 目标库。
9. JdbcTemplate 动态 SQL。
10. INSERT ... ON DUPLICATE KEY UPDATE upsert。
11. DELETE BY primaryKey 物理删除。
12. Debezium Envelope 和简化 JSON 两种格式。
13. 状态表记录 Topic 当前状态。
14. Micrometer 指标。
15. 错误日志打印完整 raw body。
16. 优雅停机。
17. Topic 配置热更新。
18. OFFSET_NOT_FOUND / START_FAILED 定时恢复。
```

### 2.2 第一版不支持能力

第一版不支持：

```text
1. 自动建目标表。
2. 自动加字段。
3. 自动过滤目标表不存在字段。
4. 字段名映射。
5. 字段白名单 / 黑名单。
6. 字段类型转换。
7. schema 解析。
8. 联合主键 / 联合唯一键。
9. 软删除。
10. dry-run 模式。
11. 管理接口。
12. 状态历史事件表。
13. queue 级状态表。
14. 自动 seek 到指定 RocketMQ offset。
15. 自动按时间点开始消费。
16. 自定义死信 / 跳过失败消息。
17. datasource 修改热更新。
18. RocketMQ 连接配置热更新。
19. batchSize 热更新。
20. 多数据库方言真实适配，第一版只实现 MySQL。
```

---

## 3. 总体架构

推荐第一版架构：

```text
Spring Boot Application
  |
  |-- ApolloConfigLoader
  |
  |-- DynamicDataSourceManager
  |     |-- DataSource(syncDbA)
  |     |-- DataSource(syncDbB)
  |
  |-- TopicSyncManager
  |     |-- TopicSyncWorker(topic_a)
  |     |     |-- RocketMQ Orderly Consumer
  |     |     |-- DebeziumJsonParser
  |     |     |-- DynamicTableWriter
  |     |
  |     |-- TopicSyncWorker(topic_b)
  |           |-- RocketMQ Orderly Consumer
  |           |-- DebeziumJsonParser
  |           |-- DynamicTableWriter
  |
  |-- SqlDialect
  |     |-- MySqlDialect
  |
  |-- StatusUpdateQueue
  |     |-- StatusUpdateWorker
  |
  |-- RecoverableTopicScheduler
  |
  |-- MetricsRecorder
```

---

## 4. RocketMQ 消费模型

### 4.1 消费模式

使用 **集群消费**。

```text
多个应用实例使用同一个 Topic 对应的 ConsumerGroup。
RocketMQ 将同一个 Topic 的 MessageQueue 分配给不同实例。
同一个 MessageQueue 同一时间只会被一个实例消费。
```

### 4.2 Consumer 管理方式

第一版采用：

```text
每个 Topic 一个 RocketMQ Consumer。
每个 Topic 一个独立 ConsumerGroup。
```

这样可以提升：

```text
1. Topic 级故障隔离。
2. Topic 级启停控制。
3. Apollo 热更新清晰度。
4. 日志和指标定位能力。
5. 人工恢复便利性。
```

虽然 Topic 数量预计可能达到 **50～200 个**，第一版仍坚持该模型，不限制 Topic 数量。

### 4.3 ConsumerGroup 生成规则

ConsumerGroup 由程序自动生成，不在 Apollo 中逐个配置。

建议规则：

```text
consumerGroup = appName + "-" + normalizedTopic + "-sync-group"
```

示例：

```text
appName = debezium-cdc-sync
topic = user_cdc_topic

consumerGroup = debezium-cdc-sync-user_cdc_topic-sync-group
```

要求：

```text
1. 同一个 topic 在所有实例上生成结果必须一致。
2. 不允许随机后缀。
3. topic 中特殊字符可规范化为下划线。
4. group 名称需要控制长度。
```

---

## 5. 顺序消费要求

### 5.1 顺序粒度

顺序粒度为：

```text
按指定唯一键有序。
第一版唯一键来自 Debezium 消息体里的 primaryKey 字段。
后续可扩展为 Apollo 配置唯一键提取规则。
```

### 5.2 顺序成立前提

消费者端只能保证 **RocketMQ MessageQueue 内有序**。

因此必须满足前置条件：

```text
生产者必须保证同一个业务唯一键的所有消息发送到同一个 RocketMQ MessageQueue。
```

如果生产者没有做到这一点，消费者无法保证同一个业务主键全链路有序。

### 5.3 队列数策略

每个 Topic 的队列数按 Topic 自己规划。

约束：

```text
Topic 队列数上线后原则上固定。
如果必须调整队列数：
  1. 停止生产。
  2. 等消费追平。
  3. 调整队列数和 Apollo 配置。
  4. 恢复生产。
```

原因：

```text
如果生产者使用 hash(primaryKey) % queueCount，
queueCount 改变会导致同一个 primaryKey 后续消息进入不同队列，
从而产生跨队列乱序风险。
```

---

## 6. Apollo 配置模型

### 6.1 总体配置示例

```yaml
cdc-sync:
  appName: debezium-cdc-sync

  rocketmq:
    nameServer: 127.0.0.1:9876
    aclEnabled: true
    accessKey: xxx
    secretKey: xxx

  statusDatasource: syncAdminDb

  datasourceDefaults:
    maximumPoolSize: 10
    minimumIdle: 2
    connectionTimeoutMs: 30000
    idleTimeoutMs: 600000
    maxLifetimeMs: 1800000

  datasources:
    syncAdminDb:
      url: jdbc:mysql://mysql-admin:3306/cdc_admin
      username: sync_admin
      password: xxx
      driverClassName: com.mysql.cj.jdbc.Driver

    syncDbA:
      url: jdbc:mysql://mysql-a:3306/sync_db_a
      username: sync_user
      password: xxx
      driverClassName: com.mysql.cj.jdbc.Driver

    syncDbB:
      url: jdbc:mysql://mysql-b:3306/sync_db_b
      username: sync_user
      password: xxx
      driverClassName: com.mysql.cj.jdbc.Driver
      pool:
        maximumPoolSize: 20
        minimumIdle: 5

  defaultBatchSize: 10
  defaultConsumeThreadMin: 1
  defaultConsumeThreadMax: 1
  defaultConsumeFailureThresholdSeconds: 300

  suspendCurrentQueueTimeMillis: 5000
  gracefulShutdownTimeoutMillis: 60000
  sqlQueryTimeoutSeconds: 30

  statusUpdateQueueCapacity: 10000
  statusUpdateOfferTimeoutMillis: 5000
  statusFlushIntervalMillis: 1000
  statusFlushBatchSize: 100

  recoverableTopicCheckIntervalMillis: 60000

  topics:
    - topic: user_cdc_topic
      enabled: true
      datasource: syncDbA
      targetTable: user_info
      primaryKey: id
      batchSize: 10
      consumeThreadMin: 1
      consumeThreadMax: 1
      consumeFailureThresholdSeconds: 300

    - topic: device_cdc_topic
      enabled: false
      datasource: syncDbB
      targetTable: device_info
      primaryKey: device_id
```

### 6.2 Topic 配置字段

每个 Topic 支持：

```text
topic                           RocketMQ Topic 名称
enabled                         是否启用，缺省 false
datasource                      目标数据源名称
targetTable                     目标表名
primaryKey                      单字段主键 / 单字段唯一键
batchSize                       RocketMQ 单次回调最大消息数，默认 10
consumeThreadMin                消费线程最小值，默认 1
consumeThreadMax                消费线程最大值，默认 1
consumeFailureThresholdSeconds  消费失败持续多久进入 CONSUME_FAILED，默认 300 秒
```

### 6.3 enabled 语义

```text
enabled 未配置 = enabled=false。
```

规则：

```text
新增 Topic：
  enabled 缺失 -> 不启动，status = STOPPED。

已有 Topic 热更新：
  enabled 缺失 -> 视为禁用。
  优雅停止旧 Consumer。
  status = STOPPED。

enabled=false：
  不校验 datasource。
  不校验 targetTable。
  不校验 primaryKey。
  不检查 offset。
  不创建 Consumer。
  status = STOPPED。

enabled=true：
  执行完整校验。
  检查 offset。
  创建并启动 Consumer。
```

---

## 7. Apollo 热更新规则

### 7.1 支持热更新的内容

第一版支持热更新：

```text
1. 新增 Topic。
2. 修改 Topic enabled。
3. 修改 Topic datasource。
4. 修改 Topic targetTable。
5. 修改 Topic primaryKey。
6. 修改 Topic consumeFailureThresholdSeconds。
7. 新增 datasource 并热创建。
```

### 7.2 不支持热更新的内容

第一版不支持热更新：

```text
1. Topic batchSize。
2. RocketMQ nameServer。
3. RocketMQ ACL 开关。
4. RocketMQ accessKey / secretKey。
5. datasource url。
6. datasource username。
7. datasource password。
8. datasource driverClassName。
9. datasource 连接池参数。
```

这些配置修改后需要重启应用生效。

### 7.3 删除 Topic 配置

如果 Apollo 中删除某个 Topic 配置：

```text
第一版不立即停止正在运行的 Consumer。
当前 Consumer 继续运行。
程序不再因为配置变更主动重建该 Topic。
应用重启后，因为配置已删除，该 Topic 不再启动。
```

原因：

```text
防止 Apollo 误删配置导致线上同步突然中断。
```

### 7.4 enabled=false

如果 Topic 配置改为：

```yaml
enabled: false
```

处理：

```text
立即优雅停止该 Topic Consumer。
status = STOPPED。
```

如果该 Topic 处于 `CONSUME_FAILED`，也允许停止 Consumer。

停止后：

```text
不会提交失败消息成功。
后续 enabled=true 后，会从 RocketMQ 原消费位点继续。
```

### 7.5 datasource / targetTable / primaryKey 修改

如果修改 Topic 的核心配置：

```text
datasource
targetTable
primaryKey
```

处理：

```text
1. 先校验新配置。
2. 校验通过后，标记 Topic 等待重建。
3. 停止拉取新消息。
4. 等待当前批次处理完成。
5. shutdown 旧 Consumer。
6. 创建新 Consumer。
7. status = RUNNING。
```

如果旧 Consumer 当前批次一直失败重试：

```text
不强制切换配置。
继续按旧配置重试。
只有旧批次成功后，才允许切换到新配置。
```

### 7.6 pending rebuild

如果某个 Topic 已经有待重建配置，Apollo 又再次修改该 Topic：

```text
只保留最后一次新配置。
中间待重建配置直接丢弃。
```

### 7.7 pending rebuild 遇到 enabled=false

如果 Topic 处于 pending rebuild，Apollo 改成 enabled=false：

```text
1. 取消 pending rebuild。
2. 丢弃 pending config。
3. 优雅停止旧 Consumer。
4. status = STOPPED。
```

---

## 8. Apollo 配置异常处理

### 8.1 启动阶段重复 Topic

如果应用启动时 Apollo 配置中出现重复 Topic：

```text
应用启动失败。
```

重复 Topic 属于全局配置结构错误。

### 8.2 热更新阶段重复 Topic

如果 Apollo 热更新时出现重复 Topic：

```text
重复的 Topic 本次热更新忽略。
其他不重复的 Topic 正常热更新。
重复 Topic 记录 ERROR 日志和指标告警。
```

对于重复 Topic：

```text
不使用前一个。
不使用后一个。
继续沿用旧运行配置。
```

### 8.3 热更新时必要字段缺失

如果 Apollo 热更新时某个 Topic 缺少必要字段：

```text
datasource
targetTable
primaryKey
```

处理：

```text
立即停止旧 Consumer。
status = CONFIG_INVALID。
last_error_message 记录缺失字段。
```

这和“字段值不合法”不同。

```text
字段值不合法：
  例如 targetTable 不存在、primaryKey 没唯一索引。
  保留旧 Consumer 继续运行。

字段结构缺失：
  例如 datasource 缺失。
  停止旧 Consumer，状态置 CONFIG_INVALID。
```

---

## 9. 数据源规则

### 9.1 多数据源

不同 Topic 可以写入不同 datasource。

```yaml
topics:
  - topic: user_cdc_topic
    datasource: syncDbA
    targetTable: user_info
    primaryKey: id

  - topic: device_cdc_topic
    datasource: syncDbB
    targetTable: device_info
    primaryKey: device_id
```

### 9.2 datasource 连接信息

datasource 连接信息全部放 Apollo：

```text
url
username
password
driverClassName
pool 参数
```

### 9.3 数据库类型

第一版所有 datasource 都只支持：

```text
MySQL 8.0
```

代码结构保留 `SqlDialect` 抽象，但第一版只实现：

```text
MySqlDialect
```

### 9.4 datasource 生命周期

规则：

```text
应用启动：
  加载 Apollo 中已有 datasource。
  初始化连接池。

新增 datasource：
  支持热创建。
  创建成功后可被新增 / 修改 Topic 引用。

修改已有 datasource：
  不热更新。
  已创建连接池继续使用旧配置。
  记录日志 / 指标，提示需要重启生效。

删除 datasource：
  不热删除。
  已创建连接池继续保留。
  当前运行中的 Topic 不受影响。
  应用重启后该 datasource 不再存在。

启动时 datasource 初始化失败：
  应用不退出。
  引用它的 Topic 不启动。
  status = CONFIG_INVALID。
  修复后必须重启应用。

新增 datasource 热创建失败：
  应用不退出。
  引用它的 Topic 不启动。
  status = CONFIG_INVALID。
  修复后必须重启应用。
```

---

## 10. 目标表要求

### 10.1 目标表提前创建

目标表必须由 DBA / 运维提前创建。

程序不负责：

```text
1. 创建目标表。
2. 修改目标表结构。
3. 自动加字段。
4. 自动建索引。
```

### 10.2 表结构关系

第一版要求：

```text
源表字段和目标表字段基本一致。
Debezium after/before 字段名和目标表字段名完全一致。
```

### 10.3 目标表额外字段

允许目标表字段比 Debezium `after/before` 更多。

约束：

```text
目标表多出来的字段必须允许 NULL 或有默认值。
```

否则 insert 时数据库报错，消费失败并重试。

### 10.4 Debezium 字段目标表不存在

如果 `payload.after` 中存在目标表不存在的字段：

```text
第一版不提前过滤。
执行 SQL 时由数据库报错。
当前批次事务回滚。
RocketMQ 顺序重试。
等待人工修复。
```

### 10.5 targetTable 不允许带库名

第一版 Apollo 中：

```yaml
targetTable: user_info
```

不允许：

```yaml
targetTable: sync_db.user_info
```

未来多库建议使用：

```yaml
datasource: syncDbA
targetTable: user_info
```

---

## 11. primaryKey 和唯一索引校验

### 11.1 单字段主键 / 唯一键

第一版只支持：

```text
单字段主键。
单字段唯一索引。
```

不支持：

```text
联合主键。
联合唯一索引。
```

### 11.2 启动 / 热更新校验

对于 enabled=true 的 Topic：

```text
1. datasource 必须存在。
2. targetTable 必须存在。
3. primaryKey 字段必须存在于 targetTable。
4. primaryKey 必须是单字段 PRIMARY KEY 或单字段 UNIQUE KEY。
```

如果目标表只有联合唯一索引：

```sql
UNIQUE KEY uk_user_device (user_id, device_id)
```

Apollo 配置：

```yaml
primaryKey: user_id
```

则：

```text
校验失败。
status = CONFIG_INVALID。
该 Topic Consumer 不启动。
```

---

## 12. Debezium JSON 解析规则

### 12.1 支持两种消息结构

#### 标准 Envelope

```json
{
  "schema": {},
  "payload": {
    "before": {},
    "after": {},
    "op": "u",
    "source": {},
    "ts_ms": 123456789
  }
}
```

#### 简化格式

```json
{
  "before": {},
  "after": {},
  "op": "u"
}
```

解析规则：

```text
如果 root.payload 是对象：
  使用 root.payload.before / root.payload.after / root.payload.op

否则：
  使用 root.before / root.after / root.op
```

### 12.2 只读取字段

第一版只读取：

```text
before
after
op
```

不处理：

```text
schema
source
ts_ms
transaction
```

### 12.3 UTF-8 编码

RocketMQ message body 编码统一为：

```text
UTF-8
```

解析方式：

```java
String rawBody = new String(messageExt.getBody(), StandardCharsets.UTF_8);
```

---

## 13. op 处理规则

### 13.1 op=c

```text
读取 payload.after。
after 必须非空。
after[primaryKey] 必须非空。
执行 upsert。
```

### 13.2 op=u

```text
读取 payload.after。
after 必须是完整行数据。
after 必须非空。
after[primaryKey] 必须非空。
执行 upsert。
```

### 13.3 op=d

```text
读取 payload.before。
before 必须非空。
before[primaryKey] 必须非空。
执行物理删除。
```

SQL：

```sql
DELETE FROM `target_table`
WHERE `primary_key` = ?
```

如果影响行数为 0：

```text
认为成功。
DELETE BY primaryKey 天然幂等。
```

### 13.4 op=r

第一版忽略 Debezium 快照读事件：

```text
op = r
打印 INFO / WARN。
不写库。
继续消费。
```

原因：

```text
全量同步由离线 SQL / DataX / DTS 完成。
CDC 程序只负责后续增量。
```

### 13.5 未知 op

如果 op 存在，但不是：

```text
c / u / d / r
```

处理：

```text
打印 WARN。
忽略该消息。
继续消费。
不阻塞队列。
```

### 13.6 op 缺失或为空

如果 op 缺失或为空：

```text
视为格式错误。
当前批次事务回滚。
返回 SUSPEND_CURRENT_QUEUE_A_MOMENT。
当前队列阻塞重试。
```

---

## 14. tombstone 消息处理

Debezium tombstone 消息包括：

```text
body == null
body.length == 0
rawBody.trim().isEmpty()
payload == null
```

处理：

```text
忽略。
打印 INFO / WARN。
不写库。
不算消费失败。
当前批次继续处理后续消息。
```

注意区分：

```text
op=d 且 before 有主键：
  有效删除事件，执行 delete。

payload=null：
  tombstone，忽略。
```

---

## 15. 字段值处理规则

第一版不处理 Debezium schema，不做复杂类型转换。

字段值转换规则：

```text
null          -> JDBC null
string        -> String
number        -> Number
boolean       -> Boolean
object        -> JSON 字符串
array         -> JSON 字符串
date/time     -> 不特殊转换
decimal       -> 不特殊转换
```

### 15.1 object / array

如果字段值是对象或数组：

```text
直接序列化成 JSON 字符串写入目标字段。
```

示例：

```json
{
  "id": 1,
  "config": {
    "alarm": true,
    "level": 3
  },
  "tags": ["vip", "camera"]
}
```

写入值：

```text
id     = 1
config = {"alarm":true,"level":3}
tags   = ["vip","camera"]
```

如果目标字段是：

```text
JSON / TEXT / VARCHAR
```

通常可正常写入。

如果目标字段是：

```text
INT / DATETIME / DECIMAL 等不兼容类型
```

由数据库报错，进入失败重试。

### 15.2 时间字段

不做特殊转换。

例如：

```text
datetime
timestamp
date
```

无论 JSON 中是字符串、数字时间戳还是其他格式，第一版都直接交给 JDBC / MySQL。

### 15.3 decimal 字段

不做特殊转换。

如果是普通数字或字符串，交给 MySQL 转换。

如果是 Debezium 特殊 base64 decimal 格式，可能写入失败，由人工处理。

---

## 16. upsert / delete 落库规则

### 16.1 upsert SQL

MySQL 第一版使用：

```sql
INSERT INTO `target_table` (`id`, `name`, `age`)
VALUES (?, ?, ?)
ON DUPLICATE KEY UPDATE
  `id` = VALUES(`id`),
  `name` = VALUES(`name`),
  `age` = VALUES(`age`);
```

规则：

```text
1. payload.after 中所有字段都写入。
2. 包括主键字段。
3. 包括自增主键字段。
4. 包括 create_time / update_time 字段。
5. 不自动维护 create_time / update_time。
6. 不过滤字段。
7. 不做字段名转换。
8. update 部分包含所有 after 字段，包括主键。
```

### 16.2 只有主键字段时

如果 after 只有主键：

```json
{
  "id": 1001
}
```

SQL 兜底：

```sql
INSERT INTO `user_info` (`id`)
VALUES (?)
ON DUPLICATE KEY UPDATE
  `id` = VALUES(`id`);
```

### 16.3 delete SQL

```sql
DELETE FROM `target_table`
WHERE `primary_key` = ?
```

规则：

```text
影响行数 1：成功。
影响行数 0：也成功。
SQL 异常：消费失败，事务回滚。
```

---

## 17. 动态 SQL 生成与缓存

### 17.1 使用 JdbcTemplate

第一版推荐使用：

```text
JdbcTemplate
```

不推荐 MyBatis / MyBatis-Plus 作为核心动态同步写入工具。

原因：

```text
1. 多 Topic 多目标表。
2. 表名动态。
3. 字段集合动态。
4. 每张表写 Mapper 成本高。
5. JdbcTemplate 更适合同步引擎型动态 SQL。
```

### 17.2 SQL 缓存

需要缓存动态 SQL。

缓存 key：

```text
datasource + targetTable + op + 字段集合
```

upsert 缓存：

```text
datasource + targetTable + UPSERT + sortedColumns
```

delete 缓存：

```text
datasource + targetTable + DELETE + primaryKey
```

字段集合需要排序，避免 JSON 字段顺序不同导致缓存 miss。

第一版 SQL 缓存不设置最大容量。

### 17.3 表名 / 字段名反引号

SQL 中表名、字段名使用 MySQL 反引号包裹：

```sql
`user_info`
`user_id`
```

示例：

```sql
INSERT INTO `target_table` (`id`, `name`, `order`)
VALUES (?, ?, ?)
ON DUPLICATE KEY UPDATE
  `id` = VALUES(`id`),
  `name` = VALUES(`name`),
  `order` = VALUES(`order`);
```

### 17.4 标识符安全

第一版：

```text
默认信任 Apollo 配置和 Debezium 字段名。
不做严格 SQL 注入校验。
不处理表名 / 字段名中自带反引号的情况。
```

实现：

```java
private String quoteIdentifier(String name) {
    return "`" + name + "`";
}
```

后续建议增强为：

```text
1. 只允许 [a-zA-Z_][a-zA-Z0-9_]*
2. 或对反引号做转义
3. Apollo targetTable / primaryKey 启动时校验
4. Debezium 字段名运行时校验
```

---

## 18. 事务边界

### 18.1 批处理策略

RocketMQ 一次回调：

```java
consumeMessage(List<MessageExt> msgs, ConsumeOrderlyContext context)
```

整个 `List<MessageExt>` 共用一个数据库事务。

规则：

```text
1. 开启目标业务库事务。
2. 按 List 顺序逐条处理消息。
3. 全部成功：提交事务，返回 SUCCESS。
4. 任意一条失败：回滚事务，返回 SUSPEND_CURRENT_QUEUE_A_MOMENT。
```

### 18.2 批大小

`consumeMessageBatchMaxSize` 使用 Apollo 的 `batchSize`。

默认：

```text
batchSize = 10
```

配置：

```yaml
cdc-sync:
  defaultBatchSize: 10

  topics:
    - topic: user_cdc_topic
      batchSize: 10
```

`batchSize` 第一版不支持热更新，修改后需要重启应用或重建 Consumer。

### 18.3 批内顺序

批内处理必须按 `List<MessageExt>` 顺序逐条处理。

不允许：

```text
1. 批内并发处理。
2. 把消息丢到异步线程池乱序处理。
3. 单条消息先返回成功后再异步写库。
```

---

## 19. 消费线程数

每个 Topic Consumer 支持配置：

```text
consumeThreadMin
consumeThreadMax
```

采用：

```text
每 Topic 可配置。
没配使用全局默认。
默认 consumeThreadMin=1。
默认 consumeThreadMax=1。
```

推荐默认：

```yaml
cdc-sync:
  defaultConsumeThreadMin: 1
  defaultConsumeThreadMax: 1
```

原因：

```text
1. 第一版顺序语义最清晰。
2. 更容易排查。
3. 避免内部并发引入乱序风险。
```

即使后续调大线程数，也必须满足：

```text
1. 使用 MessageListenerOrderly。
2. consumeMessage 内部不异步乱序处理。
3. 批内顺序处理。
4. 生产者保证同一 uniqueKey 进入同一 queue。
```

---

## 20. 失败重试与人工恢复

### 20.1 消费失败策略

消费失败时：

```text
1. 回滚当前批次业务事务。
2. 打完整 raw body 错误日志。
3. 投递状态更新事件，记录错误摘要。
4. 设置挂起重试间隔。
5. 返回 SUSPEND_CURRENT_QUEUE_A_MOMENT。
```

默认挂起重试间隔：

```text
5 秒
```

配置：

```yaml
cdc-sync:
  suspendCurrentQueueTimeMillis: 5000
```

### 20.2 不限制最大重试次数

第一版：

```text
不限制同一条失败消息最大重试次数。
不跳过失败消息。
不进入程序自定义死信流程。
一直重试，直到人工修复后成功。
```

核心原则：

```text
宁可阻塞当前顺序队列，也不跳过失败消息。
宁可人工介入修复，也不让目标表缺数据或乱序。
```

### 20.3 RETRYING 和 CONSUME_FAILED

消费第一次失败：

```text
status = RETRYING
```

如果失败持续超过阈值：

```text
status = CONSUME_FAILED
```

默认阈值：

```text
5 分钟
```

配置：

```yaml
cdc-sync:
  defaultConsumeFailureThresholdSeconds: 300

  topics:
    - topic: user_cdc_topic
      consumeFailureThresholdSeconds: 600
```

进入 `CONSUME_FAILED` 后：

```text
Consumer 不停止。
继续让 RocketMQ 顺序重试。
等待人工修复后自动恢复。
```

恢复成功后：

```text
status = RUNNING
last_error_message 不清空。
last_error_time 不清空。
通过 last_success_time > last_error_time 判断最近错误已恢复。
```

---

## 21. offset 管理规则

### 21.1 程序不主动管理 offset

第一版不支持：

```text
1. startConsumeTimestamp。
2. queueId + offset 配置。
3. 程序内 seek offset。
4. 自动按时间查 offset。
```

### 21.2 offset 由 RocketMQ 控制台人工管理

全量同步和增量衔接由人工通过 RocketMQ 控制台管理 ConsumerGroup offset。

上线流程：

```text
1. 启动 Debezium -> RocketMQ 链路，让增量消息持续进入 Topic。
2. 执行全量同步，例如 SQL / DataX / DTS。
3. 根据全量同步位点策略，在 RocketMQ 控制台手动调整 ConsumerGroup offset。
4. 启动本 CDC 同步程序。
5. 程序从 RocketMQ 当前设置好的 offset 正常消费。
```

### 21.3 offset 存在性检查

Topic Consumer 启动前需要检查：

```text
topic + consumerGroup 是否存在历史消费位点。
```

如果存在：

```text
启动 Consumer。
status = RUNNING。
```

如果不存在：

```text
不启动该 Topic Consumer。
status = OFFSET_NOT_FOUND。
last_error_message = 当前 ConsumerGroup 不存在历史消费位点，请先在 RocketMQ 控制台设置 offset。
```

### 21.4 OFFSET_NOT_FOUND 自动恢复

程序有统一恢复任务，每分钟扫描：

```text
OFFSET_NOT_FOUND
START_FAILED
```

对于 OFFSET_NOT_FOUND：

```text
1. 重新检查 topic + consumerGroup 是否存在 offset。
2. 如果仍不存在，保持 OFFSET_NOT_FOUND。
3. 如果已存在，重新校验配置并启动 Consumer。
4. 成功后 status = RUNNING。
```

默认检查间隔：

```yaml
cdc-sync:
  recoverableTopicCheckIntervalMillis: 60000
```

---

## 22. START_FAILED 处理

### 22.1 START_FAILED 场景

包括：

```text
1. RocketMQ Topic 不存在。
2. 无权限订阅。
3. subscribe 失败。
4. Consumer start 失败。
```

处理：

```text
该 Topic Consumer 不启动。
status = START_FAILED。
其他 Topic 不受影响。
```

### 22.2 START_FAILED 自动恢复

统一恢复任务每分钟重试。

流程：

```text
1. 重新校验配置。
2. 重新检查 offset。
3. 尝试创建并启动 Consumer。
4. 成功后 status = RUNNING。
5. 失败则保持最新失败状态。
```

如果失败原因变化：

```text
status 切换为当前最新失败原因。
```

例如：

```text
START_FAILED -> OFFSET_NOT_FOUND
OFFSET_NOT_FOUND -> START_FAILED
```

---

## 23. 状态表设计

### 23.1 状态表定位

状态表只记录当前状态，不记录历史事件。

第一版只做：

```text
cdc_sync_topic_status
```

不做：

```text
cdc_sync_queue_status
cdc_sync_topic_event_history
```

### 23.2 状态表存放位置

状态表放在固定管理库 / 默认 datasource 中。

配置：

```yaml
cdc-sync:
  statusDatasource: syncAdminDb
```

### 23.3 状态表由运维提前创建

程序不自动创建状态表。

但启动时必须校验状态表存在。

如果不存在：

```text
应用启动失败。
```

### 23.4 状态表字段

第一版建议字段：

```sql
CREATE TABLE `cdc_sync_topic_status` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `topic` varchar(255) NOT NULL,
  `datasource` varchar(128) DEFAULT NULL,
  `target_table` varchar(128) DEFAULT NULL,
  `primary_key` varchar(128) DEFAULT NULL,
  `consumer_group` varchar(255) DEFAULT NULL,
  `status` varchar(64) NOT NULL,
  `last_msg_id` varchar(128) DEFAULT NULL,
  `last_msg_key` varchar(512) DEFAULT NULL,
  `last_unique_key` varchar(512) DEFAULT NULL,
  `last_queue_id` int DEFAULT NULL,
  `last_queue_offset` bigint DEFAULT NULL,
  `last_success_time` datetime DEFAULT NULL,
  `last_error_time` datetime DEFAULT NULL,
  `last_error_message` text,
  `last_start_time` datetime DEFAULT NULL,
  `last_stop_time` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_topic` (`topic`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

注意：

```text
第一版不记录 running_instance。
第一版不记录 configVersion / configHash。
第一版不记录 status_message / remark。
```

非异常状态说明也写入 `last_error_message`。

### 23.5 状态枚举

第一版状态：

```text
RUNNING
STOPPED
CONFIG_INVALID
OFFSET_NOT_FOUND
START_FAILED
RETRYING
CONSUME_FAILED
```

含义：

```text
RUNNING：
  Topic Consumer 正常运行。

STOPPED：
  Topic 被 enabled=false 停止，或未启用。

CONFIG_INVALID：
  配置不合法，Consumer 未启动。

OFFSET_NOT_FOUND：
  ConsumerGroup offset 未准备好。

START_FAILED：
  RocketMQ Consumer 创建 / 订阅 / 启动失败。

RETRYING：
  消费失败，正在重试，但未超过失败阈值。

CONSUME_FAILED：
  消费失败持续超过阈值，需要人工关注。
```

### 23.6 状态优先级

第一版不定义全局状态优先级。

状态由当前流程最后处理结果决定。

---

## 24. 状态更新机制

### 24.1 状态表不参与业务事务

状态表更新不和业务表写入放在同一个事务中。

业务表事务决定 RocketMQ 消费结果。

状态表只是观测能力。

### 24.2 状态表更新失败

如果状态表写入失败：

```text
不影响业务表事务。
不影响 RocketMQ 消费结果。
只打日志和指标。
```

### 24.3 异步状态更新

状态表更新采用异步队列。

流程：

```text
消费线程
  -> 完成业务表事务
  -> 生成 TopicStatusUpdateEvent
  -> 投递到内存队列
  -> StatusUpdateWorker 批量刷新状态表
```

### 24.4 状态队列容量

队列容量通过 Apollo 配置，默认 10000。

```yaml
cdc-sync:
  statusUpdateQueueCapacity: 10000
```

### 24.5 队列满处理

如果状态更新队列满：

```text
消费线程最多阻塞等待 5 秒。
如果 5 秒仍无法入队，丢弃本次状态更新。
打日志。
记录指标。
不影响 RocketMQ 消费结果。
```

配置：

```yaml
cdc-sync:
  statusUpdateOfferTimeoutMillis: 5000
```

### 24.6 批量刷新

后台刷新方式：

```text
每 1 秒或满 100 条事件刷新一次。
```

配置：

```yaml
cdc-sync:
  statusFlushIntervalMillis: 1000
  statusFlushBatchSize: 100
```

### 24.7 同 Topic 事件合并

批量刷新时：

```text
同一个 Topic 在一个批次内出现多条状态事件，只保留最后一条。
```

状态表只关心当前状态，不做历史审计。

---

## 25. 指标监控

第一版接入 Micrometer。

建议指标：

```text
cdc_sync_consume_success_total{topic,datasource,targetTable}
cdc_sync_consume_failed_total{topic,datasource,targetTable}
cdc_sync_message_ignored_total{topic,op}
cdc_sync_consume_batch_size{topic}
cdc_sync_consume_cost_ms{topic}
cdc_sync_db_write_cost_ms{topic,datasource,targetTable,op}
cdc_sync_config_invalid_total{topic}
cdc_sync_offset_not_found_total{topic,consumerGroup}
cdc_sync_start_failed_total{topic,consumerGroup}
cdc_sync_status_update_failed_total
cdc_sync_status_update_dropped_total
cdc_sync_worker_status{topic,status}
```

指标标签不包含：

```text
uniqueKey
msgId
queueOffset
rawBody
errorMessage
```

原因：

```text
避免指标基数爆炸。
```

---

## 26. 日志规则

### 26.1 启动配置摘要日志

启动时打印配置摘要：

```text
appName
RocketMQ nameServer
RocketMQ aclEnabled
datasource 名称列表
Topic 数量
每个 Topic:
  topic
  enabled
  datasource
  targetTable
  primaryKey
  batchSize
  consumerGroup
  consumeThreadMin
  consumeThreadMax
```

敏感字段不打印明文：

```text
datasource.password
rocketmq.accessKey
rocketmq.secretKey
```

### 26.2 成功日志

成功消息不逐条打印，避免日志量过大。

### 26.3 忽略消息日志

对于：

```text
op=r
未知 op
tombstone
```

打印 INFO / WARN，包含：

```text
topic
consumerGroup
msgId
keys
queueId
queueOffset
op
uniqueKey
rawBody
```

### 26.4 失败日志

消费失败时打印 ERROR，包含：

```text
topic
consumerGroup
datasource
targetTable
primaryKey
uniqueKey
msgId
keys
queueId
queueOffset
op
batchSize
failedIndex
完整 rawBody
异常堆栈
```

第一版 raw body 不脱敏，完整打印。

风险：

```text
如果消息中包含手机号、邮箱、地址、token、证件号等敏感数据，会进入日志系统。
需要控制日志权限和保留周期。
```

---

## 27. SQL 超时和连接池

### 27.1 SQL 执行超时

数据库写入设置 SQL 执行超时时间。

全局配置，默认 30 秒：

```yaml
cdc-sync:
  sqlQueryTimeoutSeconds: 30
```

SQL 超时处理：

```text
当前批次事务回滚。
返回 SUSPEND_CURRENT_QUEUE_A_MOMENT。
RocketMQ 顺序重试。
```

### 27.2 连接池参数

使用 HikariCP。

配置策略：

```text
全局默认 + datasource 可覆盖。
```

示例：

```yaml
cdc-sync:
  datasourceDefaults:
    maximumPoolSize: 10
    minimumIdle: 2
    connectionTimeoutMs: 30000
    idleTimeoutMs: 600000
    maxLifetimeMs: 1800000

  datasources:
    syncDbB:
      pool:
        maximumPoolSize: 20
        minimumIdle: 5
```

---

## 28. 优雅停机

程序关闭或发布重启时使用：

```text
优雅停机 + 最大等待时间。
```

最大等待时间：

```text
Apollo 全局配置，默认 60 秒。
```

配置：

```yaml
cdc-sync:
  gracefulShutdownTimeoutMillis: 60000
```

流程：

```text
1. 收到 Spring shutdown 信号。
2. TopicSyncManager 标记 stopping=true。
3. 不再创建新的 Topic Consumer。
4. 停止各 Topic Consumer 拉取新消息。
5. 等待当前批次事务完成。
6. 当前批次成功：commit 后返回 SUCCESS。
7. 当前批次失败：rollback 后返回 SUSPEND_CURRENT_QUEUE_A_MOMENT。
8. 最多等待 60 秒。
9. 超时后强制 shutdown。
```

---

## 29. 全量同步与增量衔接

### 29.1 全量同步

目标表初始化数据由外部工具完成：

```text
SQL
DataX
DTS
其他离线同步工具
```

本程序只负责增量 CDC。

### 29.2 r 事件忽略

由于全量由外部工具负责，Debezium `op=r` 快照事件第一版忽略。

### 29.3 不停写场景

源表不能停写。

因此必须依赖人工进行位点衔接。

本程序不自动处理全量和增量位点对齐。

### 29.4 上线推荐流程

```text
1. 先启动 Debezium -> RocketMQ 链路。
2. 确认增量消息持续写入 RocketMQ。
3. 执行全量同步。
4. 根据全量同步开始 / 结束策略，在 RocketMQ 控制台设置 ConsumerGroup offset。
5. Apollo 中配置 Topic enabled=true。
6. 启动同步程序。
7. 程序检查 offset 存在后开始消费。
8. 观察状态表、日志和指标。
```

---

## 30. 配置校验流程

### 30.1 应用启动流程

```text
1. 加载 Apollo 配置。
2. 检查重复 Topic。
   - 如果重复，应用启动失败。
3. 初始化 RocketMQ 基础配置。
4. 初始化 datasource。
   - 某 datasource 初始化失败，不导致应用退出。
5. 校验 statusDatasource 和 cdc_sync_topic_status。
   - 状态表不存在，应用启动失败。
6. 遍历 Topic 配置。
7. enabled=false 或缺失：
   - status = STOPPED，不校验。
8. enabled=true：
   - 校验 datasource。
   - 校验 targetTable。
   - 校验 primaryKey 字段。
   - 校验 primaryKey 单字段唯一。
   - 检查 consumerGroup offset。
   - 创建并启动 RocketMQ Consumer。
9. 单个 Topic 失败不影响其他 Topic。
```

### 30.2 Topic 启动判断

```text
enabled=false:
  STOPPED

配置缺失:
  CONFIG_INVALID

datasource 不存在 / 初始化失败:
  CONFIG_INVALID

targetTable 不存在:
  CONFIG_INVALID

primaryKey 不存在:
  CONFIG_INVALID

primaryKey 非单字段唯一:
  CONFIG_INVALID

offset 不存在:
  OFFSET_NOT_FOUND

RocketMQ Consumer 启动失败:
  START_FAILED

全部通过:
  RUNNING
```

---

## 31. 消费主流程

伪流程：

```text
RocketMQ MessageListenerOrderly.consumeMessage(msgs, context)
  |
  |-- 获取 TopicSyncWorker 当前配置
  |
  |-- 开启目标 datasource 事务
  |
  |-- for msg in msgs 按顺序处理：
  |     |
  |     |-- UTF-8 解码 rawBody
  |     |-- 判断 tombstone，若是则忽略
  |     |-- 解析 JSON
  |     |-- 兼容 payload / root 两种结构
  |     |-- 读取 op / before / after
  |     |
  |     |-- op=c/u:
  |     |     |-- 校验 after 非空
  |     |     |-- 提取 after[primaryKey]
  |     |     |-- 校验 primaryKey 值非空
  |     |     |-- 字段值转换
  |     |     |-- 生成 / 获取缓存 upsert SQL
  |     |     |-- 执行 upsert
  |     |
  |     |-- op=d:
  |     |     |-- 校验 before 非空
  |     |     |-- 提取 before[primaryKey]
  |     |     |-- 校验 primaryKey 值非空
  |     |     |-- 生成 / 获取缓存 delete SQL
  |     |     |-- 执行 delete
  |     |
  |     |-- op=r:
  |     |     |-- 忽略
  |     |
  |     |-- unknown op:
  |           |-- WARN 并忽略
  |
  |-- 全部成功:
  |     |-- commit
  |     |-- 投递状态更新事件
  |     |-- return SUCCESS
  |
  |-- 任意异常:
        |-- rollback
        |-- 打完整 rawBody 错误日志
        |-- 投递状态更新事件
        |-- context.setSuspendCurrentQueueTimeMillis(5000)
        |-- return SUSPEND_CURRENT_QUEUE_A_MOMENT
```

---

## 32. 第一版风险与约束

### 32.1 每 Topic 一个 Consumer 的资源风险

预计 50～200 Topic 时，需要关注：

```text
1. Consumer 数量。
2. 线程数。
3. RocketMQ 连接和心跳。
4. 应用启动耗时。
5. 重平衡压力。
6. 指标数量。
7. 状态刷新压力。
```

第一版不限制 Topic 数量，但需要运维观察。

### 32.2 标识符安全风险

第一版默认信任 Apollo 和 Debezium 字段名。

动态 SQL 的表名、字段名不能通过 JDBC `?` 参数化。

后续应加强：

```text
1. 表名白名单校验。
2. 字段名正则校验。
3. 反引号转义。
4. 字段元数据校验。
```

### 32.3 raw body 日志敏感风险

第一版消费失败打印完整 raw body 且不脱敏。

风险：

```text
敏感字段进入日志系统。
```

需要控制：

```text
1. 日志访问权限。
2. 日志保留周期。
3. 后续脱敏策略。
```

### 32.4 不自动 offset 衔接风险

第一版依赖人工在 RocketMQ 控制台设置 offset。

如果人工设置错误，可能导致：

```text
1. 漏消费。
2. 重复消费。
3. 旧增量覆盖新全量。
4. 删除事件丢失。
```

需要上线流程严格执行。

### 32.5 不做字段转换风险

时间、decimal、json 等字段完全交给 JDBC/MySQL。

可能导致运行时报错并阻塞顺序队列。

### 32.6 状态表异步更新不强一致

状态表是观测能力。

可能出现：

```text
业务表已写入成功，但状态表更新延迟或失败。
```

这是第一版可接受行为。

---

## 33. 后续演进方向

后续可以考虑：

```text
1. Topic 分组 Consumer，降低 Consumer 数量。
2. 支持联合主键 / 联合唯一键。
3. 支持字段白名单 / 黑名单。
4. 支持字段映射。
5. 支持 schema 解析和类型转换。
6. 支持 startConsumeTimestamp。
7. 支持 queueId + offset 配置。
8. 支持 dry-run。
9. 支持管理接口：暂停、恢复、重启 Topic。
10. 支持 cdc_sync_queue_status。
11. 支持历史事件表。
12. 支持 raw body 脱敏。
13. 支持 PostgreSQL 方言。
14. 支持 soft delete。
15. 支持配置版本 / 配置快照记录。
16. 支持 datasource 修改热更新。
17. 支持 RocketMQ 连接配置热更新。
18. 支持 SQL 缓存最大容量。
19. 支持标识符严格校验。
20. 支持字段级默认值补齐。
```

---

## 34. 第一版最终结论

第一版核心方案可以总结为：

```text
这是一个以可靠性和可恢复性优先的 CDC 同步程序。

它不追求复杂转换，不追求自动修复，不跳过失败消息。
它依赖 RocketMQ 顺序消费保证队列内顺序，
依赖生产者按唯一键分队列保证业务主键顺序，
依赖 MySQL upsert/delete 保证落库幂等，
依赖 Apollo 管理 Topic 和 datasource 配置，
依赖状态表、日志、指标支撑运维，
依赖人工管理 RocketMQ offset 完成全量和增量衔接。
```

最关键的设计原则是：

```text
1. Topic 级隔离。
2. 队列内顺序。
3. 批内事务。
4. 失败不跳过。
5. 人工修复后原消息继续重试。
6. 状态观测不影响主链路。
7. 配置错误尽量局部化，不拖垮整个应用。
```
