package com.czarea.txlcn.study.common.dto;

import java.util.Date;
import lombok.Data;

/**
 * @author zhouzx
 */
@Data
public class PointDTO {

    private Long id;
    private Long userId;
    private Long point;
    private Date createTime;

}
