package com.czarea.txlcn.study.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codingapi.txlcn.tc.annotation.LcnTransaction;
import com.czarea.txlcn.study.common.dto.PointDTO;
import com.czarea.txlcn.study.common.dto.ProductDTO;
import com.czarea.txlcn.study.entity.Account;
import com.czarea.txlcn.study.feign.MemberFeignClient;
import com.czarea.txlcn.study.feign.StockFeignClient;
import com.czarea.txlcn.study.mapper.AccountMapper;
import com.czarea.txlcn.study.service.AccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author zhouzx
 */
@Service
public class AccountServiceImpl extends ServiceImpl<AccountMapper, Account> implements AccountService {

    private final MemberFeignClient memberFeignClient;
    private final StockFeignClient stockFeignClient;

    public AccountServiceImpl(MemberFeignClient memberFeignClient, StockFeignClient stockFeignClient) {
        this.memberFeignClient = memberFeignClient;
        this.stockFeignClient = stockFeignClient;
    }

    @LcnTransaction
    @Override
    public void consume(double amount, Long userId, Long productId) throws IllegalAccessException {
        Account entity = new Account();
        entity.setAmount(amount);
        entity.setUserId(userId);
        baseMapper.insert(entity);

        PointDTO point = new PointDTO();
        point.setUserId(userId);
        point.setPoint((long) (amount * 100));
        memberFeignClient.addPoint(point);

        ProductDTO productDTO = new ProductDTO();
        productDTO.setId(productId);
        productDTO.setNumber(5);

        stockFeignClient.reduce(productDTO);

        if (point.getPoint() % 7 == 0) {
            throw new IllegalAccessException("mock exception!");
        }
    }

    @LcnTransaction
    @Override
    public void tccConsume(Double amount, Long userId, Long productId) throws IllegalAccessException {
        Account entity = new Account();
        entity.setAmount(amount);
        entity.setUserId(userId);
        baseMapper.insert(entity);

        int point = (int) (amount * 10);

        ProductDTO productDTO = new ProductDTO();
        productDTO.setId(productId);
        productDTO.setNumber(5);
        stockFeignClient.tccReduce(productDTO);

        if (point % 7 == 0) {
            throw new IllegalAccessException("mock exception!");
        }
    }

    @LcnTransaction
    @Override
    public void txcConsume(Double amount, Long userId, Long productId) throws IllegalAccessException {
        Account entity = new Account();
        entity.setAmount(amount);
        entity.setUserId(userId);
        baseMapper.insert(entity);

        PointDTO point = new PointDTO();
        point.setUserId(userId);
        point.setPoint((long) (amount * 100));
        memberFeignClient.txcAddPoint(point);

        if (point.getPoint() % 7 == 0) {
            throw new IllegalAccessException("mock exception!");
        }
    }
}
