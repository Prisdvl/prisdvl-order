package com.prisdvl.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 订单状态数量聚合结果，用于一次 group by 替代多次 count 查询。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusCountDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 订单状态：2待接单 3已接单 4派送中
     */
    private Integer status;

    /**
     * 该状态下的订单数量
     */
    private Integer orderCount;
}
