package com.gcd.coding.gcdgatewayai.filter;

// ========== 导入部分 ==========

// Caffeine缓存库 - 用于本地API-Key元数据缓存
import com.github.benmanes.caffeine.cache.Caffeine;

// AI模块的API-Key元数据类 - 存储用户配额和计划信息
import com.gcd.coding.gcdgatewayai.metadata.ApiKeyMetadata;

// 网关上下文 - 在整个过滤器链中传递请求/响应数据
import com.gcd.coding.gcdgatewaycore.context.GatewayContext;

// 过滤器接口 - 所有过滤器必须实现此接口
import com.gcd.coding.gcdgatewaycore.filter.Filter;

// 网关响应 - 用于构建返回给客户端的响应
import com.gcd.coding.gcdgatewaycore.response.GatewayResponse;

// Netty HTTP相关类 - 用于设置响应头和状态码
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;

// Lombok日志注解 - 自动生成log字段
import lombok.extern.slf4j.Slf4j;

// Java并发工具类
import java.util.Map;                        // 存储API-Key到元数据的映射
import java.util.concurrent.ConcurrentHashMap; // 线程安全的HashMap实现
import java.util.concurrent.TimeUnit;         // 时间单位转换
import java.util.concurrent.atomic.AtomicInteger; // 原子整数，用于计数

// 过滤器名称常量引用
import static com.gcd.coding.gcdgatewaycommon.constant.FilterConstant.AUTH_FILTER_NAME;
// 过滤器执行顺序常量引用
import static com.gcd.coding.gcdgatewaycommon.constant.FilterConstant.AUTH_FILTER_ORDER;

/**
 * 鉴权过滤器 - AI网关的API-Key认证层
 *
 * 功能说明：
 * 1. 从HTTP请求头中提取API-Key（支持X-API-Key和Authorization: Bearer两种方式）
 * 2. 先查询Caffeine本地缓存（60秒过期），未命中则查远程服务（本实现为模拟）
 * 3. 验证API-Key的有效性、过期时间、剩余配额
 * 4. 将ApiKeyMetadata存入GatewayContext，供后续过滤器使用
 *
 * 过滤器顺序：Integer.MIN_VALUE + 1，在跨域过滤器之后，流控过滤器之前
 */
@Slf4j  // Lombok自动生成log字段
public class AuthFilter implements Filter {

    // ========== 常量定义 ==========

    /** API-Key请求头名称 */
    private static final String API_KEY_HEADER = "X-API-Key";

    /** Authorization请求头名称 */
    private static final String AUTH_HEADER = "Authorization";

    /** Bearer令牌前缀，用于Authorization: Bearer <token>格式 */
    private static final String BEARER_PREFIX = "Bearer ";

    // ========== 缓存定义 ==========

    /**
     * API-Key本地缓存
     * Key: API-Key字符串
     * Value: ApiKeyMetadata元数据对象
     * 说明：使用ConcurrentHashMap保证线程安全
     */
    private final Map<String, ApiKeyMetadata> apiKeyCache = new ConcurrentHashMap<>();

    /**
     * 本地请求计数器（当前未使用，预留功能）
     * 用于统计每个API-Key的请求次数，实现限流
     */
    private final Map<String, AtomicInteger> localRequestCounts = new ConcurrentHashMap<>();

    // ========== 构造函数 ==========

    /**
     * 默认构造函数
     * 使用默认配置初始化过滤器
     */
    public AuthFilter() {
        // 构造函数为空，所有初始化在字段定义时完成
    }

    // ========== 过滤器核心方法 ==========

    /**
     * 前置过滤器方法 - 在请求进入AI服务前执行
     *
     * 执行流程：
     * 1. 从请求头提取API-Key
     * 2. 校验API-Key是否存在
     * 3. 查询API-Key元数据（本地缓存/远程）
     * 4. 检查API-Key是否过期
     * 5. 将元数据存入上下文，继续过滤器链
     *
     * @param context 网关上下文，存储请求、响应、路由等信息
     */
    @Override
    public void doPreFilter(GatewayContext context) {
        // 第1步：从请求中提取API-Key
        String apiKey = extractApiKey(context);

        // 第2步：校验API-Key是否存在
        if (apiKey == null || apiKey.isEmpty()) {
            // API-Key缺失，返回401未授权响应
            sendUnauthorizedResponse(context, "Missing API key");
            return;
        }

        // 第3步：获取API-Key元数据（可能来自缓存或远程）
        ApiKeyMetadata metadata = getApiKeyMetadata(apiKey);

        // 第4步：校验API-Key元数据是否有效
        if (metadata == null) {
            // API-Key无效（远程验证失败），返回401
            sendUnauthorizedResponse(context, "Invalid API key");
            return;
        }

        // 第5步：检查API-Key是否过期
        if (metadata.isExpired()) {
            // API-Key已过期，返回403禁止访问
            sendForbiddenResponse(context, "API key expired");
            return;
        }

        // 第6步：认证成功，将元数据存入上下文供后续过滤器使用
        context.setApiKeyMetadata(metadata);
        log.debug("Auth filter passed for user: {}", metadata.getUserId());

        // 第7步：继续执行过滤器链的下一个过滤器
        context.doFilter();
    }

    /**
     * 后置过滤器方法 - 在AI服务返回响应后执行
     *
     * 说明：AuthFilter在此阶段不做任何操作，仅传递控制权
     * 实际使用场景：可在此记录审计日志、更新使用统计等
     *
     * @param context 网关上下文
     */
    @Override
    public void doPostFilter(GatewayContext context) {
        // 透传，不做任何处理
        context.doFilter();
    }

    // ========== Filter接口实现 ==========

    /**
     * 返回过滤器唯一标识名称
     * SPI机制通过此方法识别过滤器
     *
     * @return 过滤器名称常量 "auth_filter"
     */
    @Override
    public String mark() {
        return AUTH_FILTER_NAME;
    }

    /**
     * 返回过滤器执行顺序
     * 数值越小越早执行，用于过滤器排序
     *
     * @return 过滤器顺序常量，值为Integer.MIN_VALUE + 1
     */
    @Override
    public int getOrder() {
        return AUTH_FILTER_ORDER;
    }

    // ========== 私有辅助方法 ==========

    /**
     * 从HTTP请求头中提取API-Key
     *
     * 支持两种提取方式：
     * 1. X-API-Key: <key> - 直接在请求头中指定
     * 2. Authorization: Bearer <token> - OAuth2风格的Bearer令牌
     *
     * 优先级：优先从X-API-Key提取，若无则尝试Authorization头
     *
     * @param context 网关上下文，包含请求信息
     * @return 提取到的API-Key字符串，若不存在则返回null
     */
    private String extractApiKey(GatewayContext context) {
        // 优先方式1：从X-API-Key请求头提取
        String apiKey = context.getRequest().getHeaders().get(API_KEY_HEADER);
        if (apiKey != null && !apiKey.isEmpty()) {
            return apiKey;
        }

        // 次优方式2：从Authorization: Bearer <token>提取
        String authHeader = context.getRequest().getHeaders().get(AUTH_HEADER);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            // 提取Bearer后的令牌部分并去除首尾空格
            return authHeader.substring(BEARER_PREFIX.length()).trim();
        }

        // 两种方式都无法提取到API-Key
        return null;
    }

    /**
     * 获取API-Key的元数据
     *
     * 查询顺序：
     * 1. 先查本地ConcurrentHashMap缓存
     * 2. 缓存未命中则查远程服务（本实现为模拟）
     * 3. 远程获取后写入本地缓存
     *
     * @param apiKey API-Key字符串
     * @return API-Key对应的元数据对象，若无效则返回null
     */
    private ApiKeyMetadata getApiKeyMetadata(String apiKey) {
        // 第1步：查询本地缓存
        ApiKeyMetadata cached = apiKeyCache.get(apiKey);
        if (cached != null) {
            log.debug("API key found in local cache");
            return cached;
        }

        // 第2步：本地缓存未命中，查询远程服务
        ApiKeyMetadata metadata = fetchFromRemote(apiKey);

        // 第3步：远程获取成功，写入本地缓存
        if (metadata != null) {
            apiKeyCache.put(apiKey, metadata);
        }

        return metadata;
    }

    /**
     * 从远程服务获取API-Key元数据
     *
     * 当前实现为模拟实现：
     * - 实际项目中应调用用户中心/鉴权服务的HTTP接口
     * - 接口示例：GET /api/key/metadata?key=xxx
     *
     * @param apiKey API-Key字符串
     * @return API-Key元数据对象
     */
    private ApiKeyMetadata fetchFromRemote(String apiKey) {
        log.info("Fetching API key metadata from remote for key: {}...", maskApiKey(apiKey));
        // TODO: 实际项目中应调用远程HTTP API获取元数据
        // 目前使用模拟实现，直接从API-Key派生元数据
        return buildMetadataFromApiKey(apiKey);
    }

    /**
     * 根据API-Key内容派生元数据
     *
     * 这是一个模拟实现，实际项目应从数据库/远程服务获取真实数据
     * 派生规则：
     * - userId: API-Key前8字符的哈希值
     * - planType: 根据Key前缀判断 (sk-pro-→pro, sk-ent-→enterprise, 其他→free)
     * - quotaRemaining/QuotaTotal: 模拟配额值
     * - expiresAt: 24小时后过期
     *
     * @param apiKey API-Key字符串
     * @return 派生的元数据对象
     */
    private ApiKeyMetadata buildMetadataFromApiKey(String apiKey) {
        // 创建新的元数据对象
        ApiKeyMetadata metadata = new ApiKeyMetadata();

        // 存储原始API-Key
        metadata.setApiKey(apiKey);

        // 根据API-Key派生用户ID
        metadata.setUserId(deriveUserIdFromApiKey(apiKey));

        // 根据API-Key前缀判断套餐类型
        metadata.setPlanType(determinePlanType(apiKey));

        // 模拟剩余配额（实际应从数据库读取）
        metadata.setQuotaRemaining(1000);

        // 模拟总配额
        metadata.setQuotaTotal(1000);

        // 设置24小时后过期（实际应从API-Key本身解析或从数据库读取）
        metadata.setExpiresAt(System.currentTimeMillis() + 86400000L);

        return metadata;
    }

    /**
     * 从API-Key派生用户ID
     *
     * 实现逻辑：取API-Key前8个字符的哈希码作为用户标识
     * 注意：实际项目中userId应由用户中心统一分配
     *
     * @param apiKey API-Key字符串
     * @return 用户标识字符串，格式为 "user_哈希值"
     */
    private String deriveUserIdFromApiKey(String apiKey) {
        // 防御性检查：API-Key长度小于8时返回unknown
        if (apiKey == null || apiKey.length() < 8) {
            return "unknown";
        }
        // 取前8字符的哈希码作为用户标识
        return "user_" + apiKey.substring(0, 8).hashCode();
    }

    /**
     * 根据API-Key前缀判断套餐类型
     *
     * 套餐类型说明：
     * - sk-pro-: 专业版套餐 (Pro)
     * - sk-ent-: 企业版套餐 (Enterprise)
     * - 其他前缀: 免费版套餐 (Free)
     *
     * @param apiKey API-Key字符串
     * @return 套餐类型标识符
     */
    private String determinePlanType(String apiKey) {
        // 检查是否以sk-pro-开头（专业版）
        if (apiKey.startsWith("sk-pro-")) {
            return "pro";
        }
        // 检查是否以sk-ent-开头（企业版）
        else if (apiKey.startsWith("sk-ent-")) {
            return "enterprise";
        }
        // 默认返回免费版
        return "free";
    }

    /**
     * 脱敏API-Key，仅显示前4位和后4位
     *
     * 用于日志输出，避免完整Key泄露
     * 示例：sk-pro-xxxx1234xxxx5678 → sk-p****xxxx
     *
     * @param apiKey 原始API-Key
     * @return 脱敏后的Key
     */
    private String maskApiKey(String apiKey) {
        // 防御性检查：Key过短时返回脱敏符
        if (apiKey == null || apiKey.length() < 8) {
            return "****";
        }
        // 拼接：前4位 + **** + 后4位
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    // ========== 响应构建方法 ==========

    /**
     * 发送401未授权响应
     *
     * 使用场景：API-Key缺失或无效
     *
     * @param context 网关上下文
     * @param message 错误消息
     */
    private void sendUnauthorizedResponse(GatewayContext context, String message) {
        log.warn("Auth failed: {}", message);

        // 构建HTTP响应对象
        GatewayResponse response = new GatewayResponse();

        // 设置响应头：Content-Type为JSON，字符编码UTF-8
        response.addHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON + ";charset=utf-8");

        // 设置HTTP状态码：401 Unauthorized
        response.setHttpResponseStatus(HttpResponseStatus.UNAUTHORIZED);

        // 设置响应体：JSON格式的错误消息
        response.setContent("{\"error\":\"" + message + "\"}");

        // 将响应存入上下文
        context.setResponse(response);

        // 启用短路：后续过滤器不会执行，直接返回此响应
        context.setShortCircuit(true);
    }

    /**
     * 发送403禁止访问响应
     *
     * 使用场景：API-Key已过期、配额用尽、无权限访问等
     *
     * @param context 网关上下文
     * @param message 错误消息
     */
    private void sendForbiddenResponse(GatewayContext context, String message) {
        log.warn("Auth forbidden: {}", message);

        // 构建HTTP响应对象
        GatewayResponse response = new GatewayResponse();

        // 设置响应头：Content-Type为JSON，字符编码UTF-8
        response.addHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON + ";charset=utf-8");

        // 设置HTTP状态码：403 Forbidden
        response.setHttpResponseStatus(HttpResponseStatus.FORBIDDEN);

        // 设置响应体：JSON格式的错误消息
        response.setContent("{\"error\":\"" + message + "\"}");

        // 将响应存入上下文
        context.setResponse(response);

        // 启用短路：后续过滤器不会执行，直接返回此响应
        context.setShortCircuit(true);
    }
}
