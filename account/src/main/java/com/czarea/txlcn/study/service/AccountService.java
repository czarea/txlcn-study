package com.czarea.txlcn.study.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.czarea.txlcn.study.entity.Account;

/**
 * @author zhouzx
 */
public interface AccountService extends IService<Account> {

    void consume(double amount, Long userId, Long productId) throws IllegalAccessException;
}
