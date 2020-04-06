package com.czarea.txlcn.study.member;

import com.codingapi.txlcn.tc.config.EnableDistributedTransaction;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.cloud.client.SpringCloudApplication;


/**
 * @author zhouzx
 */
@SpringCloudApplication
@EnableDistributedTransaction
@MapperScan("com.czarea.txlcn.study.**.mapper")
public class MemberApplication {

    public static void main(String[] args) {
        SpringApplication.run(MemberApplication.class, args);
    }
}
