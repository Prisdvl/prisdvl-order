package com.prisdvl.order.mapper;

import com.github.pagehelper.Page;
import com.prisdvl.order.dto.GoodsSalesDTO;
import com.prisdvl.order.dto.OrderStatusCountDTO;
import com.prisdvl.order.dto.OrdersPageQueryDTO;
import com.prisdvl.order.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper {

    /**
     * 插入订单数据
     * @param orders
     */
    void insert(Orders orders);

    /**
     * 根据订单号查询订单（支付回调无登录上下文，不能依赖 ThreadLocal 中的用户id）
     *
     * @param orderNumber 商户订单号
     * @return 订单
     */
    @Select("select * from orders where number = #{orderNumber}")
    Orders getByNumber(String orderNumber);

    /**
     * 根据id和用户id查询订单，防止用户越权操作他人订单
     *
     * @param id     订单id
     * @param userId 用户id
     * @return 订单
     */
    @Select("select * from orders where id = #{id} and user_id = #{userId}")
    Orders getByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 支付成功状态流转：仅待付款订单可更新，重复回调返回 0 行。
     *
     * @param id           订单id
     * @param checkoutTime 结账时间
     * @return 受影响行数
     */
    @Update("update orders set status = 2, pay_status = 1, checkout_time = #{checkoutTime} " +
            "where id = #{id} and status = 1 and pay_status = 0")
    int paySuccess(@Param("id") Long id, @Param("checkoutTime") LocalDateTime checkoutTime);

    /**
     * 修改订单信息
     * @param orders
     */
    void update(Orders orders);

    /**
     * 分页条件查询并按下单时间排序
     * @param ordersPageQueryDTO
     */
    Page<Orders> pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 根据id查询订单
     * @param id
     */
    @Select("select * from orders where id=#{id}")
    Orders getById(Long id);

    /**
     * 一次 group by 统计待接单、已接单、派送中的订单数量，替代 3 次 count 查询。
     *
     * @return 状态-数量列表
     */
    @Select("select status, count(id) as order_count from orders " +
            "where status in (2, 3, 4) group by status")
    List<OrderStatusCountDTO> countGroupByStatus();

    /**
     * 批量取消超时未支付订单。
     *
     * <p>单条 {@code UPDATE ... WHERE status = 1 ... LIMIT n} 替代“查出来逐条 update”；
     * status 条件同时承担乐观锁作用，避免把刚支付的订单误取消。</p>
     *
     * @param deadline     下单时间截止线
     * @param cancelReason 取消原因
     * @param cancelTime   取消时间
     * @param limit        单批数量
     * @return 受影响行数
     */
    @Update("update orders set status = 6, cancel_reason = #{cancelReason}, cancel_time = #{cancelTime} " +
            "where status = 1 and order_time < #{deadline} order by id limit #{limit}")
    int cancelTimeoutOrders(@Param("deadline") LocalDateTime deadline,
                            @Param("cancelReason") String cancelReason,
                            @Param("cancelTime") LocalDateTime cancelTime,
                            @Param("limit") int limit);

    /**
     * 批量完成长时间处于派送中的订单。
     *
     * @param deadline     下单时间截止线
     * @param deliveryTime 完成时间
     * @param limit        单批数量
     * @return 受影响行数
     */
    @Update("update orders set status = 5, delivery_time = #{deliveryTime} " +
            "where status = 4 and order_time < #{deadline} order by id limit #{limit}")
    int completeDeliveryOrders(@Param("deadline") LocalDateTime deadline,
                               @Param("deliveryTime") LocalDateTime deliveryTime,
                               @Param("limit") int limit);

    /**
     * 根据动态条件统计营业额数据
     * @param map
     * @return
     */
    Double sumByMap(Map map);

    /**
     * 根据动态条件统计订单数量
     * @param map
     * @return
     */
    Integer countByMap(Map map);

    /**
     * 统计指定时间区间内的销量排名前10
     * @param begin
     * @param end
     * @return
     */
    List<GoodsSalesDTO> getSalesTop10(LocalDateTime begin,LocalDateTime end);
}
