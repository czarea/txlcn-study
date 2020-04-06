package com.czarea.txlcn.study.member.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * @author zhouzx
 */
@Data
@TableName("t_point_log")
public class Point {

    @TableId
    private Long id;
    private Long userId;
    private Integer point;
    private Date createTime;
}
