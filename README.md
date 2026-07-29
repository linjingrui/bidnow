# BidNow — 在线竞拍平台

> 支持英式拍卖与荷兰式拍卖的在线竞拍平台。已实现 Redis 缓存体系、RocketMQ 异步消息、Lua 原子出价、乐观锁并发控制。

---

## 技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 开发语言 |
| SpringBoot | 3.3.9 | 应用框架 |
| MyBatis-Plus | 3.5.5 | ORM，含分页插件 + 乐观锁插件 |
| MySQL | 8.0 | 持久化存储 |
| Redis | 5.0 (Windows) | 缓存、原子出价、分布式锁 |
| RocketMQ | 4.9.8 | 异步消息、削峰填谷 |
| Maven | 3.9 | 依赖管理 |

---

## 项目结构

```
src/main/java/com/bidnow/bidnow/
├── BidnowApplication.java
├── config/
│   ├── MyBatisPlusConfig.java     # 分页插件 + 乐观锁插件
│   └── RedisConfig.java           # Redis 序列化配置
├── controller/
│   ├── ItemController.java        # 拍品 CRUD + 上下架
│   └── BidController.java         # 出价接口
├── service/
│   ├── ItemService.java
│   ├── BidService.java            # 出价服务（Lua 原子校验）
│   └── impl/
│       ├── ItemServiceImpl.java
│       └── BidServiceImpl.java
├── mapper/
│   ├── UserMapper.java
│   └── ItemMapper.java
├── entity/
│   ├── User.java
│   └── Item.java                  # @Version 乐观锁
├── dto/
│   ├── ItemCreateRequest.java
│   ├── ItemVO.java
│   └── BidRequest.java
├── mq/
│   ├── CacheDeleteConsumer.java   # 异步删缓存
│   └── BidSuccessConsumer.java    # 异步更新 MySQL（乐观锁）
└── common/
    ├── Result.java
    ├── BizException.java
    ├── CacheData.java             # 逻辑过期包装类
    └── GlobalExceptionHandler.java

resources/
└── lua/
    └── bid.lua                    # 原子出价 Lua 脚本
```

---

## 核心设计

| 模块 | 要点 |
|------|------|
| **缓存策略** | Cache-Aside + 逻辑过期防击穿 + 空值缓存防穿透 + MQ 异步删缓存 |
| **原子出价** | Lua 脚本在 Redis 单线程执行"校验>更新"，防并发超卖 |
| **乐观锁** | @Version + OptimisticLockerInnerInterceptor，出价写入 MySQL 防并发覆盖 |
| **异步解耦** | 出价/编辑/删除 → MQ → 消费者处理，接口不阻塞 |
| **状态流转** | DRAFT → publish → ACTIVE → bid / close → ENDED |

---

## 数据库表

### item（拍品表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键自增 |
| seller_id | BIGINT | 卖家 ID |
| title | VARCHAR(128) | 标题 |
| start_price | DECIMAL | 起拍价 |
| current_price | DECIMAL | 当前价 |
| status | VARCHAR(16) | DRAFT / ACTIVE / ENDED / SOLD |
| auction_type | VARCHAR(16) | ENGLISH / DUTCH |
| version | INT | 乐观锁版本号 |

---

## API 文档

| 方法 | URL | 说明 |
|------|-----|------|
| POST | `/api/items` | 创建拍品（默认 DRAFT） |
| GET | `/api/items?pageNum=1&pageSize=10&category=收藏品` | 分页列表 |
| GET | `/api/items/{id}` | 拍品详情（带缓存） |
| PUT | `/api/items/{id}` | 编辑拍品（仅 DRAFT） |
| DELETE | `/api/items/{id}` | 删除拍品（仅 DRAFT） |
| POST | `/api/items/{id}/publish` | 上架（DRAFT → ACTIVE） |
| POST | `/api/items/{id}/bid` | 出价（需 ACTIVE） |
| POST | `/api/items/{id}/close` | 结束拍卖（ACTIVE → ENDED） |

---

## 本地运行

### 前置条件

确保以下服务在运行：

```
D:\Redis\redis-server.exe
D:\rocketmq\bin\mqnamesrv.cmd
D:\rocketmq\bin\mqbroker.cmd -n localhost:9876 autoCreateTopicEnable=true
```

### 启动应用

IDEA 运行 `BidnowApplication.main()` 或 `mvn spring-boot:run`。

### 关闭服务

```bash
D:\rocketmq\bin\mqshutdown.cmd broker
D:\rocketmq\bin\mqshutdown.cmd namesrv
```

> 异常退出导致下次 Broker 起不来时，删除用户目录下的 `store` 文件夹后重试。
