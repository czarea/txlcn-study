package com.czarea.txlcn.study.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czarea.txlcn.study.common.dto.ProductDTO;
import com.czarea.txlcn.study.stock.entity.Product;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * @author zhouzx
 */
public interface ProductMapper extends BaseMapper<Product> {

    @Update("update t_product t set t.store=t.store-#{p.number} where t.id=#{p.id}")
    int reduct(@Param("p") ProductDTO productDTO);
}
