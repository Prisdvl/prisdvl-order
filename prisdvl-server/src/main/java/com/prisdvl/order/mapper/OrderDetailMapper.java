package com.prisdvl.order.mapper;

import com.prisdvl.order.entity.OrderDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderDetailMapper {
    /**
     * 批量插入订单明细数据
     * @param orderDetailList
     */
    void insertBatch(List<OrderDetail> orderDetailList);

    /**
     * 根据订单id查询订单明细
     * @param orderId
     * @return
     */
    @Select("select * from order_detail where order_id = #{orderId}")
    List<OrderDetail> getByOrderId(Long orderId);

    /**
     * 根据订单id集合批量查询订单明细，供订单列表一次性装配，避免逐单查询。
     *
     * @param orderIds 订单id集合
     * @return 订单明细集合
     */
    List<OrderDetail> getByOrderIds(@Param("orderIds") List<Long> orderIds);
}
