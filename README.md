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

### 4. 启动后端服务 (可选)

```bash
# 启动用户服务
mvn spring-boot:run -pl GcdGateway-User

# 启动订单服务
mvn spring-boot:run -pl GcdGateway-Order
```

## 配置说明

### 网关配置 (gateway.yaml)

```yaml
gcd:
  gateway:
    name: gcd-ai-gateway          # 网关名称
    port: 10080                   # 网关端口
    configCenter:
      enabled: false              # 是否启用配置中心（本地yaml模式）
      type: NACOS                 # 配置中心类型
      address: 127.0.0.1:8848     # 配置中心地址
    registerCenter:
      type: NACOS                 # 注册中心类型
      address: 127.0.0.1:8848     # 注册中心地址
    globalCorsConfig:
      allowOrigin: "*"            # 允许的源
      allowMethods: "GET,POST,OPTIONS"
      allowHeaders: "Content-Type, Authorization"
      maxAge: 86400
    routes:                       # 路由配置
      - id: ai-chat-route
        serviceName: ai-chat-service
        uri: /api/ai/**
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

### 服务配置 (application.yml)

```yaml
server:
  port: 10001

spring:
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848  # Nacos 地址
  application:
    name: user-service              # 服务名称
```

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
  "maxTokens": 1000,                  // 最大输出 tokens (可选)
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
# 测试基本对话功能
curl -X POST http://127.0.0.1:10080/api/ai/chat \
  -H "Content-Type: application/json" \
  -d '{
    "model": "deepseek-chat",
    "messages": [{"role": "user", "content": "Hello"}]
  }'
```

**预期响应**：
```json
{
  "id": "chatcmpl-xxx",
  "object": "chat.completion",
  "created": 1712500000,
  "model": "deepseek-v4-flash",
  "choices": [{
    "index": 0,
    "message": {
      "role": "assistant",
      "content": "Hello! How can I help you today?"
    },
    "finishReason": "stop"
  }],
  "usage": {
    "promptTokens": 5,
    "completionTokens": 12,
    "totalTokens": 17
  }
}
```

### 2. 流式响应测试

```bash
# 测试SSE流式输出
curl -X POST http://127.0.0.1:10080/api/ai/chat \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "model": "deepseek-chat",
    "messages": [{"role": "user", "content": "写一首诗"}],
    "stream": true
  }'
```

**预期响应**（SSE格式）：
```
data: {"choices":[{"index":0,"delta":{"role":"assistant","content":"春"},"finishReason":null}]}

data: {"choices":[{"index":0,"delta":{"content":"眠"},"finishReason":null}]}

data: {"choices":[{"index":0,"delta":{"content":"不"},"finishReason":null}]}

data: {"choices":[{"index":0,"delta":{"content":"觉"},"finishReason":null}]}

data: {"choices":[{"index":0,"delta":{"content":"晓"},"finishReason":null}]}

data: {"choices":[{"index":0,"delta":{"content":""},"finishReason":"stop"}]}
```

### 3. 多轮对话测试

```bash
# 测试多轮对话上下文
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

### 4. 计费功能测试

确保`billing.enabled: true`后，请求会自动记录计费信息：

```bash
# 查看计费日志
tail -f logs/billing.log

# 发送请求后，日志会记录类似：
# [Billing] user=xxx, model=deepseek-chat, promptTokens=50, completionTokens=100, totalTokens=150, cost=0.25 CNY
```

### 5. 缓存功能测试

**精确匹配缓存**：
```bash
# 发送相同请求两次，第二次应该命中缓存
curl -X POST http://127.0.0.1:10080/api/ai/chat \
  -H "Content-Type: application/json" \
  -d '{
    "model": "deepseek-chat",
    "messages": [{"role": "user", "content": "今天天气怎么样"}]
  }'
```

## Nacos AI 配置详解

### 方式一：Nacos配置中心（推荐生产环境使用）

当`configCenter.enabled: true`时，AI配置可从Nacos动态获取，支持配置变更实时生效。

#### 1. 在Nacos中创建配置

**Data ID**: `gcd-ai-gateway`（可自定义，通过`nacos.aiDataId`配置）
**Group**: `DEFAULT_GROUP`（可自定义，通过`nacos.aiGroup`配置）
**配置格式**: JSON

#### 2. Nacos中AI配置的完整JSON格式

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
      "apiKey": "sk-your-api-key-here",
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
      "apiKey": "sk-your-openai-api-key",
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
      "modelName": "deepseek-reasoner",
      "providerName": "deepseek",
      "providerModelId": "deepseek-reasoner",
      "inputPricePer1KTokens": 0.004,
      "outputPricePer1KTokens": 0.008,
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

#### 3. gateway.yaml中的Nacos配置

```yaml
gcd:
  gateway:
    configCenter:
      enabled: true              # 启用Nacos配置中心
      type: NACOS
      address: 127.0.0.1:8848
      nacos:
        dataId: gcd-gateway          # 路由配置dataId
        group: DEFAULT_GROUP
        aiDataId: gcd-ai-gateway    # AI配置dataId（可选）
        aiGroup: DEFAULT_GROUP        # AI配置group（可选）
```

### 方式二：本地YAML配置（开发环境）

当`configCenter.enabled: false`时，使用本地gateway.yaml中的AI配置。

```yaml
gcd:
  gateway:
    configCenter:
      enabled: false  # 使用本地配置
    ai:
      # AI配置直接写在gateway.yaml中
```

### 配置优先级

```
Nacos配置中心 > 本地gateway.yaml
```

当Nacos配置中心启用时：
1. 先加载本地gateway.yaml作为基础配置
2. 如果Nacos可用，从Nacos拉取AI配置并覆盖本地配置
3. 当Nacos中配置变更时，自动重新加载并生效

### 配置热更新

修改Nacos中的AI配置后，网关会自动感知变更并重新初始化，无需重启服务。

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
# 检查 Nacos 是否运行
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

### 4. Nacos配置未生效

1. 确认`configCenter.enabled: true`
2. 确认Nacos中配置的DataId和Group与gateway.yaml中一致
3. 检查Nacos配置格式是否为有效的JSON

### 5. 缓存未命中

- exact模式：请求参数需完全一致
- embedding模式：检查`similarityThreshold`阈值是否过高

## License

MIT License
