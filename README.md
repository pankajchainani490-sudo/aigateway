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
      enabled: false              # 是否启用配置中心
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
# 测试 AI 聊天接口
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

## License

MIT License
