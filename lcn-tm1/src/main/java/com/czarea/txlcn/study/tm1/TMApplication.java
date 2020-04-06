package com.czarea.txlcn.study.tm1;

import com.codingapi.txlcn.tm.config.EnableTransactionManagerServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author zhouzx
 */
@SpringBootApplication
@EnableTransactionManagerServer
public class TMApplication {

    public static void main(String[] args) {
        SpringApplication.run(TMApplication.class, args);
    }
}
