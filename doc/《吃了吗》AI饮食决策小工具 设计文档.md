# 《吃了吗》AI饮食决策小工具 设计文档（V1.0）

> 基于 PRD V2.0 编写，2026-06-05

---

## 一、文档概述

### 1.1 文档目的

本文档基于《吃了吗》PRD V2.0 进行技术设计，涵盖系统架构、模块拆解、数据库设计、接口协议、AI集成策略、安全方案、部署架构等，为开发实施提供完整技术蓝图。

### 1.2 适用范围

- 后端：Spring Boot 4.0 + JDK 17 + MyBatis-Plus + MySQL 8.0 + Redis 7.x
- 前端：Vue 3 + Vite + Vant UI + Pinia + Axios
- 移动端H5优先，PC自适应

### 1.3 设计原则

| 原则 | 说明 |
|------|------|
| 轻量优先 | 不过度设计，MVP阶段满足20次/人/日推荐+100DAU即可 |
| 降级兜底 | 所有AI依赖路径均设降级策略，保证基本可用 |
| 数据隔离 | 用户数据严格隔离，基于JWT userId做权限校验 |
| 成本可控 | 缓存复用+次数限制+模型降级，目标月成本≤200元 |

---

## 二、系统架构设计

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────┐
│                    Nginx (反向代理)                     │
│  - 静态资源托管 (Vue3 dist/)                            │
│  - /api/* 反向代理至 Spring Boot                       │
│  - SSL终止                                            │
│  - Gzip压缩                                           │
└───────────────┬─────────────────────────────────────┘
                │
┌───────────────▼─────────────────────────────────────┐
│              Spring Boot 4.0 (8080)                   │
│                                                       │
│  ┌─────────────────────────────────────────────────┐ │
│  │           Interceptor Chain                      │ │
│  │  JwtInterceptor → RateLimitInterceptor → Controller│
│  └─────────────────────────────────────────────────┘ │
│                                                       │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌─────────┐ │
│  │MealSvc   │ │QaSvc     │ │FavoriteSvc│ │UserSvc  │ │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬────┘ │
│       └──────────────┴───────────┴────────────┘      │
│                          │                            │
│  ┌───────────────────────▼─────────────────────────┐ │
│  │              AiProxyService (AI调度层)            │ │
│  │  - 模型路由 (Qwen-Plus → DeepSeek-V3)            │ │
│  │  - Prompt管理 + JSON解析校验                     │ │
│  │  - 超时/重试/缓存/降级                            │ │
│  └─────────────────────────────────────────────────┘ │
│                          │                            │
│       ┌──────────────────┼──────────────────┐        │
│       ▼                  ▼                  ▼        │
│  ┌─────────┐    ┌─────────────┐    ┌──────────────┐ │
│  │ MySQL 8 │    │  Redis 7.x  │    │ AI大模型API   │ │
│  │(主存储) │    │(缓存/限流)  │    │(外部HTTP)    │ │
│  └─────────┘    └─────────────┘    └──────────────┘ │
└─────────────────────────────────────────────────────┘
```

### 2.2 技术选型版本锁定

| 组件 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 4.0.6 | 后端框架 |
| JDK | 17 | 运行环境 |
| MyBatis-Plus | 3.5.x | ORM |
| MySQL | 8.0 | 持久化存储 |
| Redis | 7.x | 缓存/限流/会话 |
| Vue | 3.4+ | 前端框架 |
| Vite | 5.x | 构建工具 |
| Vant | 4.x | 移动端UI组件库 |
| Pinia | 2.x | 状态管理 |
| Vue Router | 4.x | 路由 |

### 2.3 模块依赖关系

```
eat-server/
├── eat-common/           # 公共模块：统一返回、异常定义、工具类
│   └── (无依赖)
├── eat-security/         # 安全模块：JWT、限流拦截器、敏感词过滤
│   └── depends: eat-common
├── eat-user/             # 用户模块：登录、偏好
│   └── depends: eat-common, eat-security
├── eat-meal/             # 推荐模块：AI推荐、收藏
│   └── depends: eat-common, eat-user
├── eat-qa/               # 问答模块：AI问答
│   └── depends: eat-common, eat-user
└── eat-ai/               # AI代理模块：统一AI调用
    └── depends: eat-common
```

> MVP阶段可不拆分微服务，上述结构以**包内模块**方式组织，便于后续拆分。

---

## 三、前端设计

### 3.1 组件树

```
App.vue
├── Layout.vue (底部Tab布局)
│   ├── TabBar.vue (公共底部导航栏)
│   └── <RouterView>
│       ├── HomePage.vue (首页/决策页) `/home`
│       │   ├── SloganBanner.vue (顶部Slogan)
│       │   ├── PeriodSelector.vue (时段选择组)
│       │   ├── PreferenceTags.vue (口味/忌口/需求/场景标签)
│       │   ├── GenerateButton.vue (主操作按钮+loading)
│       │   ├── MealCard.vue ×3 (方案卡片)
│       │   │   ├── MealItemList.vue (菜品列表)
│       │   │   └── MealMeta.vue (耗时+热量)
│       │   └── LoadingSkeleton.vue (骨架屏)
│       │
│       ├── QaPage.vue (问答页) `/qa`
│       │   ├── ChatBubble.vue ×N (对话气泡)
│       │   ├── QuickQuestions.vue (快捷问题标签)
│       │   └── ChatInput.vue (输入框+字数统计)
│       │
│       ├── FavoritesPage.vue (收藏页) `/favorites`
│       │   ├── DateGroup.vue ×N (日期分组)
│       │   │   └── FavoriteCard.vue ×N (收藏卡片)
│       │   └── EmptyState.vue (空状态)
│       │
│       ├── ProfilePage.vue (个人中心) `/profile`
│       │   ├── UserInfo.vue (脱敏手机号+统计)
│       │   └── MenuList.vue (设置菜单项)
│       │
│       └── LoginPage.vue (登录页) `/login`
│           ├── PhoneInput.vue (手机号输入)
│           └── SmsCodeInput.vue (验证码输入+倒计时)
```

### 3.2 路由设计

```javascript
// router/index.js
const routes = [
  {
    path: '/',
    component: Layout,
    redirect: '/home',
    children: [
      { path: 'home',      component: () => import('@/views/HomePage.vue') },
      { path: 'qa',        component: () => import('@/views/QaPage.vue') },
      { path: 'favorites', component: () => import('@/views/FavoritesPage.vue'), meta: { requiresAuth: true } },
      { path: 'profile',   component: () => import('@/views/ProfilePage.vue'), meta: { requiresAuth: true } },
    ]
  },
  { path: '/login', component: () => import('@/views/LoginPage.vue') },
];

// 路由守卫逻辑:
// - /favorites, /profile: 需登录，未登录跳转/login
// - /home, /qa: 无需登录可浏览，但收藏/问答操作时检查登录态
```

### 3.3 Pinia 状态管理

```javascript
// stores/user.js
{
  state: {
    token: '',            // JWT Token (持久化到localStorage)
    userId: null,
    phone: '',            // 脱敏展示
    isLoggedIn: false
  },
  actions: {
    login(phone, code),
    logout(),             // 清除token + 状态重置
    fetchProfile(),
    checkAuth()           // token过期检查
  }
}

// stores/preference.js
{
  state: {
    taste: '',            // 默认口味
    taboos: [],           // 默认忌口
    goal: '',             // 默认目标
    scene: ''             // 默认场景
  },
  actions: {
    fetchPreference(),
    updatePreference(payload)
  }
}
```

### 3.4 Axios 拦截器设计

```
请求拦截器:
  1. 添加 Authorization: Bearer {token}
  2. 添加 X-Request-Id (UUID，用于日志追踪)

响应拦截器:
  1. code=200: 正常返回 response.data
  2. code=401: 清除token → 记录当前路由 → 跳转/login → 登录后回跳
  3. code=429: toast提示"操作太频繁"，解析Retry-After头
  4. code=500/503: toast提示统一错误信息
  5. 网络错误: toast提示"网络连接失败，请检查网络"
  6. 超时(>20s): 自动终止，toast提示"请求超时"
```

### 3.5 SSE 流式接收设计

```
推荐接口和问答接口采用SSE (Server-Sent Events) 流式传输:

前端:
  - 使用 EventSource 或 fetch + ReadableStream 接收
  - 推荐页: 方案逐条渲染，每条输出完即时展示卡片
  - 问答页: 打字机效果逐字追加

SSE事件类型:
  - event: "plan"    data: {方案JSON}          → 推荐场景，输出一个完整方案
  - event: "token"   data: "文本片段"          → 问答场景，逐token追加
  - event: "done"    data: {完整结果摘要}       → 流结束
  - event: "error"   data: {code, message}     → 流异常中断
```

---

## 四、后端设计

### 4.1 模块分层结构

```
com.eat
├── EatApplication.java                # 启动类
├── common/
│   ├── R.java                         # 统一响应体 {code, message, data}
│   ├── ResultCode.java                # 响应码枚举 (200, 400, 401, 429, 500)
│   ├── GlobalExceptionHandler.java    # @RestControllerAdvice 全局异常处理
│   └── utils/
│       ├── JwtUtil.java               # JWT签发/校验
│       ├── PhoneUtil.java             # 手机号加密(AES) + 脱敏
│       ├── SensitiveFilter.java       # 敏感词过滤
│       └── SseEmitterUtil.java        # SSE推送工具
├── config/
│   ├── RedisConfig.java               # Redis序列化配置
│   ├── MyBatisPlusConfig.java         # 分页插件
│   ├── WebMvcConfig.java              # 拦截器注册
│   └── RestTemplateConfig.java        # HTTP客户端(用于AI调用)
├── interceptor/
│   ├── JwtInterceptor.java            # JWT校验拦截器 (/api/v1/** 除public路径)
│   └── RateLimitInterceptor.java      # 基于Redis的滑动窗口限流
├── controller/
│   ├── UserController.java            # 用户API
│   ├── MealController.java            # 推荐API
│   ├── QaController.java              # 问答API
│   └── FavoriteController.java        # 收藏API
├── service/
│   ├── UserService.java               # 用户业务逻辑
│   ├── MealService.java               # 推荐业务逻辑
│   ├── QaService.java                 # 问答业务逻辑
│   ├── FavoriteService.java           # 收藏业务逻辑
│   └── AiProxyService.java            # AI统一调用+路由+降级
├── mapper/
│   ├── UserMapper.java
│   ├── UserPreferenceMapper.java
│   ├── FavoriteMapper.java
│   └── QaRecordMapper.java
├── entity/
│   ├── User.java
│   ├── UserPreference.java
│   ├── Favorite.java
│   └── QaRecord.java
└── dto/
    ├── request/
    │   ├── SendCodeRequest.java       # {phone}
    │   ├── LoginRequest.java          # {phone, code}
    │   ├── MealGenerateRequest.java   # {period, taste, taboos, goal, scene}
    │   ├── MealRegenerateRequest.java # {planType, excludeTitle}
    │   ├── QaAskRequest.java          # {question, sessionId?}
    │   └── PreferenceUpdateRequest.java
    └── response/
        ├── LoginResponse.java         # {token, userId, phone}
        ├── MealPlanResponse.java      # {planType, title, items[], estTime, calorieRange}
        ├── MealResultResponse.java    # {period, plans[]}
        ├── QaReplyResponse.java       # {answer, sessionId}
        └── FavoriteListResponse.java  # {dateGroups[]}
```

### 4.2 Service 核心逻辑设计

#### 4.2.1 MealService - 饮食推荐

```
generateMeal(userId, request):
  1. 构造缓存Key: "ai:cache:{period}:{taste}:{taboos}:{goal}:{scene}"
  2. 查询Redis缓存，命中则直接返回
  3. 检查当日推荐次数 (rate:meal:{userId})，≥20次返回错误
  4. 调用 AiProxyService.recommend(request) 获取AI结果
  5. INCR当日次数计数器 (TTL=当天剩余秒数)
  6. 缓存结果到Redis (TTL=1h)
  7. 返回结果

regenerateMeal(userId, request):
  1. 同generateMeal逻辑，但prompt中增加"排除方案: {excludeTitle}"
  2. 不增加次数计数器 (替换刷新，不计为新推荐)
```

#### 4.2.2 QaService - 饮食问答

```
ask(userId, sessionId, question):
  1. 敏感词过滤 (SensitiveFilter.check(question))
     拦截 → 直接返回安全提示文案，不调AI，不记录
  2. 检查当日问答次数 (rate:qa:{userId})，≥30次返回错误
  3. 调用 AiProxyService.qa(question, context)
     context: 取最近5条问答作为上下文传入
  4. INCR当日次数计数器
  5. 保存问答记录到MySQL
  6. 更新Redis热数据列表 (qa:recent:{userId}, 保留最近20条)
  7. 返回AI回复

getHistory(userId):
  1. 优先从Redis (qa:recent:{userId}) 取最近20条
  2. Redis未命中 → 查MySQL LIMIT 20
  3. 返回
```

#### 4.2.3 FavoriteService - 收藏管理

```
addFavorite(userId, mealPlan):  → 写入MySQL + 更新Redis最近收藏
removeFavorite(userId, id):     → 软删除 (is_deleted=1) + 更新Redis
getFavorites(userId, page):     → Redis优先(7天内) → MySQL
```

#### 4.2.4 AiProxyService - AI统一代理

```
核心职责:
  1. Prompt模板管理（从配置文件加载）
  2. 模型路由：默认qwen-plus，失败/超时自动切deepseek-chat
  3. 超时控制：连接5s，读取15s
  4. 重试策略：5xx错误重试1次（间隔1s），4xx不重试
  5. JSON解析校验：AI返回必须符合Schema，解析失败返回兜底
  6. 降级兜底：AI不可用 → 返回静态方案
  7. SSE流式输出：支持flush流式写入

recommend(request):
  1. 构建UserMessage: 填充Prompt模板(period, taste, taboos, goal, scene)
  2. 构建SystemMessage: 从配置加载角色设定+JSON Schema约束
  3. POST {AI_URL}/chat/completions (stream=true)
  4. 解析SSE流 → 每完成一个方案emit SSE事件到前端
  5. 解析失败 → 记录日志 → 返回兜底方案

qa(question, contextMessages):
  1. 构建消息列表: [SystemMessage, ...contextMessages, UserMessage(question)]
  2. POST {AI_URL}/chat/completions (stream=true)
  3. 逐token转发SSE到前端
  4. 末尾自动追加免责声明

getFallback(period):
  1. 从配置文件/枚举类中获取对应时段的3套预设方案
  2. 直接返回（不调AI）
```

### 4.3 拦截器链设计

```
请求进入 → JwtInterceptor → RateLimitInterceptor → Controller

JwtInterceptor (order=1):
  - 排除路径: /api/v1/user/send-code, /api/v1/user/login, /api/v1/meal/fallback
  - 从Header取token → 校验有效性 → 解析userId → 写入ThreadLocal

RateLimitInterceptor (order=2):
  - 基于(userId或IP) + API路径做滑动窗口限流
  - 推荐接口: 5次/分钟
  - 问答接口: 10次/分钟
  - 发送验证码: 1次/60秒
  - 使用Redis ZSET实现滑动窗口
```

### 4.4 兜底方案配置

```yaml
# application.yml
eat:
  fallback:
    meals:
      breakfast:
        - type: quick
          title: "经典快手早餐"
          items: ["牛奶", "全麦面包", "水煮蛋"]
          estTime: "约5分钟"
          calorieRange: "约350-400千卡"
        - type: home
          title: "营养均衡早餐"
          items: ["豆浆", "包子", "水果沙拉"]
          estTime: "约15分钟"
          calorieRange: "约450-500千卡"
        - type: comfort
          title: "暖胃舒心早餐"
          items: ["燕麦粥", "坚果", "酸奶"]
          estTime: "约10分钟"
          calorieRange: "约300-350千卡"
      lunch:
        # ...同上结构,具体菜品见PRD 9.2节
      dinner:
        # ...
      snack:
        # ...
```

---

## 五、数据库详细设计

### 5.1 ER图

```
┌──────────────┐       ┌────────────────────┐
│   eat_user   │       │ eat_user_preference│
│──────────────│       │────────────────────│
│ id (PK)      │◄──────│ user_id (FK, UNIQUE)│
│ phone        │       │ default_taste      │
│ nickname     │       │ default_taboos     │
│ avatar_url   │       │ default_goal       │
│ created_at   │       │ default_scene      │
│ updated_at   │       │ updated_at         │
└──────┬───────┘       └────────────────────┘
       │
       │ 1:N
       ▼
┌──────────────┐       ┌────────────────────┐
│ eat_favorite │       │   eat_qa_record    │
│──────────────│       │────────────────────│
│ id (PK)      │       │ id (PK)           │
│ user_id (FK) │       │ user_id (FK)       │
│ meal_period  │       │ session_id        │
│ plan_type    │       │ question          │
│ plan_title   │       │ answer            │
│ plan_content │       │ is_sensitive      │
│ plan_items   │       │ created_at        │
│ est_time     │       └────────────────────┘
│ calorie_range│
│ is_deleted   │
│ created_at   │
└──────────────┘
```

### 5.2 建表SQL

数据库名: `eat_db`，字符集: `utf8mb4`，排序规则: `utf8mb4_unicode_ci`

```sql
-- 001_create_user.sql
CREATE TABLE eat_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    phone       VARCHAR(50)  NOT NULL UNIQUE COMMENT '手机号AES加密存储',
    nickname    VARCHAR(50)  DEFAULT '' COMMENT '用户昵称（预留）',
    avatar_url  VARCHAR(255) DEFAULT '' COMMENT '头像URL（预留）',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 002_create_user_preference.sql
CREATE TABLE eat_user_preference (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL UNIQUE COMMENT '关联用户ID',
    default_taste   VARCHAR(50)  DEFAULT '' COMMENT '默认口味: light/heavy/sweet_sour/spicy/""',
    default_taboos  VARCHAR(255) DEFAULT '' COMMENT '默认忌口, 逗号分隔: cilantro/allium/seafood/lactose/nuts',
    default_goal    VARCHAR(50)  DEFAULT '' COMMENT '默认目标: diet/bulk/stomach/crave/""',
    default_scene   VARCHAR(50)  DEFAULT '' COMMENT '默认场景: quick/serious/takeout/home/""',
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES eat_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户偏好表';

-- 003_create_favorite.sql
CREATE TABLE eat_favorite (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT NOT NULL COMMENT '关联用户ID',
    meal_period   VARCHAR(10) NOT NULL COMMENT '用餐时段: breakfast/lunch/dinner/snack',
    plan_type     VARCHAR(20) NOT NULL COMMENT '方案类型: quick/home/comfort',
    plan_title    VARCHAR(100) NOT NULL COMMENT '方案标题',
    plan_content  TEXT NOT NULL COMMENT '方案完整内容JSON',
    plan_items    TEXT COMMENT '菜品列表JSON: ["菜品1","菜品2"]',
    est_time      VARCHAR(20) COMMENT '预估耗时',
    calorie_range VARCHAR(30) COMMENT '热量区间',
    is_deleted    TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除: 0正常 1已删除',
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_date (user_id, created_at DESC),
    FOREIGN KEY (user_id) REFERENCES eat_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏表';

-- 004_create_qa_record.sql
CREATE TABLE eat_qa_record (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT NOT NULL COMMENT '关联用户ID',
    session_id    VARCHAR(64) NOT NULL COMMENT '会话ID (UUID)',
    question      VARCHAR(500) NOT NULL COMMENT '用户问题',
    answer        TEXT NOT NULL COMMENT 'AI回复',
    is_sensitive  TINYINT(1) NOT NULL DEFAULT 0 COMMENT '敏感词拦截: 0正常 1拦截',
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_session (user_id, session_id),
    INDEX idx_user_time (user_id, created_at DESC),
    FOREIGN KEY (user_id) REFERENCES eat_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问答记录表';
```

### 5.3 索引策略说明

| 表 | 索引 | 用途 |
|------|------|------|
| eat_user | `idx_phone` | 登录时按手机号查用户 |
| eat_favorite | `idx_user_date (user_id, created_at DESC)` | 收藏列表按时间分组查询 |
| eat_qa_record | `idx_user_session (user_id, session_id)` | 按会话查历史记录 |
| eat_qa_record | `idx_user_time (user_id, created_at DESC)` | 按时间倒序查最近记录 |

### 5.4 Redis Key 设计详解

| Key | 类型 | TTL | 数据结构 |
|-----|------|-----|----------|
| `sms:code:{phone}` | String | 240s | `"123456"` （6位数字） |
| `sms:cd:{phone}` | String | 60s | `"1"` （冷却标记） |
| `token:user:{userId}` | String | 604800s(7d) | `"{jwt_token}"` |
| `user:pref:{userId}` | Hash | 1800s | `{taste, taboos, goal, scene}` |
| `fav:recent:{userId}` | List | 604800s | `["{json1}", "{json2}", ...]` (上限100条) |
| `qa:recent:{userId}` | List | 604800s | `["{json1}", "{json2}", ...]` (上限20条) |
| `rate:meal:{userId}` | String | 当天剩余秒数 | `"5"` (计数器) |
| `rate:qa:{userId}` | String | 当天剩余秒数 | `"3"` (计数器) |
| `ai:cache:{md5}` | String | 3600s | `"{完整推荐结果JSON}"` |
| `rate:ip:{ip}:{api}` | ZSET | 窗口大小60s | `{timestamp: score, requestId: member}` |

### 5.5 限流实现（滑动窗口）

```
Redis ZSET滑动窗口算法:

Key: rate:ip:{ip}:{api}
Score: 当前时间戳(毫秒)
Member: UUID (唯一标识每次请求)

限流逻辑:
1. 当前窗口 = [now - windowSize, now]
2. ZREMRANGEBYSCORE key 0 (now - windowSize)   // 清理过期记录
3. ZCOUNT key (now - windowSize) now            // 统计窗口内请求数
4. if count >= limit → 返回429
5. ZADD key now {uuid}                          // 添加本次请求
6. EXPIRE key windowSize                        // 设置过期时间

限流配置:
- 推荐接口: 5次/分钟 (windowSize=60s, limit=5)
- 问答接口: 10次/分钟 (windowSize=60s, limit=10)
- 验证码: 1次/分钟 (windowSize=60s, limit=1)
```

---

## 六、API接口详细设计

### 6.1 通用规范

```
Base URL: /api/v1
Content-Type: application/json
Auth: Authorization: Bearer {jwt_token}  (除公开接口外)
Tracking: X-Request-Id: {uuid}           (建议)

统一响应:
{
  "code": 200,
  "message": "success",
  "data": { ... },
  "timestamp": 1717516800000
}

分页响应:
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [...],
    "total": 100,
    "page": 1,
    "size": 20
  }
}
```

### 6.2 接口详细定义

#### 6.2.1 用户模块

**POST /api/v1/user/send-code** (公开)

```
Request:  { "phone": "13812341234" }
Response: { "code": 200, "message": "验证码已发送", "data": null }
Error:
  - 400: 手机号格式不正确
  - 429: 发送过于频繁，请{cooldown}秒后再试
  - 503: 短信服务暂不可用

实现要点:
  1. 校验手机号正则: ^1[3-9]\d{9}$
  2. Redis检查 sms:cd:{phone} 是否存在，存在返回429
  3. 生成6位随机数字验证码
  4. 调用短信服务发送
  5. 验证码存入 Redis: sms:code:{phone}，TTL=240s
  6. 冷却标记存入 Redis: sms:cd:{phone}，TTL=60s
```

**POST /api/v1/user/login** (公开)

```
Request:  { "phone": "13812341234", "code": "123456" }
Response: {
  "code": 200,
  "data": {
    "token": "eyJhbGciOi...",
    "userId": 1,
    "phone": "138****1234",
    "expiresAt": "2026-06-12T12:00:00"
  }
}
Error:
  - 400: 验证码错误或已过期
  - 400: 验证码已失效，请重新获取

实现要点:
  1. 从Redis取 sms:code:{phone} 校验
  2. 校验成功后立即DELETE该key
  3. 查询 eat_user 表，不存在则INSERT（无感注册）
  4. 签发JWT: {userId, phone, iat, exp=now+7d}
  5. JWT存入Redis: token:user:{userId}，TTL=7d
```

**GET /api/v1/user/profile** (需认证)

```
Response: {
  "data": {
    "userId": 1,
    "phone": "138****1234",
    "totalUsage": 128,
    "totalFavorites": 36
  }
}

实现要点:
  1. totalUsage: COUNT(eat_qa_record WHERE user_id=?) + 当日推荐计数
  2. totalFavorites: COUNT(eat_favorite WHERE user_id=? AND is_deleted=0)
```

**GET /api/v1/user/preference** (需认证)

```
Response: {
  "data": {
    "taste": "spicy",
    "taboos": ["seafood", "lactose"],
    "goal": "diet",
    "scene": "quick"
  }
}
```

**PUT /api/v1/user/preference** (需认证)

```
Request:  { "taste": "spicy", "taboos": ["seafood"], "goal": "", "scene": "quick" }
Response: { "code": 200, "message": "更新成功" }

实现要点:
  1. 校验枚举值: taste∈{light,heavy,sweet_sour,spicy,""}
  2. 存入MySQL → 删除Redis user:pref:{userId}
```

#### 6.2.2 推荐模块

**POST /api/v1/meal/generate** (需认证)

```
Request: {
  "period": "lunch",         // breakfast/lunch/dinner/snack
  "taste": "spicy",          // 可选, 留空=""
  "taboos": ["seafood"],     // 可选, 留空=[]
  "goal": "diet",            // 可选
  "scene": "takeout"         // 可选
}

Response: SSE流式 (Content-Type: text/event-stream)

event: plan
data: {"planType":"quick","title":"10分钟麻辣减脂便当","items":["鸡胸肉沙拉","全麦卷饼","凉拌黄瓜"],"estTime":"约10分钟","calorieRange":"约400-450千卡"}

event: plan
data: {"planType":"home","title":"家常麻辣轻食","items":[...],"estTime":"约25分钟","calorieRange":"约500-550千卡"}

event: plan
data: {"planType":"comfort","title":"解馋麻辣双拼","items":[...],"estTime":"约15分钟","calorieRange":"约450-500千卡"}

event: done
data: {"totalPlans":3,"requestId":"uuid"}

Error事件:
event: error
data: {"code":429,"message":"今日推荐次数已用完，明天再来吧"}

非流式降级 (AI不可用时):
Response: { "code": 200, "data": { "period": "lunch", "plans": [...], "fromFallback": true } }
```

**POST /api/v1/meal/regenerate** (需认证)

```
Request: {
  "period": "lunch",
  "excludePlanType": "quick",    // 要替换的方案类型
  "excludeTitle": "10分钟麻辣减脂便当",
  ...preference params...
}

Response: SSE流式 (仅返回替换的方案，事件类型同 generate)
```

**GET /api/v1/meal/fallback** (公开)

```
Response: {
  "data": {
    "breakfast": [ {planType, title, items, estTime, calorieRange} ×3 ],
    "lunch":     [ ... ×3 ],
    "dinner":    [ ... ×3 ],
    "snack":     [ ... ×3 ]
  }
}
```

#### 6.2.3 收藏模块

**POST /api/v1/favorites** (需认证)

```
Request: {
  "mealPeriod": "lunch",
  "planType": "quick",
  "planTitle": "10分钟麻辣减脂便当",
  "planContent": "{完整方案JSON}",
  "planItems": ["鸡胸肉沙拉","全麦卷饼"],
  "estTime": "约10分钟",
  "calorieRange": "约400-450千卡"
}
Response: { "code": 200, "data": { "favoriteId": 123 } }
```

**DELETE /api/v1/favorites/{id}** (需认证)

```
Response: { "code": 200, "message": "已取消收藏" }
Error:
  - 404: 收藏不存在
  - 403: 无权操作此收藏 (userId不匹配)
```

**GET /api/v1/favorites** (需认证)

```
Request:  ?page=1&size=20
Response: {
  "data": {
    "dateGroups": [
      {
        "date": "2026-06-04",
        "items": [
          { "id": 1, "mealPeriod": "lunch", "planType": "quick", "planTitle": "...",
            "planItems": [...], "estTime": "...", "calorieRange": "...", "createdAt": "12:35" }
        ]
      },
      { "date": "2026-06-03", "items": [...] }
    ],
    "total": 36,
    "page": 1,
    "size": 20
  }
}

实现要点:
  1. 查询MySQL WHERE user_id=? AND is_deleted=0 ORDER BY created_at DESC
  2. 后端内存中按日期分组 (dateGroups)
```

#### 6.2.4 问答模块

**POST /api/v1/qa/ask** (需认证)

```
Request: {
  "sessionId": "uuid-or-empty",   // 首次为空, 后续传上次返回的sessionId
  "question": "减脂期能吃米饭吗？"
}

Response: SSE流式

event: token
data: "可以的，减脂期可以适量吃米饭。建议..."

event: token
data: "...每次控制在半碗到一碗的量..."

event: token
data: "\n\n以上内容仅供参考，不构成医疗建议。"

event: done
data: {"sessionId":"uuid-xxx","answerLength":256}

Error:
  - 400: 问题长度不能超过500字
  - 429: 今日问答次数已用完
  - 422: "请提出健康的饮食问题" (敏感词拦截)

实现要点:
  1. 问题长度校验: >500字返回400
  2. 敏感词过滤: 命中拦截词返回422
  3. 取最近5条上下文 (qa:recent:{userId} 的前5条) 传入AI
  4. SSE流式转发AI回复token
  5. 保存记录到MySQL + Redis
```

**GET /api/v1/qa/history** (需认证)

```
Request:  ?page=1&size=20
Response: {
  "data": {
    "records": [
      { "id": 1, "sessionId": "uuid", "question": "减脂期能吃米饭吗？",
        "answer": "可以的...", "createdAt": "2026-06-04 12:35:00" }
    ],
    "total": 20,
    "page": 1
  }
}

实现要点:
  1. 优先从Redis (qa:recent:{userId}) 取
  2. 未命中或需要翻页 → 查MySQL LIMIT 20 OFFSET
```

**GET /api/v1/qa/hot-questions** (公开)

```
Response: {
  "data": [
    "减脂期吃什么主食好？",
    "外卖怎么点比较健康？",
    "有什么快手早餐推荐？",
    "经期饮食需要注意什么？",
    "吃什么对胃比较好？"
  ]
}

实现要点:
  1. 从配置文件/数据库静态表读取
  2. 不做个性化排序，固定列表
```

---

## 七、AI集成详细设计

### 7.1 AI Prompt设计

#### 7.1.1 饮食推荐 System Prompt

```
你是一位拥有20年经验的中国营养搭配师和家庭厨师。
你的任务是根据用户的口味、场景和需求，为其推荐3套不同的饮食方案。

## 输出要求
你必须严格按照以下JSON格式输出，不要输出任何JSON以外的内容：

```json
{
  "plans": [
    {
      "planType": "quick",
      "title": "方案名称（10字以内）",
      "items": ["菜品1（含简要做法或外卖搜索关键词）", "菜品2", "菜品3"],
      "estTime": "约X分钟",
      "calorieRange": "约XXX-XXX千卡"
    },
    {
      "planType": "home",
      "title": "...",
      "items": [...],
      "estTime": "...",
      "calorieRange": "..."
    },
    {
      "planType": "comfort",
      "title": "...",
      "items": [...],
      "estTime": "...",
      "calorieRange": "..."
    }
  ]
}
```

## 约束规则
1. planType必须依次为 quick(极简速食)/home(家常正餐)/comfort(解馋轻食)
2. 每套方案items数量2-4个
3. 只推荐中国常见食材和外卖平台可获取的菜品
4. 热量估算合理: quick 300-500千卡, home 500-700千卡, comfort 350-550千卡
5. 不要推荐生食、未煮熟食物、极端饮食方式
6. 方案A(quick)耗时5-15分钟，方案B(home)耗时20-40分钟，方案C(comfort)耗时10-20分钟
7. 如用户选择了忌口，严格避开所有相关食材

## 输出示例
{提供一个完整的JSON示例}
```

#### 7.1.2 饮食问答 System Prompt

```
你是一位专业的饮食健康顾问，为用户提供饮食相关的建议和知识。

## 回答原则
1. 用通俗易懂的中文回答，像朋友聊天一样自然
2. 回答控制在300字以内，简洁明了
3. 基于营养学常识回答，不要编造数据
4. 涉及具体健康问题时，建议咨询专业医生
5. 不要推荐极端饮食方式、偏方、未经证实的疗法

## 必须遵守
每次回答末尾必须加上：
"以上内容仅供参考，不构成医疗建议。如有健康问题请咨询专业医生。"
```

### 7.2 AI响应解析与校验

```java
// AiProxyService 核心流程伪代码

public Flux<MealPlan> recommendStream(MealGenerateRequest req) {
    // 1. 构建消息
    String systemPrompt = promptConfig.getMealSystemPrompt();
    String userMessage = buildMealUserMessage(req);

    // 2. 调用AI (优先qwen-plus)
    return callAiStream(systemPrompt, userMessage)
        .flatMap(chunk -> {
            // 3. 累积JSON片段
            buffer.append(chunk);
            // 4. 尝试解析完整方案
            MealPlan plan = tryParsePlan(buffer);
            if (plan != null && !sentPlans.contains(plan.getPlanType())) {
                sentPlans.add(plan.getPlanType());
                return Flux.just(plan);
            }
            return Flux.empty();
        })
        .timeout(Duration.ofSeconds(15))           // 15秒超时
        .onErrorResume(e -> fallbackRecommend(req.getPeriod()));

}

public Flux<String> qaStream(String question, List<QaRecord> context) {
    String systemPrompt = promptConfig.getQaSystemPrompt();
    List<Message> messages = buildContextMessages(systemPrompt, context, question);

    return callAiStream(messages)
        .timeout(Duration.ofSeconds(15))
        .concatWith(Flux.just("\n\n以上内容仅供参考，不构成医疗建议。"))
        .onErrorResume(e -> Flux.just("抱歉，AI服务暂时不可用，请稍后再试。"));
}
```

### 7.3 模型路由与故障切换

```yaml
# application.yml
eat:
  ai:
    primary:
      url: https://dashscope.aliyuncs.com/compatible-mode/v1
      key: ${AI_API_KEY}
      model: qwen-plus
    fallback:
      url: https://api.deepseek.com/v1
      key: ${AI_FALLBACK_KEY}
      model: deepseek-chat
    timeout:
      connect: 5000     # ms
      read: 15000       # ms
    retry:
      maxRetries: 1
      retryDelay: 1000  # ms
      retryOn: [500, 502, 503]  # 仅5xx重试
```

切换逻辑：

```
primary调用 → 成功 → 返回
            → 连接超时/读取超时 → 记录metric → 切fallback
            → 5xx错误 → 重试1次 → 仍失败 → 记录metric → 切fallback
            → 4xx错误 → 不重试 → 返回错误

fallback调用 → 成功 → 返回(标记fromFallback)
             → 失败 → 返回静态兜底方案
```

### 7.4 AI结果缓存策略

```
缓存Key: ai:cache:{MD5(period + taste + taboos + goal + scene)}
缓存值: 完整推荐结果JSON
TTL: 3600秒 (1小时)

命中条件: 与上一次请求的参数组合完全相同
预估命中率: 40% (同一用户前后来回切换偏好的场景较多)

排除缓存场景:
- regenerate接口不缓存（用户明确要求换一批）
- 带有timestamp参数的请求不缓存
```

---

## 八、安全设计

### 8.1 认证与授权

```
JWT Payload:
{
  "userId": 1,
  "phoneHash": "sha256前6位",
  "iat": 1717516800,
  "exp": 1718121600
}

签发: HMAC-SHA256, Secret≥256位, 从环境变量读取
有效期: 7天
刷新策略: 不做refresh token（轻量场景）, 过期直接重新登录

Token主动失效:
  - Redis维护白名单: token:user:{userId}
  - 用户退出登录时 DELETE 该Key
  - 拦截器校验时检查Redis中是否存在该token
```

### 8.2 数据安全

| 安全措施 | 实现方式 |
|----------|----------|
| 手机号加密存储 | AES-256-CBC加密后存入MySQL，密钥从环境变量读取 |
| 手机号脱敏展示 | 保留前3后4位，中间4位替换为**** |
| JWT Secret | `${JWT_SECRET}`环境变量，≥256位随机字符串 |
| 传输安全 | HTTPS (Nginx层SSL终止) |
| 日志脱敏 | 不记录手机号明文，日志中仅记录脱敏版本 |
| SQL注入防护 | MyBatis-Plus参数化查询，不使用拼接SQL |
| 密码存储 | 无密码体系，仅短信验证码登录 |

### 8.3 敏感词过滤

```java
public class SensitiveFilter {
    // 基于前缀树(Trie)的敏感词匹配，O(n)时间复杂度

    private static final Set<String> BLOCK_WORDS = Set.of(
        "绝食", "催吐", "暴食", "辟谷", "断食疗法",
        "排毒果汁", "一天一顿", "只吃苹果",
        // ...完整列表从配置文件加载
    );

    public boolean containsSensitive(String text) {
        // Trie匹配，命中任一敏感词返回true
    }
}
```

拦截后行为：
1. 不调用AI
2. 不保存问答记录（MySQL + Redis都不写）
3. 返回固定文案："请提出健康的饮食问题"
4. 记录告警日志（仅记录is_sensitive标记，不记录问题原文）

### 8.4 限流策略汇总

| 场景 | 维度 | 窗口 | 限制 | 返回 |
|------|------|------|------|------|
| 短信验证码发送 | phone | 60s | 1次 | 429 + 冷却倒计时 |
| 饮食推荐 | userId+IP | 60s | 5次 | 429 |
| AI问答 | userId+IP | 60s | 10次 | 429 |
| 每日推荐总次数 | userId | 1天 | 20次 | 429 + 次数用完提示 |
| 每日问答总次数 | userId | 1天 | 30次 | 429 + 次数用完提示 |

---

## 九、异常处理与监控

### 9.1 全局异常处理

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(JwtException.class)
    public R<Void> handleJwt(JwtException e) {
        return R.fail(401, "登录已过期，请重新登录");
    }

    @ExceptionHandler(RateLimitException.class)
    public R<Void> handleRateLimit(RateLimitException e) {
        return R.fail(429, e.getMessage());
    }

    @ExceptionHandler(AiServiceException.class)
    public R<Void> handleAiError(AiServiceException e) {
        log.error("AI service error: {}", e.getMessage());
        return R.fail(500, "服务繁忙，请稍后再试");
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleUnknown(Exception e) {
        log.error("Unhandled exception", e);
        return R.fail(500, "系统异常，请稍后再试");
    }
}
```

### 9.2 降级策略流程

```
                     请求进入
                         │
                         ▼
                  ┌─ 检查Redis ─┐
                  │  (缓存命中?)  │
                  └──────┬───────┘
                    是        否
                    │          │
                    ▼          ▼
               返回缓存    调用AI API
                              │
                    ┌─────────┼─────────┐
                    ▼         ▼         ▼
                  成功      超时15s    5xx错误
                    │         │         │
                    ▼         ▼         ▼
               解析JSON   重试1次   重试1次
                    │       │         │
              ┌─────┴──┐   ▼         ▼
              ▼        ▼ 仍失败    仍失败
           解析成功  解析失败  │         │
              │        │     ▼         ▼
              ▼        ▼  记录告警  记录告警
           返回结果  记录日志  │         │
                     │     ▼         ▼
                     │  返回兜底方案 ←──┘
                     └──────┘
```

### 9.3 关键监控指标

| 指标 | 采集方式 | 告警阈值 |
|------|----------|----------|
| AI调用成功率 | AiProxyService 计数器 | <95%触发告警 |
| AI平均响应时间 | AiProxyService 计时器 | >12s触发告警 |
| 兜底方案触发次数 | 兜底方案调用计数器 | >10次/小时触发告警 |
| 限流触发次数 | RateLimitInterceptor 计数器 | >50次/小时关注 |
| 敏感词拦截次数 | SensitiveFilter 计数器 | 仅记录不告警 |
| 短信发送成功率 | UserService 计数器 | <90%触发告警 |
| 接口QPS | Controller层AOP | >100QPS关注 |
| Redis连接状态 | HealthIndicator | 断开立即告警 |
| MySQL连接状态 | HealthIndicator | 断开立即告警 |

### 9.4 日志规范

```java
// 关键操作日志格式
log.info("[MEAL] userId={} period={} taste={} cacheHit={} latencyMs={}", userId, period, taste, hit, ms);
log.info("[QA] userId={} sessionId={} questionLen={} sensitive={} latencyMs={}", userId, sid, len, sen, ms);
log.info("[LOGIN] phone={} success={}", maskedPhone, success);
log.info("[SMS] phone={} success={} provider={}", maskedPhone, success, provider);

// 错误日志
log.error("[AI] model={} errorType={} latencyMs={} retryCount={}", model, type, ms, retry);
log.error("[AI_FALLBACK] reason={} period={}", reason, period);

// 不记录的内容:
// - 手机号明文
// - 问答完整内容 (只记ID和字数)
// - JWT Token
// - 用户完整偏好具体值
```

---

## 十、部署架构

### 10.1 部署拓扑

```
┌──────────────────────────────────────────┐
│             云服务器 (2C4G)                │
│                                           │
│  ┌─────────────────────────────────────┐ │
│  │         Docker Compose               │ │
│  │                                       │ │
│  │  ┌──────────┐  ┌──────────┐         │ │
│  │  │  Nginx   │  │  MySQL   │         │ │
│  │  │  :80/443 │  │  :3306   │         │ │
│  │  └────┬─────┘  └──────────┘         │ │
│  │       │                               │ │
│  │  ┌────▼─────┐  ┌──────────┐         │ │
│  │  │  Java App│  │  Redis   │         │ │
│  │  │  :8080   │  │  :6379   │         │ │
│  │  └──────────┘  └──────────┘         │ │
│  └─────────────────────────────────────┘ │
└──────────────────────────────────────────┘
```

### 10.2 Docker Compose 配置

```yaml
# docker-compose.yml
version: '3.8'
services:
  nginx:
    image: nginx:1.25-alpine
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf
      - ./eat-frontend/dist:/usr/share/nginx/html
    depends_on:
      - app

  app:
    build: ./eat-server
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - MYSQL_URL=jdbc:mysql://mysql:3306/eat_db
      - REDIS_HOST=redis
      - JWT_SECRET=${JWT_SECRET}
      - AI_API_KEY=${AI_API_KEY}
    depends_on:
      - mysql
      - redis

  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: eat_db
    volumes:
      - mysql_data:/var/lib/mysql
      - ./eat-server/src/main/resources/db/migration:/docker-entrypoint-initdb.d

  redis:
    image: redis:7-alpine
    command: redis-server --appendonly yes
    volumes:
      - redis_data:/data

volumes:
  mysql_data:
  redis_data:
```

### 10.3 环境变量管理

所有敏感配置通过环境变量注入，生产环境通过 Docker Compose `.env` 文件管理：

```
# .env (不提交到Git)
JWT_SECRET=<256位以上随机字符串>
MYSQL_ROOT_PASSWORD=<数据库密码>
AI_API_KEY=sk-xxx
AI_FALLBACK_KEY=sk-xxx
SMS_ACCESS_KEY=xxx
SMS_SECRET_KEY=xxx
SMS_TEMPLATE_CODE=SMS_123456
```

---

## 十一、开发排期与技术风险

### 11.1 开发任务拆解（4周）

| 周次 | 模块 | 任务 |
|------|------|------|
| 第1周 | 基础设施 | 项目初始化、数据库建表、Redis配置、JWT拦截器、限流拦截器、统一异常处理 |
| 第2周 | 后端核心 | 用户模块(登录/偏好)、推荐模块、问答模块、收藏模块、AI代理模块 |
| 第3周 | 前端核心 | 4个页面+路由+状态管理+SSE流式接收、Axios拦截器、异常/降级处理 |
| 第4周 | 联调上线 | 全流程测试、异常场景测试、性能优化、生产部署 |

### 11.2 技术风险与应对

| 风险 | 等级 | 应对措施 |
|------|------|----------|
| AI接口不稳定 | 高 | 双模型切换 + 静态兜底方案，保证基本可用 |
| AI返回格式不符合JSON Schema | 中 | 返回前校验 + 解析失败走兜底 + 日志记录持续优化Prompt |
| 短信服务不可用 | 中 | 开发阶段预留Mock模式，后续可接入备选服务商 |
| 高并发下AI调用成本过高 | 低 | 已有缓存+限流策略，MVP阶段DAU低，成本可控 |
| Redis不可用导致功能全挂 | 中 | 代码中捕获Redis异常后降级直查MySQL，保证核心流程可用 |
| 前端SSE兼容性 | 低 | fetch + ReadableStream已覆盖主流浏览器，低版本降级为轮询 |

### 11.3 MVP不做的事项（重申PRD边界）

1. 不做APP原生/微信小程序
2. 不做社交功能（分享/评论）
3. 不做支付/会员
4. 不做AI图片识别
5. 不做外卖平台对接
6. 不做多语言
7. 不做复杂的营养数据分析

---

## 附录A：项目目录结构（完整）

```
eat-demo/
├── eat-frontend/
│   ├── src/
│   │   ├── views/
│   │   │   ├── HomePage.vue
│   │   │   ├── QaPage.vue
│   │   │   ├── FavoritesPage.vue
│   │   │   ├── ProfilePage.vue
│   │   │   └── LoginPage.vue
│   │   ├── components/
│   │   │   ├── TabBar.vue
│   │   │   ├── PeriodSelector.vue
│   │   │   ├── PreferenceTags.vue
│   │   │   ├── MealCard.vue
│   │   │   ├── LoadingSkeleton.vue
│   │   │   ├── ChatBubble.vue
│   │   │   ├── ChatInput.vue
│   │   │   ├── QuickQuestions.vue
│   │   │   ├── DateGroup.vue
│   │   │   ├── FavoriteCard.vue
│   │   │   └── EmptyState.vue
│   │   ├── stores/
│   │   │   ├── user.js
│   │   │   └── preference.js
│   │   ├── api/
│   │   │   ├── request.js          # Axios实例+拦截器
│   │   │   ├── user.js
│   │   │   ├── meal.js
│   │   │   ├── qa.js
│   │   │   └── favorite.js
│   │   ├── router/
│   │   │   └── index.js
│   │   ├── utils/
│   │   │   ├── sse.js              # SSE流式接收工具
│   │   │   └── storage.js          # localStorage封装
│   │   ├── App.vue
│   │   └── main.js
│   ├── index.html
│   ├── vite.config.js
│   └── package.json
│
├── eat-server/
│   ├── src/main/java/com/eat/
│   │   ├── EatApplication.java
│   │   ├── common/
│   │   │   ├── R.java
│   │   │   ├── ResultCode.java
│   │   │   ├── GlobalExceptionHandler.java
│   │   │   └── utils/
│   │   │       ├── JwtUtil.java
│   │   │       ├── PhoneUtil.java
│   │   │       ├── SensitiveFilter.java
│   │   │       └── SseEmitterUtil.java
│   │   ├── config/
│   │   │   ├── RedisConfig.java
│   │   │   ├── MyBatisPlusConfig.java
│   │   │   ├── WebMvcConfig.java
│   │   │   └── RestTemplateConfig.java
│   │   ├── interceptor/
│   │   │   ├── JwtInterceptor.java
│   │   │   └── RateLimitInterceptor.java
│   │   ├── controller/
│   │   │   ├── UserController.java
│   │   │   ├── MealController.java
│   │   │   ├── QaController.java
│   │   │   └── FavoriteController.java
│   │   ├── service/
│   │   │   ├── UserService.java
│   │   │   ├── MealService.java
│   │   │   ├── QaService.java
│   │   │   ├── FavoriteService.java
│   │   │   └── AiProxyService.java
│   │   ├── mapper/
│   │   │   ├── UserMapper.java
│   │   │   ├── UserPreferenceMapper.java
│   │   │   ├── FavoriteMapper.java
│   │   │   └── QaRecordMapper.java
│   │   ├── entity/
│   │   │   ├── User.java
│   │   │   ├── UserPreference.java
│   │   │   ├── Favorite.java
│   │   │   └── QaRecord.java
│   │   └── dto/
│   │       ├── request/
│   │       │   ├── SendCodeRequest.java
│   │       │   ├── LoginRequest.java
│   │       │   ├── MealGenerateRequest.java
│   │       │   ├── MealRegenerateRequest.java
│   │       │   ├── QaAskRequest.java
│   │       │   └── PreferenceUpdateRequest.java
│   │       └── response/
│   │           ├── LoginResponse.java
│   │           ├── MealPlanResponse.java
│   │           ├── MealResultResponse.java
│   │           ├── QaReplyResponse.java
│   │           └── FavoriteListResponse.java
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── application-dev.yml
│   │   ├── application-prod.yml
│   │   └── db/migration/
│   │       ├── V001__create_user.sql
│   │       ├── V002__create_user_preference.sql
│   │       ├── V003__create_favorite.sql
│   │       └── V004__create_qa_record.sql
│   ├── pom.xml
│   └── Dockerfile
│
├── doc/
│   ├── PRD文档.md
│   └── 设计文档.md            # 本文档
├── docker-compose.yml
├── nginx.conf
└── .env.example
```

---

> 文档版本 V1.0，2026-06-05，基于PRD V2.0编写
