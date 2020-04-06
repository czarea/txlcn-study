package com.czarea.txlcn.study.stock.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.czarea.txlcn.study.common.dto.ProductDTO;
import com.czarea.txlcn.study.stock.entity.Product;

/**
 * @author zhouzx
 */
public interface ProductService extends IService<Product> {

    int reduce(ProductDTO productDTO);

    int tccReduce(ProductDTO productDTO);

    int cancelTccReduce(ProductDTO productDTO);

    int confirmTccReduce(ProductDTO productDTO);
}
