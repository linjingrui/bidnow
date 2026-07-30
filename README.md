# BidNow — 在线竞拍平台

> 支持英式拍卖的在线竞拍平台，含 JWT 认证、Lua 原子出价、代理出价、防截杀、限流、缓存体系、消息队列异步落库。

---

## 技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 开发语言 |
| SpringBoot | 3.3.9 | 应用框架 |
| Spring AOP | — | 切面编程（限流注解） |
| MyBatis-Plus | 3.5.5 | ORM，含分页 + 乐观锁 |
| MySQL | 8.0 | 持久化存储 |
| Redis | 5.0 (Windows) | 缓存、原子出价、代理出价、限流、分布式锁 |
| RocketMQ | 4.9.8 | 异步消息、削峰 |
| jjwt | 0.12.3 | JWT 令牌生成与解析 |
| Spring Security Crypto | — | BCrypt 密码加密 |
| Maven | 3.9 | 依赖管理 |

---

## 项目结构

```
src/main/java/com/bidnow/bidnow/
├── BidnowApplication.java
├── config/
│   ├── MyBatisPlusConfig.java       # 分页 + 乐观锁插件
│   ├── RedisConfig.java             # Redis 序列化
│   ├── WebConfig.java               # 拦截器注册 + 静态资源映射
│   └── LoginInterceptor.java        # JWT 登录拦截器
├── controller/
│   ├── AuthController.java          # 注册 / 登录
│   ├── ItemController.java          # 拍品 CRUD + 上下架 + 我的拍品
│   ├── BidController.java           # 出价接口（含限流）
│   └── UploadController.java        # 图片上传
├── service/
│   ├── UserService.java
│   ├── ItemService.java
│   ├── BidService.java
│   ├── ProxyBidResolver.java        # 代理出价排名解析器
│   └── impl/
│       ├── UserServiceImpl.java     # BCrypt + JWT
│       ├── ItemServiceImpl.java
│       └── BidServiceImpl.java      # Lua 原子出价 + 代理出价
├── mapper/
│   └── ItemMapper.java
├── entity/
│   ├── User.java
│   └── Item.java                    # @Version 乐观锁
├── dto/
│   ├── RegisterRequest.java
│   ├── LoginRequest.java
│   ├── ItemCreateRequest.java
│   ├── ItemVO.java
│   └── BidRequest.java              # amount + 可选 maxAmount
├── mq/
│   ├── CacheDeleteConsumer.java     # 异步删缓存
│   └── BidSuccessConsumer.java      # 异步更新 MySQL（乐观锁）
└── common/
    ├── Result.java                   # 统一响应体
    ├── BizException.java             # 业务异常
    ├── CacheData.java                # 逻辑过期包装
    ├── GlobalExceptionHandler.java
    ├── JwtUtil.java                  # JWT 生成 / 解析
    ├── RateLimit.java                # @RateLimit 自定义注解
    └── RateLimitAspect.java          # 限流 AOP 切面

resources/
└── lua/
    ├── bid.lua                       # 原子出价（含防截杀）
    └── rate_limit.lua                # ZSET 滑动窗口限流
```

---

## 核心设计

| 模块 | 要点 |
|------|------|
| **JWT 认证** | BCrypt 密码加密，jjwt 生成/解析令牌，LoginInterceptor 拦截 `/api/**`，AuthController 除外 |
| **缓存策略** | Cache-Aside + 逻辑过期防击穿 + 空值缓存防穿透 + MQ 异步删缓存 |
| **原子出价** | bid.lua 在 Redis 单线程执行"校验价格+校验时间+防截杀+更新价格" |
| **代理出价** | Redis Hash 存各用户上限，ProxyBidResolver 降序排名，当前价 = min(第二名上限+加价, 第一名上限) |
| **防截杀** | 最后 30 秒有人出价 → 自动延长结束时间 30 秒 |
| **乐观锁** | @Version + OptimisticLockerInnerInterceptor，出价写入 MySQL 防并发覆盖 |
| **限流** | @RateLimit 注解 + AOP 切面 + rate_limit.lua ZSET 滑动窗口，出价接口每秒 3 次 |
| **异步解耦** | 出价/编辑/删除 → RocketMQ → 消费者，接口不阻塞 |
| **状态流转** | DRAFT → publish → ACTIVE → bid / close → ENDED |
| **图片上传** | POST /api/upload，按日期分目录存储，映射 /uploads/** 静态资源 |

---

## API 文档

### 认证

| 方法 | URL | 说明 |
|------|-----|------|
| POST | `/api/auth/register` | 注册（返回 userId） |
| POST | `/api/auth/login` | 登录（返回 JWT token） |

### 拍品

| 方法 | URL | 说明 |
|------|-----|------|
| POST | `/api/items` | 创建拍品（默认 DRAFT） |
| GET | `/api/items?pageNum=1&pageSize=10&category=收藏品` | 分页列表（无需登录） |
| GET | `/api/items/{id}` | 拍品详情（带缓存） |
| GET | `/api/items/my` | 我的拍品列表（按 JWT 筛选） |
| PUT | `/api/items/{id}` | 编辑拍品（仅 DRAFT） |
| DELETE | `/api/items/{id}` | 删除拍品（拍卖中不可删） |
| POST | `/api/items/{id}/publish` | 上架（DRAFT → ACTIVE） |
| POST | `/api/items/{id}/close` | 下架（ACTIVE → ENDED） |

### 出价

| 方法 | URL | 说明 |
|------|-----|------|
| POST | `/api/items/{id}/bid` | 出价 `{amount, maxAmount?}`，含限流 3次/秒 |

### 上传

| 方法 | URL | 说明 |
|------|-----|------|
| POST | `/api/upload` | 图片上传（multipart/form-data），返回 `{url}` |

---

## 本地运行

### 前置

```
D:\Redis\redis-server.exe
D:\rocketmq\bin\mqnamesrv.cmd
D:\rocketmq\bin\mqbroker.cmd -n localhost:9876 autoCreateTopicEnable=true
```

### 启动

IDEA 运行 `BidnowApplication.main()` 或 `mvn spring-boot:run`。

### 关闭

```bash
D:\rocketmq\bin\mqshutdown.cmd broker
D:\rocketmq\bin\mqshutdown.cmd namesrv
```

> 异常退出导致 Broker 起不来时，删除 `C:\Users\linji\store\abort` 后重试。
