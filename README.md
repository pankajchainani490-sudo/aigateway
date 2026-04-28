# GcdGateway-AI

一个基于 Netty 开发的 AI 网关，支持多 AI 模型路由、负载均衡、熔断降级、流量控制、灰度发布等企业级功能。

## 项目介绍

GcdGateway-AI 是一个高性能的 AI 网关系统，采用微服务架构设计，核心基于 Netty 异步非阻塞 I/O 实现。主要功能包括：

### 核心特性

- **AI 模型路由**：支持 DeepSeek、OpenAI 等多种 AI provider，自动负载均衡
- **动态配置中心**：支持 Nacos、Zookeeper 等配置中心，配置实时生效
- **服务注册发现**：基于 Nacos 的服务注册与发现机制
- **流量控制**：支持令牌桶、漏桶、滑动窗口三种限流算法
- **熔断降级**：基于 Resilience4j 的熔断器实现
- **灰度发布**：支持基于客户端 IP、阈值等灰度策略
- **负载均衡**：轮询、随机、加权、一致性哈希等策略
- **语义缓存**：支持精确匹配和嵌入相似度缓存
- **计费系统**：支持输入/输出 token 计费和缓存优惠
- **跨域处理**：灵活的 CORS 配置

### 技术栈

- Java 17
- Maven (Spring Boot 3.5.6)
- Netty 4.1.51.Final
- Nacos 2.2.0
- Resilience4j 2.2.0
- Caffeine 3.1.8 (高性能缓存)
- jtokkit 1.0.0 (Token 计数)

### 模块说明

| 模块 | 说明 |
|------|------|
| GcdGateway-Bootstrap | 网关启动入口 |
| GcdGateway-Core | 核心网关功能 (Netty、FilterChain、负载均衡等) |
| GcdGateway-Common | 公共工具和常量 |
| GcdGateway-Config | 配置中心管理 |
| GcdGateway-Register | 服务注册发现 |
| GcdGateway-AI | AI 网关功能 (模型路由、计费、缓存等) |
| GcdGateway-User | 用户服务示例 |
| GcdGateway-Order | 订单服务示例 |
| GcdGateway-Demo | 启动示例 |

## 项目启动

### 环境要求

- JDK 17+
- Maven 3.6+
- Nacos Server 2.x (默认地址: 127.0.0.1:8848)

### 1. 启动 Nacos

```bash
# 下载并启动 Nacos
# https://github.com/alibaba/nacos/releases

# 单机模式启动
sh startup.sh -m standalone
```

### 2. 编译项目

```bash
cd aigateway
mvn clean install -DskipTests
```

### 3. 启动网关

运行 `GcdGateway-Demo` 模块中的 `Main` 类：

```bash
# 或通过 Maven 运行
mvn compile exec:java -pl GcdGateway-Demo -Dexec.mainClass="com.gcd.coding.gcdgatewaydemo.Main"
```

## 配置说明

### 网关基础配置 (gateway.yaml)

```yaml
gcd:
  gateway:
    name: gcd-ai-gateway          # 网关名称
    port: 10080                   # 网关端口
    env: dev                      # 环境标识
    configCenter:
      enabled: false              # 是否启用配置中心（false则使用本地配置）
      type: NACOS                 # 配置中心类型：NACOS / ZOOKEEPER
      address: 127.0.0.1:8848     # 配置中心地址
      nacos:
        dataId: gcd-gateway          # 路由配置dataId
        group: DEFAULT_GROUP
        aiDataId: gcd-ai-gateway    # AI配置dataId（可选）
        aiGroup: DEFAULT_GROUP        # AI配置group（可选）
    registerCenter:
      type: NACOS                 # 注册中心类型
      address: 127.0.0.1:8848     # 注册中心地址
    globalCorsConfig:              # 全局跨域配置
      enabled: true
      allowOrigin: "*"            # 允许的源
      allowMethods: "GET,POST,PUT,DELETE,OPTIONS"
      allowHeaders: "Content-Type, Authorization"
      allowCredentials: true
      maxAge: 86400
    netty:                         # Netty服务器配置
      eventLoopGroupBossNum: 1     # Boss线程数
      eventLoopGroupWorkerNum: 32   # Worker线程数（默认CPU核数*2）
      maxContentLength: 67108864    # 最大内容长度（64MB）
    httpClient:                     # HTTP客户端配置
      eventLoopGroupWorkerNum: 32   # HTTP客户端Worker线程数
      httpConnectTimeout: 30000     # 连接超时（ms）
      httpRequestTimeout: 30000    # 请求超时（ms）
      httpMaxRedirects: 2          # 最大重定向次数
      httpMaxConnections: 10000    # 最大连接数
      httpConnectionsPerHost: 8000 # 每主机最大连接数
      httpPooledConnectionIdleTimeout: 60000  # 空闲连接超时（ms）
```

### 完整路由配置示例

以下是包含所有策略和算法的完整路由配置：

```yaml
gcd:
  gateway:
    routes:
      # AI聊天路由 - 包含流控、熔断、负载均衡、灰度等所有策略
      - id: ai-chat-route
        serviceName: ai-chat-service
        uri: /api/ai/**
        order: 0
        resilience:                    # 弹性配置（熔断、降级、重试）
          enabled: true
          retryEnabled: true          # 是否开启重试
          circuitBreakerEnabled: true # 是否开启熔断
          fallbackEnabled: true       # 是否开启降级
          bulkheadEnabled: false      # 是否开启信号量隔离
          threadPoolBulkheadEnabled: false  # 是否开启线程池隔离
          order:                      # 过滤器执行顺序
            - THREADPOOLBULKHEAD
            - BULKHEAD
            - RETRY
            - CIRCUITBREAKER
            - FALLBACK
          # 重试配置
          maxAttempts: 3              # 最大重试次数
          waitDuration: 500            # 重试间隔时间（ms）
          # 熔断配置
          failureRateThreshold: 50     # 失败率阈值（%），超过则熔断
          slowCallRateThreshold: 100   # 慢调用比例阈值（%）
          slowCallDurationThreshold: 60000  # 慢调用判定时间（ms）
          permittedNumberOfCallsInHalfOpenState: 10  # 半开状态允许调用次数
          maxWaitDurationInHalfOpenState: 0  # 半开状态最大等待时间（ms），0表示一直等待
          type: COUNT_BASED          # 熔断器类型：COUNT_BASED / TIME_BASED
          slidingWindowSize: 100      # 滑动窗口大小
          minimumNumberOfCalls: 100   # 最小调用次数（用于计算失败率）
          waitDurationInOpenState: 60000  # 熔断开启到半开的等待时间（ms）
          automaticTransitionFromOpenToHalfOpenEnabled: false  # 自动转换到半开
          # 降级配置
          fallbackHandlerName: default_fallback_handler  # 降级处理器名称
          # 信号量隔离配置
          maxConcurrentCalls: 1000   # 最大并发调用数
          maxWaitDuration: 0         # 最大等待时间（ms）
          fairCallHandlingEnabled: false  # 公平竞争模式
          # 线程池隔离配置
          coreThreadPoolSize: 5      # 核心线程数
          maxThreadPoolSize: 10      # 最大线程数
          queueCapacity: 100         # 队列容量
        filterConfigs:               # 路由级过滤器配置
          - name: flow_filter
            enable: true
            config: |
              {
                "enabled": true,
                "type": "TOKEN_BUCKET",
                "capacity": 1000,
                "rate": 500
              }
          - name: gray_filter
            enable: true
            config: |
              {
                "strategyName": "threshold_gray_strategy",
                "maxGrayThreshold": 0.3
              }
          - name: load_balance_filter
            enable: true
            config: |
              {
                "strategyName": "round_robin",
                "isStrictRoundRobin": true,
                "virtualNodeNum": 3
              }
        corsFilterConfig:           # 路由级跨域配置
          enabled: true
          allowOrigin: "*"
          allowMethods: "GET,POST,PUT,DELETE,OPTIONS"
          allowHeaders: "Content-Type, Authorization"
          allowCredentials: true
          maxAge: 3600

      # 用户服务路由 - 简单配置
      - id: user-service-route
        serviceName: user-service
        uri: /api/user/**
        order: 1
        resilience:
          enabled: true
          retryEnabled: true
          circuitBreakerEnabled: true
          fallbackEnabled: true
```

### 流控配置 (FlowFilterConfig)

流量控制支持三种算法：

```yaml
# 方式1：令牌桶限流（TOKEN_BUCKET）- 常用
filterConfigs:
  - name: flow_filter
    enable: true
    config: |
      {
        "enabled": true,
        "type": "TOKEN_BUCKET",    # 令牌桶算法
        "capacity": 1000,           # 桶容量
        "rate": 500                 # 令牌生成速率（个/秒）
      }

# 方式2：滑动窗口限流（SLIDING_WINDOW）
filterConfigs:
  - name: flow_filter
    enable: true
    config: |
      {
        "enabled": true,
        "type": "SLIDING_WINDOW",   # 滑动窗口算法
        "capacity": 1000,           # 窗口容量
        "rate": 60000                # 窗口大小（ms）
      }

# 方式3：漏桶限流（LEAKY_BUCKET）
filterConfigs:
  - name: flow_filter
    enable: true
    config: |
      {
        "enabled": true,
        "type": "LEAKY_BUCKET",     # 漏桶算法
        "capacity": 1000,           # 桶容量
        "rate": 100                 # 漏桶速率（ms/个）
      }
```

### 灰度发布配置 (GrayFilterConfig)

```yaml
# 阈值灰度策略 - 根据流量比例灰度
filterConfigs:
  - name: gray_filter
    enable: true
    config: |
      {
        "strategyName": "threshold_gray_strategy",
        "maxGrayThreshold": 0.3     # 30%流量走灰度版本
      }

# 基于IP的灰度策略
filterConfigs:
  - name: gray_filter
    enable: true
    config: |
      {
        "strategyName": "ip_gray_strategy",
        "grayIpList": ["192.168.1.100", "192.168.1.101"],
        "maxGrayThreshold": 1.0     # IP在列表中则100%走灰度
      }
```

### 负载均衡配置 (LoadBalanceFilterConfig)

```yaml
# 方式1：轮询负载均衡（默认）
filterConfigs:
  - name: load_balance_filter
    enable: true
    config: |
      {
        "strategyName": "round_robin",
        "isStrictRoundRobin": true   # 严格轮询
      }

# 方式2：加权轮询
filterConfigs:
  - name: load_balance_filter
    enable: true
    config: |
      {
        "strategyName": "weight_round_robin",
        "isStrictRoundRobin": true
      }

# 方式3：随机负载均衡
filterConfigs:
  - name: load_balance_filter
    enable: true
    config: |
      {
        "strategyName": "random"
      }

# 方式4：加权随机
filterConfigs:
  - name: load_balance_filter
    enable: true
    config: |
      {
        "strategyName": "weight_random"
      }

# 方式5：一致性哈希
filterConfigs:
  - name: load_balance_filter
    enable: true
    config: |
      {
        "strategyName": "consistent_hash",
        "virtualNodeNum": 3         # 虚拟节点数
      }

# 方式6：最少活跃调用
filterConfigs:
  - name: load_balance_filter
    enable: true
    config: |
      {
        "strategyName": "least_active"
      }
```

### 熔断器配置 (CircuitBreaker)

Resilience4j熔断器配置，通过`resilience`字段设置：

```yaml
resilience:
  enabled: true
  # 熔断器类型
  type: COUNT_BASED  # COUNT_BASED=计数滑动窗口，TIME_BASED=时间滑动窗口
  # 熔断触发条件
  failureRateThreshold: 50      # 失败率阈值（%），超过触发熔断
  slowCallRateThreshold: 100     # 慢调用比例阈值（%）
  slowCallDurationThreshold: 60000  # 慢调用判定阈值（ms）
  # 滑动窗口
  slidingWindowSize: 100          # 滑动窗口大小
  minimumNumberOfCalls: 100       # 计算失败率的最小调用数
  # 熔断状态转换
  waitDurationInOpenState: 60000  # 熔断开启到半开的等待时间（ms）
  permittedNumberOfCallsInHalfOpenState: 10  # 半开状态允许的调用数
  automaticTransitionFromOpenToHalfOpenEnabled: false  # 是否自动转换
  maxWaitDurationInHalfOpenState: 0  # 半开状态最大等待时间
```

### 降级配置 (Fallback)

```yaml
resilience:
  fallbackEnabled: true
  fallbackHandlerName: default_fallback_handler  # 降级处理器名称

# 内置降级处理器：
# - default_fallback_handler: 返回统一错误响应
# - custom_fallback_handler: 自定义降级处理（需实现FallbackHandler接口）
```

### 重试配置 (Retry)

```yaml
resilience:
  retryEnabled: true
  maxAttempts: 3               # 最大重试次数（包括首次调用）
  waitDuration: 500             # 重试间隔（ms）
```

### 信号量隔离配置 (Bulkhead)

```yaml
resilience:
  bulkheadEnabled: true
  maxConcurrentCalls: 1000      # 最大并发信号量数
  maxWaitDuration: 0            # 最大等待时间（ms），0表示不等待
  fairCallHandlingEnabled: false  # 公平竞争模式
```

### 线程池隔离配置 (ThreadPoolBulkhead)

```yaml
resilience:
  threadPoolBulkheadEnabled: true
  coreThreadPoolSize: 5          # 核心线程数
  maxThreadPoolSize: 10          # 最大线程数
  queueCapacity: 100             # 队列容量
```

### AI 配置 (gateway.yaml 中的 ai 部分)

```yaml
gcd:
  gateway:
    ai:
      enabled: true               # 是否启用 AI 功能
      billing:
        enabled: true             # 是否启用计费
        currency: CNY              # 货币类型
        logEnabled: true          # 是否记录计费日志
      token:
        defaultRateLimitPerMinute: 1000000  # 默认每分钟限流
        tokenRateLimitEnabled: false        # 是否启用 token 限流
      cache:
        enabled: true             # 是否启用缓存
        mode: exact              # 缓存模式: exact(精确匹配) / embedding(嵌入相似度)
        ttlSeconds: 3600         # 缓存 TTL
        maxSize: 10000            # 最大缓存条数
        similarityThreshold: 0.95 # 相似度阈值 (embedding 模式)
      providers:                 # AI Provider 配置
        - name: deepseek
          type: deepseek
          baseUrl: https://api.deepseek.com
          apiKey: your-api-key   # 请替换为您的 API Key
          protocol: openai
          supportsStreaming: true
          connectTimeout: 30000
          requestTimeout: 120000
          maxRetries: 3
      models:                    # 模型配置
        - modelName: deepseek-chat
          providerName: deepseek
          providerModelId: deepseek-chat
          inputPricePer1KTokens: 0.001   # 输入价格 (元/千 token)
          outputPricePer1KTokens: 0.002  # 输出价格 (元/千 token)
          cacheHitDiscount: 0.5          # 缓存命中折扣
          maxInputTokens: 128000
          maxOutputTokens: 4096
          supportsStreaming: true
          weight: 100                     # 权重 (用于负载均衡)
```

## Nacos 配置详解

### 完整 Nacos 配置格式（JSON）

当`configCenter.enabled: true`时，可在Nacos中配置所有网关参数：

```json
{
  "routes": [
    {
      "id": "ai-chat-route",
      "serviceName": "ai-chat-service",
      "uri": "/api/ai/**",
      "order": 0,
      "resilience": {
        "enabled": true,
        "retryEnabled": true,
        "circuitBreakerEnabled": true,
        "fallbackEnabled": true,
        "bulkheadEnabled": false,
        "threadPoolBulkheadEnabled": false,
        "order": ["THREADPOOLBULKHEAD", "BULKHEAD", "RETRY", "CIRCUITBREAKER", "FALLBACK"],
        "maxAttempts": 3,
        "waitDuration": 500,
        "failureRateThreshold": 50,
        "slowCallRateThreshold": 100,
        "slowCallDurationThreshold": 60000,
        "permittedNumberOfCallsInHalfOpenState": 10,
        "maxWaitDurationInHalfOpenState": 0,
        "type": "COUNT_BASED",
        "slidingWindowSize": 100,
        "minimumNumberOfCalls": 100,
        "waitDurationInOpenState": 60000,
        "automaticTransitionFromOpenToHalfOpenEnabled": false,
        "fallbackHandlerName": "default_fallback_handler",
        "maxConcurrentCalls": 1000,
        "maxWaitDuration": 0,
        "fairCallHandlingEnabled": false,
        "coreThreadPoolSize": 5,
        "maxThreadPoolSize": 10,
        "queueCapacity": 100
      },
      "filterConfigs": [
        {
          "name": "flow_filter",
          "enable": true,
          "config": "{\"enabled\":true,\"type\":\"TOKEN_BUCKET\",\"capacity\":1000,\"rate\":500}"
        },
        {
          "name": "gray_filter",
          "enable": true,
          "config": "{\"strategyName\":\"threshold_gray_strategy\",\"maxGrayThreshold\":0.3}"
        },
        {
          "name": "load_balance_filter",
          "enable": true,
          "config": "{\"strategyName\":\"round_robin\",\"isStrictRoundRobin\":true,\"virtualNodeNum\":3}"
        }
      ],
      "corsFilterConfig": {
        "enabled": true,
        "allowOrigin": "*",
        "allowMethods": "GET,POST,PUT,DELETE,OPTIONS",
        "allowHeaders": "Content-Type, Authorization",
        "allowCredentials": true,
        "maxAge": 3600
      }
    }
  ]
}
```

### Nacos AI 配置（单独管理）

AI配置可通过单独的dataId管理，便于独立发布：

```json
{
  "enabled": true,
  "billing": {
    "enabled": true,
    "currency": "CNY",
    "logEnabled": true
  },
  "token": {
    "defaultRateLimitPerMinute": 1000000,
    "tokenRateLimitEnabled": false
  },
  "cache": {
    "enabled": true,
    "mode": "exact",
    "ttlSeconds": 3600,
    "maxSize": 10000,
    "similarityThreshold": 0.95
  },
  "providers": [
    {
      "name": "deepseek",
      "type": "deepseek",
      "baseUrl": "https://api.deepseek.com",
      "apiKey": "sk-your-api-key",
      "protocol": "openai",
      "supportsStreaming": true,
      "connectTimeout": 30000,
      "requestTimeout": 120000,
      "maxRetries": 3
    },
    {
      "name": "openai",
      "type": "openai",
      "baseUrl": "https://api.openai.com",
      "apiKey": "sk-your-openai-key",
      "protocol": "openai",
      "supportsStreaming": true,
      "connectTimeout": 30000,
      "requestTimeout": 120000,
      "maxRetries": 3
    }
  ],
  "models": [
    {
      "modelName": "deepseek-chat",
      "providerName": "deepseek",
      "providerModelId": "deepseek-chat",
      "inputPricePer1KTokens": 0.001,
      "outputPricePer1KTokens": 0.002,
      "cacheHitDiscount": 0.5,
      "maxInputTokens": 128000,
      "maxOutputTokens": 4096,
      "supportsStreaming": true,
      "weight": 100
    },
    {
      "modelName": "gpt-4",
      "providerName": "openai",
      "providerModelId": "gpt-4",
      "inputPricePer1KTokens": 0.03,
      "outputPricePer1KTokens": 0.06,
      "cacheHitDiscount": 0.5,
      "maxInputTokens": 8192,
      "maxOutputTokens": 4096,
      "supportsStreaming": true,
      "weight": 50
    }
  ]
}
```

### 配置优先级

```
Nacos配置中心 > 本地gateway.yaml
```

### 配置热更新

修改Nacos中的配置后，网关会自动感知变更并生效，无需重启服务。

## 项目测试

### 运行单元测试

```bash
# 运行所有测试
mvn test

# 运行特定模块测试
mvn test -pl GcdGateway-AI

# 运行 AI 网关功能测试
mvn test -pl GcdGateway-AI -Dtest=AIGatewayTest
```

### 手动测试 API

```bash
# 测试 AI 聊天接口（非流式）
curl -X POST http://localhost:10080/api/ai/chat \
  -H "Content-Type: application/json" \
  -d '{
    "model": "deepseek-chat",
    "messages": [
      {"role": "user", "content": "你好，请介绍一下自己"}
    ]
  }'

# 测试流式响应
curl -X POST http://localhost:10080/api/ai/chat \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "model": "deepseek-chat",
    "messages": [
      {"role": "user", "content": "用一句话介绍你自己"}
    ],
    "stream": true
  }'

# 测试带参数的请求
curl -X POST http://localhost:10080/api/ai/chat \
  -H "Content-Type: application/json" \
  -d '{
    "model": "deepseek-chat",
    "messages": [
      {"role": "system", "content": "你是一个友好的AI助手"},
      {"role": "user", "content": "什么是人工智能?"}
    ],
    "temperature": 0.7,
    "maxTokens": 500
  }'
```

### 测试用例说明

`AIGatewayTest` 包含以下测试：

1. **Token 计数测试** (`testTokenCounter`) - 测试 Token 计算功能
2. **计费记录测试** (`testBillingRecord`) - 测试计费逻辑，包含缓存命中/未命中场景
3. **缓存测试** (`testCache`) - 测试精确匹配和嵌入相似度缓存
4. **DeepSeek API 测试** (`testDeepSeekAPI`) - 测试实际 API 调用

## API 接口

### AI 聊天接口

```
POST /api/ai/chat
```

**请求体**：

```json
{
  "model": "deepseek-chat",           // 模型名称 (可选，不填则自动路由)
  "messages": [                       // 消息列表
    {"role": "system", "content": "你是一个AI助手"},
    {"role": "user", "content": "你好"}
  ],
  "temperature": 0.7,                  // 温度参数 (可选)
  "maxTokens": 1000,                 // 最大输出 tokens (可选)
  "stream": false                     // 是否流式输出 (可选)
}
```

**响应体**：

```json
{
  "id": "chatcmpl-xxx",
  "model": "deepseek-chat",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "你好！有什么可以帮助你的？"
      },
      "finishReason": "stop"
    }
  ],
  "usage": {
    "promptTokens": 50,
    "completionTokens": 20,
    "totalTokens": 70
  }
}
```

## AI 功能测试详解

### 1. 基础聊天测试

```bash
curl -X POST http://127.0.0.1:10080/api/ai/chat \
  -H "Content-Type: application/json" \
  -d '{
    "model": "deepseek-chat",
    "messages": [{"role": "user", "content": "Hello"}]
  }'
```

### 2. 流式响应测试

```bash
curl -X POST http://127.0.0.1:10080/api/ai/chat \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "model": "deepseek-chat",
    "messages": [{"role": "user", "content": "写一首诗"}],
    "stream": true
  }'
```

### 3. 多轮对话测试

```bash
curl -X POST http://127.0.0.1:10080/api/ai/chat \
  -H "Content-Type: application/json" \
  -d '{
    "model": "deepseek-chat",
    "messages": [
      {"role": "system", "content": "你是一个乐于助人的助手"},
      {"role": "user", "content": "我叫张三"},
      {"role": "assistant", "content": "你好张三！有什么可以帮助你的？"},
      {"role": "user", "content": "我叫什么呢？"}
    ]
  }'
```

## AI 模块架构说明

### 过滤器执行顺序

AI请求经过以下过滤器链：

```
1. CorsFilter (CORS跨域处理)
2. FlowFilter (流量控制)
3. GrayFilter (灰度发布)
4. LoadBalanceFilter (负载均衡)
5. AIProtocolFilter (协议转换)      ← 解析请求体，设置AIRequest到上下文
6. AITokenFilter (Token计数/限流)
7. AISemanticCacheFilter (语义缓存)  ← 检查缓存命中
8. AIModelRouteFilter (模型路由)    ← 核心：调用AI Provider
9. AIBillingFilter (计费记录)
10. RouteFilter (路由转发)
```

### 核心类说明

| 类名 | 位置 | 功能 |
|------|------|------|
| `AIModelRouteFilter` | filter | AI模型路由核心过滤器 |
| `AIProtocolFilter` | filter | 请求协议转换（OpenAI格式解析） |
| `AITokenFilter` | filter | Token计数与限流 |
| `AISemanticCacheFilter` | filter | 语义缓存（精确匹配/嵌入相似度） |
| `AIBillingFilter` | filter | 计费记录 |
| `AIGatewayConfig` | config | AI网关配置模型 |
| `AIGatewayConfigManager` | config | AI配置管理器（单例） |
| `ProviderConfig` | config | Provider配置 |
| `ModelConfig` | config | 模型配置 |
| `DeepSeekModelProvider` | provider | DeepSeek API调用实现 |
| `OpenAIModelProvider` | provider | OpenAI API调用实现 |
| `AIModelProviderManager` | provider | Provider管理器 |
| `AIRequest` | model | AI请求模型 |
| `AIResponse` | model | AI响应模型 |
| `ChatMessage` | model | 聊天消息模型 |

## 常见问题

### 1. 启动失败，提示连接 Nacos 失败

确保 Nacos 已启动：

```bash
curl http://127.0.0.1:8848/nacos/v1/console/health/readiness
```

### 2. AI 请求返回 401 Unauthorized

检查 `gateway.yaml` 中的 `apiKey` 是否正确配置。

### 3. 内存占用过高

调整 `gateway.yaml` 中的缓存配置：

```yaml
cache:
  maxSize: 10000   # 减小缓存大小
  ttlSeconds: 3600 # 缩短缓存 TTL
```

### 4. 熔断器频繁触发

调整熔断器配置：

```yaml
resilience:
  failureRateThreshold: 70    # 提高失败率阈值
  slowCallDurationThreshold: 30000  # 降低慢调用阈值
```

### 5. 限流不生效

确保流控过滤器已启用：

```yaml
filterConfigs:
  - name: flow_filter
    enable: true
```

## License

MIT License
