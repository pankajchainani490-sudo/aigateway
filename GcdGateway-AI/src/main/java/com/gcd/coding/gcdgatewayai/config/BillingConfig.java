package com.gcd.coding.gcdgatewayai.config;

import lombok.Data;

/**
 * 计费配置 - 控制AI请求的计费功能开关和日志
 *
 * 功能说明：
 * 配置AI网关的计费功能，包括是否启用、货币类型、日志开关等。
 * 当enabled=true时，AIBillingFilter会记录每次请求的计费信息。
 *
 * 配置项：
 * - enabled：计费功能开关
 * - currency：货币类型（默认CNY）
 * - logEnabled：是否将计费明细写入日志
 *
 * @see AIBillingFilter 计费过滤器，使用此配置
 * @see AIBillingRecord 计费记录类
 */
@Data
public class BillingConfig {

    /** 计费功能开关，默认为true（启用） */
    private boolean enabled = true;

    /** 货币类型，默认为"CNY"（人民币） */
    private String currency = "CNY";

    /** 是否将计费明细写入日志，默认为true */
    private boolean logEnabled = true;

}