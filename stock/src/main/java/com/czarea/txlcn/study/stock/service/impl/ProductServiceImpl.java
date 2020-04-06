package com.czarea.txlcn.study.stock.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codingapi.txlcn.tc.annotation.LcnTransaction;
import com.czarea.txlcn.study.common.dto.ProductDTO;
import com.czarea.txlcn.study.stock.entity.Product;
import com.czarea.txlcn.study.stock.mapper.ProductMapper;
import com.czarea.txlcn.study.stock.service.ProductService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author zhouzx
 */
@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    @Transactional
    @LcnTransaction
    @Override
    public int reduce(ProductDTO productDTO) {
        return baseMapper.reduct(productDTO);
    }
}
