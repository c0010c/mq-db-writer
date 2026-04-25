# 计划 05：Debezium 解析与事件路由

## 1. 目标
统一解析 Debezium Envelope 与简化 JSON，输出标准事件模型并路由到 `c/u/d/r/unknown/tombstone`。

## 2. 边界
- 只读取 `before/after/op`。
- UTF-8 解码。
- `payload=null`、空 body、空白 body 视为 tombstone。

## 3. 代码落点（建议）
- `.../parser/DebeziumMessageParser.java`
- `.../parser/model/ParsedEvent.java`
- `.../parser/model/OpType.java`
- `.../parser/FieldValueNormalizer.java`

## 4. 实施步骤
1. 先解码 raw body，保存原文用于失败日志。
2. 解析 JSON：优先读 `root.payload`，否则读 root。
3. 路由规则：
   - `c/u` -> 必须 `after` 非空且有 primaryKey。
   - `d` -> 必须 `before` 非空且有 primaryKey。
   - `r` -> ignored。
   - unknown -> warn + ignored。
   - op 缺失/空 -> 抛异常触发重试。
4. 字段值规范化：object/array 序列化为 JSON 字符串，其余保持原值。
5. 单测覆盖：标准 envelope、简化格式、tombstone、unknown op、缺失 op。

## 5. 验证命令
- `./mvnw -q -Dtest=*DebeziumMessageParser* test`

## 6. DoD
- 解析器可稳定识别两种输入结构。
- 所有边界分支均有单测。
