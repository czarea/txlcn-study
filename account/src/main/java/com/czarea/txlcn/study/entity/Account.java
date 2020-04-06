package com.czarea.txlcn.study.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * @author zhouzx
 */
@Data
@TableName("t_consume_log")
public class Account {

    @TableId
    private Long id;
    private Long userId;
    private Double amount;
    private Date createTime;
}
