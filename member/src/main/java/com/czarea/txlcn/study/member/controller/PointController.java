package com.czarea.txlcn.study.member.controller;

import com.codingapi.txlcn.tc.annotation.DTXPropagation;
import com.codingapi.txlcn.tc.annotation.LcnTransaction;
import com.codingapi.txlcn.tc.annotation.TxcTransaction;
import com.czarea.txlcn.study.member.entity.Point;
import com.czarea.txlcn.study.member.service.PointService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author zhouzx
 */
@RestController
@RequestMapping("/member/point")
public class PointController {

    private final PointService pointService;

    public PointController(PointService pointService) {
        this.pointService = pointService;
    }


    @Transactional
    @LcnTransaction(propagation = DTXPropagation.REQUIRED)
    @PostMapping("/add")
    public int add(@RequestBody Point point) throws Exception {
        if (point.getPoint() % 3 == 0) {
            throw new Exception("throw exception !");
        }
        return pointService.save(point) ? 1 : 0;
    }

    @PostMapping("/txc/add")
    public int txcAdd(@RequestBody Point point) throws Exception {
        if (point.getPoint() % 3 == 0) {
            throw new Exception("throw exception !");
        }
        return pointService.txcSave(point);
    }
}
