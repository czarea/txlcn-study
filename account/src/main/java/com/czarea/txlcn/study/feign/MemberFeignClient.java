package com.czarea.txlcn.study.feign;

import com.czarea.txlcn.study.common.dto.PointDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * @author zhouzx
 */
@FeignClient("member")
public interface MemberFeignClient {

    @PostMapping("/member/point/add")
    int addPoint(@RequestBody PointDTO point);
}
