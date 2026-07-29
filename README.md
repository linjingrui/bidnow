# BidNow — 在线竞拍平台

> 支持英式拍卖与荷兰式拍卖的在线竞拍平台。阶段一完成项目骨架搭建 + Redis 缓存集成。

---

## 技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 开发语言 |
| SpringBoot | 3.3.9 | 应用框架 |
| MyBatis-Plus | 3.5.5 | ORM，简化数据库操作 |
| MySQL | 8.0 | 持久化存储 |
| Redis | 5.0 (Windows) | 缓存、分布式锁、限流 |
| RocketMQ | 4.9.8 | 异步消息、削峰填谷 |
| Maven | 3.9 | 依赖管理 |

---

## 项目结构

```
src/main/java/com/bidnow/bidnow/
├── BidnowApplication.java        # 启动类
├── config/
│   ├── MyBatisPlusConfig.java    # 分页插件
│   └── RedisConfig.java          # Redis序列化配置
├── controller/
│   └── ItemController.java       # 拍品接口（RESTful）
├── service/
│   ├── ItemService.java          # 接口
│   └── impl/
│       └── ItemServiceImpl.java  # 业务逻辑 + 缓存策略
├── mapper/
│   ├── UserMapper.java           # 用户数据访问
│   └── ItemMapper.java           # 拍品数据访问
├── entity/
│   ├── User.java                 # 用户实体
│   └── Item.java                 # 拍品实体
├── dto/
│   ├── ItemCreateRequest.java    # 创建/编辑请求体
│   └── ItemVO.java               # 拍品视图对象（返回前端）
└── common/
    ├── Result.java               # 统一返回格式
    ├── BizException.java         # 业务异常
    ├── CacheData.java            # 缓存包装类（逻辑过期用）
    └── GlobalExceptionHandler.java # 全局异常处理
```

---

## 已实现的缓存策略

| 策略 | 说明 |
|------|------|
| **Cache-Aside** | 查详情先走 Redis，未命中再查 MySQL |
| **缓存穿透防护** | 不存在的数据写入空标记，短 TTL 防止恶意请求穿透到数据库 |
| **逻辑过期防击穿** | 缓存不设物理 TTL，通过逻辑过期时间 + SETNX 互斥锁控制缓存重建 |
| **缓存一致性** | 更新/删除后清除缓存，下次查询自动重建 |

---

## 数据库表

### item（拍品表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键自增 |
| seller_id | BIGINT | 卖家ID |
| title | VARCHAR(128) | 标题 |
| start_price | DECIMAL | 起拍价 |
| current_price | DECIMAL | 当前价 |
| status | VARCHAR(16) | DRAFT/PENDING/ACTIVE/ENDED/SOLD |
| auction_type | VARCHAR(16) | ENGLISH/DUTCH |
| version | INT | 乐观锁版本号 |

---

## API 文档

| 方法 | URL | 说明 |
|------|-----|------|
| POST | `/api/items` | 创建拍品（状态默认 DRAFT） |
| GET | `/api/items?pageNum=1&pageSize=10&category=收藏品` | 分页列表 |
| GET | `/api/items/{id}` | 拍品详情（带缓存） |
| PUT | `/api/items/{id}` | 编辑拍品（仅 DRAFT 状态） |
| DELETE | `/api/items/{id}` | 删除拍品（仅 DRAFT 状态） |

---

## 本地运行

### 前置条件

确保以下服务在运行：

```
D:\Redis\redis-server.exe          # 窗口1
D:\rocketmq\bin\mqnamesrv.cmd      # 窗口2
D:\rocketmq\bin\mqbroker.cmd -n localhost:9876 autoCreateTopicEnable=true  # 窗口3
```

### 启动应用

```bash
mvn spring-boot:run
```

或 IDEA 直接运行 `BidnowApplication.main()`。

### 测试

```bash
# 创建拍品
curl -X POST http://localhost:8080/api/items \
  -H "Content-Type: application/json" \
  -d '{"title":"古董相机","description":"徕卡M3","category":"收藏品","startPrice":5000,"auctionType":"ENGLISH","startTime":"2026-08-01T10:00:00","endTime":"2026-08-03T10:00:00","bidIncrement":100}'

# 查询详情（第二次走缓存）
curl http://localhost:8080/api/items/1
```
