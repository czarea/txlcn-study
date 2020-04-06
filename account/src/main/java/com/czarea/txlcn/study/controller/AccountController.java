package com.czarea.txlcn.study.controller;

import com.czarea.txlcn.study.service.AccountService;
import org.springframework.web.bind.annotation.Mapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author zhouzx
 */
@RestController
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @RequestMapping("/consume")
    public String consume(Long userId, Double amount,Long productId) throws IllegalAccessException {
        accountService.consume(amount, userId,productId);
        return "OK";
    }
}
