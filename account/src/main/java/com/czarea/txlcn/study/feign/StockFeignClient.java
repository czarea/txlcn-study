package com.czarea.txlcn.study.feign;

import com.czarea.txlcn.study.common.dto.ProductDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * @author zhouzx
 */
@FeignClient("stock")
public interface StockFeignClient {

    @PostMapping("/product/reduce")
    int reduce(@RequestBody ProductDTO productDTO);
}
