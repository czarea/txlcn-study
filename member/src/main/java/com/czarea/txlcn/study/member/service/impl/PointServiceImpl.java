package com.czarea.txlcn.study.member.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codingapi.txlcn.tc.annotation.TxcTransaction;
import com.czarea.txlcn.study.member.entity.Point;
import com.czarea.txlcn.study.member.mapper.PointMapper;
import com.czarea.txlcn.study.member.service.PointService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author zhouzx
 */
@Service
public class PointServiceImpl extends ServiceImpl<PointMapper, Point> implements PointService {

    @Transactional
    @TxcTransaction
    @Override
    public int txcSave(Point point) {
        baseMapper.insert(point);
        return 1;
    }
}
