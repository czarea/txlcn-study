package com.czarea.txlcn.study.stock.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * @author zhouzx
 */
@Data
@TableName("t_product")
public class Product {

    @TableId
    private Long id;
    private String name;
    private String brief;
    private Integer store;
    private Date createTime;
    private Date updateTime;
}
