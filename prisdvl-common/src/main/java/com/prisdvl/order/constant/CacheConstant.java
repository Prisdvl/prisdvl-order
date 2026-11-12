package com.prisdvl.order.constant;

/**
 * Spring Cache 缓存名称与 Redis key 约定。
 */
public final class CacheConstant {

    /**
     * 用户端菜品列表：key = 分类id，value = List&lt;DishVO&gt;
     */
    public static final String DISH_CACHE = "dish";

    /**
     * 用户端套餐列表：key = 分类id，value = List&lt;Setmeal&gt;
     */
    public static final String SETMEAL_CACHE = "setmeal";

    /**
     * 分类列表：key = 分类类型（1菜品 2套餐），value = List&lt;Category&gt;
     */
    public static final String CATEGORY_CACHE = "category";

    private CacheConstant() {
    }
}
