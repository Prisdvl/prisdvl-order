package com.prisdvl.order.task;

import com.prisdvl.order.component.RedisDistributedLock;
import com.prisdvl.order.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 订单状态定时任务。
 *
 * <p>两个任务均按批执行 {@code UPDATE ... LIMIT}，并用 Redis 分布式锁保证多实例部署时
 * 同一时刻只有一个实例在处理，避免重复扫描与重复更新。</p>
 */
@Component
@Slf4j
public class OrderTask {

    /**
     * 单批处理数量，避免一次锁住过多行
     */
    private static final int BATCH_SIZE = 500;

    /**
     * 锁过期时间，防止实例宕机后死锁；任务本身是幂等的，锁过期后重复执行也不会重复扣减
     */
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);

    private static final String LOCK_PREFIX = "lock:order:task:";

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private RedisDistributedLock distributedLock;

    /**
     * 超过指定分钟仍未支付的订单自动取消，默认 15 分钟。
     */
    @Value("${sky.order.timeout-minutes:15}")
    private long timeoutMinutes;

    /**
     * 派送中超过指定分钟仍未完成的订单自动完成，默认 60 分钟。
     */
    @Value("${sky.order.delivery-timeout-minutes:60}")
    private long deliveryTimeoutMinutes;

    /**
     * 处理超时未支付订单，每分钟触发一次。
     */
    @Scheduled(cron = "0 * * * * ?")
    public void processTimeoutOrder() {
        runWithLock("timeout", () -> {
            LocalDateTime deadline = LocalDateTime.now().minusMinutes(timeoutMinutes);
            int total = 0;
            int rows;
            do {
                // 一条 UPDATE 批量取消一批，status = 1 条件保证不会误取消已支付订单
                rows = orderMapper.cancelTimeoutOrders(
                        deadline, "订单超时，自动取消", LocalDateTime.now(), BATCH_SIZE);
                total += rows;
            } while (rows == BATCH_SIZE);

            if (total > 0) {
                log.info("自动取消超时未支付订单 {} 条，截止时间：{}", total, deadline);
            }
        });
    }

    /**
     * 处理一直处于派送中状态的订单，每天凌晨 1 点触发一次。
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void processDeliveryOrder() {
        runWithLock("delivery", () -> {
            LocalDateTime deadline = LocalDateTime.now().minusMinutes(deliveryTimeoutMinutes);
            int total = 0;
            int rows;
            do {
                rows = orderMapper.completeDeliveryOrders(deadline, LocalDateTime.now(), BATCH_SIZE);
                total += rows;
            } while (rows == BATCH_SIZE);

            if (total > 0) {
                log.info("自动完成超时派送订单 {} 条，截止时间：{}", total, deadline);
            }
        });
    }

    /**
     * 多实例互斥执行：抢不到锁直接跳过，抢到锁后在 finally 中按持有者标识释放。
     */
    private void runWithLock(String biz, Runnable action) {
        String lockKey = LOCK_PREFIX + biz;
        String token;
        try {
            token = distributedLock.tryLock(lockKey, LOCK_TTL);
        } catch (Exception e) {
            // 锁依赖 Redis：拿不到锁时宁可跳过，也不在多实例下重复执行
            log.error("订单任务[{}]获取分布式锁失败，本次跳过", biz, e);
            return;
        }
        if (token == null) {
            log.info("订单任务[{}]正在其他实例执行，本次跳过", biz);
            return;
        }
        try {
            action.run();
        } catch (Exception e) {
            log.error("订单任务[{}]执行失败", biz, e);
        } finally {
            distributedLock.unlock(lockKey, token);
        }
    }
}
