package com.czarea.txlcn.study.stock.controller;

import com.czarea.txlcn.study.common.dto.ProductDTO;
import com.czarea.txlcn.study.stock.service.ProductService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author zhouzx
 */
@RestController
@RequestMapping("/product")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping("/reduce")
    public int reduce(@RequestBody ProductDTO productDTO) {
        return productService.reduce(productDTO);
    }

    @PostMapping("/tcc/reduce")
    public int tccReduce(@RequestBody ProductDTO productDTO) {
        return productService.tccReduce(productDTO);
    }

}
