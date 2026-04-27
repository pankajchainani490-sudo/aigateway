package com.gcd.coding.gcdgatewaycore.config;

import com.gcd.coding.gcdgatewayconfig.config.Config;
import com.gcd.coding.gcdgatewaycore.netty.NettyHttpClient;
import com.gcd.coding.gcdgatewaycore.netty.NettyHttpServer;
import com.gcd.coding.gcdgatewaycore.netty.processor.NettyCoreProcessor;

import java.util.concurrent.atomic.AtomicBoolean;

public class Container implements LifeCycle {

    private final NettyHttpServer nettyHttpServer;

    private final NettyHttpClient nettyHttpClient;

    private final AtomicBoolean start = new AtomicBoolean(false);


    public Container(Config config) {
        this.nettyHttpServer = new NettyHttpServer(config, new NettyCoreProcessor());
        this.nettyHttpClient = new NettyHttpClient(config);
    }

    @Override
    public void start() {
        if (!start.compareAndSet(false, true)) return;
        nettyHttpServer.start();
        nettyHttpClient.start();
    }

    @Override
    public void shutdown() {
        if (!start.get()) return;
        nettyHttpServer.shutdown();
        nettyHttpClient.shutdown();
    }

    @Override
    public boolean isStarted() {
        return start.get();
    }

}
