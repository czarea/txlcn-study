package com.czarea.txlcn.study.stock.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codingapi.txlcn.tc.annotation.LcnTransaction;
import com.codingapi.txlcn.tc.annotation.TccTransaction;
import com.czarea.txlcn.study.common.dto.ProductDTO;
import com.czarea.txlcn.study.stock.entity.Product;
import com.czarea.txlcn.study.stock.mapper.ProductMapper;
import com.czarea.txlcn.study.stock.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author zhouzx
 */
@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    private static final Logger logger = LoggerFactory.getLogger(ProductServiceImpl.class);

    @Transactional
    @LcnTransaction
    @Override
    public int reduce(ProductDTO productDTO) {
        return baseMapper.reduct(productDTO);
    }

    @TccTransaction
    @Transactional
    @Override
    public int tccReduce(ProductDTO productDTO) {
        logger.info("try transaction !!!!");
        return 1;
    }

    @Override
    public int cancelTccReduce(ProductDTO productDTO) {
        logger.info("cancel ..............");
        return baseMapper.cancelReduce(productDTO);
    }

    @Override
    public int confirmTccReduce(ProductDTO productDTO) {
        return baseMapper.reduct(productDTO);
    }


}
