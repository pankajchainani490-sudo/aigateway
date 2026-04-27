package com.gcd.coding.gcdgatewayorder.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class OrderController {

    @GetMapping("/api/order/ping1")
    public String ping1() {
        return "this is order ping1";
    }

    @GetMapping("/api/order/ping2")
    public String ping2() {
        return "this is order ping2";
    }

}
