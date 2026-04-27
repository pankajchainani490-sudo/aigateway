package com.gcd.coding.gcdgatewaycore.netty.processor;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;

public interface NettyProcessor {

    void process(ChannelHandlerContext ctx, FullHttpRequest request);

}