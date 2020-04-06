package com.czarea.txlcn.study.stock;

import com.codingapi.txlcn.tc.config.EnableDistributedTransaction;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.cloud.client.SpringCloudApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * @author zhouzx
 */
@SpringCloudApplication
@EnableFeignClients
@EnableDistributedTransaction
@MapperScan("com.czarea.txlcn.study.stock.**.mapper")
public class StockApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockApplication.class, args);
    }
}
