package com.prisdvl.order.config;

import com.prisdvl.order.entity.DishFlavor;
import com.prisdvl.order.vo.DishVO;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 校验 Spring Cache 的 JSON 序列化器能正确往返 DishVO（含 LocalDateTime/嵌套口味）。
 * 不依赖真实 Redis，避免本地无 Redis 时测试无法执行。
 */
class CacheSerializerTest {

    @Test
    void shouldRoundTripDishVoList() throws Exception {
        RedisConfiguration configuration = new RedisConfiguration();
        Method method = RedisConfiguration.class.getDeclaredMethod("cacheSerializer");
        method.setAccessible(true);
        GenericJackson2JsonRedisSerializer serializer =
                (GenericJackson2JsonRedisSerializer) method.invoke(configuration);

        DishFlavor flavor = DishFlavor.builder()
                .id(1L)
                .dishId(10L)
                .name("辣度")
                .value("[\"不辣\",\"微辣\"]")
                .build();
        DishVO dish = DishVO.builder()
                .id(10L)
                .name("宫保鸡丁")
                .categoryId(100L)
                .price(new BigDecimal("28.00"))
                .status(1)
                .updateTime(LocalDateTime.of(2026, 11, 12, 17, 30))
                .flavors(Collections.singletonList(flavor))
                .build();
        List<DishVO> source = new ArrayList<>();
        source.add(dish);

        byte[] bytes = serializer.serialize(source);
        Object target = serializer.deserialize(bytes);

        assertInstanceOf(List.class, target);
        List<?> list = (List<?>) target;
        assertEquals(1, list.size());
        DishVO restored = assertInstanceOf(DishVO.class, list.get(0));
        assertEquals("宫保鸡丁", restored.getName());
        assertEquals(new BigDecimal("28.00"), restored.getPrice());
        assertEquals(LocalDateTime.of(2026, 11, 12, 17, 30), restored.getUpdateTime());
        assertEquals(1, restored.getFlavors().size());
        assertEquals("辣度", restored.getFlavors().get(0).getName());
        assertTrue(restored.getFlavors().get(0).getValue().contains("微辣"));
    }
}
