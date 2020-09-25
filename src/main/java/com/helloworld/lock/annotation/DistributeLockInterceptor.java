package com.helloworld.lock.annotation;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.util.StringUtils;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @program: spring-boot-redis-lock
 * @description: Interceptor use for aop
 * @author: crzbird
 * @create: 2020-02-04 17:50
 **/
public class DistributeLockInterceptor implements MethodInterceptor {

    private final Logger LOGGER = LoggerFactory.getLogger(DistributeLockInterceptor.class);

    //LUA script for macking delete action atomic,compare the lock value if true then delete it
    private static final String REDIS_LOCK_LUA = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "return redis.call('del', KEYS[1]) else return 0 end";

    //redisTemplate from ioc context
    private RedisTemplate redisTemplate;

    public DistributeLockInterceptor(RedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * if method annotate by DistributeLock.class then enhance it
     *
     * @param methodInvocation
     * @return
     * @throws Throwable
     */
    @Override
    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
        Method method = methodInvocation.getMethod();
        DistributeLock distributeLock = null;
        if (method.isAnnotationPresent(DistributeLock.class)) {
            distributeLock = method.getAnnotation(DistributeLock.class);
        }
        if (distributeLock == null) {
            return methodInvocation.proceed();
        }
        String lockKey = StringUtils.isEmpty(distributeLock.lockKey()) ? method.getName() : distributeLock.lockKey();
        String lockValue = UUID.randomUUID().toString();
        long expire = distributeLock.expire();
        long tryInterval = distributeLock.tryInterval();
        Object methodResult = null;
        try {
            tryLock(lockKey, lockValue, expire, tryInterval);
            methodResult = methodInvocation.proceed();
        } catch (Exception e) {
            LOGGER.error("err:", e);
        } finally {
            releaseLock(lockKey, lockValue);
        }
        return methodResult;
    }

    /**
     * try to get lock
     *
     * @param lockKey   redis lock key
     * @param lockValue redis lock value ,now it is UUID
     * @param expire    time to expire,default 6000 millionseconds
     */
    private void tryLock(String lockKey, String lockValue, long expire, long tryInterval) {
        try {
            redisTemplate.execute((RedisCallback) redisConnection -> {
                String value = null;
                do {
                    try {
                        TimeUnit.MILLISECONDS.sleep(tryInterval);
                    } catch (InterruptedException e) {
                        LOGGER.error("try lock error:", e);
                    }
                    byte[] valueBytes = redisConnection.get(lockKey.getBytes());
                    value = valueBytes == null ? null : new String(valueBytes);
                } while (!StringUtils.isEmpty(value) ||
                        !redisConnection.set(lockKey.getBytes(), lockValue.getBytes(),
                                Expiration.milliseconds(expire), RedisStringCommands.SetOption.SET_IF_ABSENT));
                System.out.println(Thread.currentThread().getName() + "\tget lock:\t" + lockKey +
                        "\tsuccess,expire in " + expire + " sec");
                return true;
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * release the lockl,compare the lockValue is belong current thread
     *
     * @param lockKey   redis lock key
     * @param lockValue redis lock value ,now it is UUID
     */
    public void releaseLock(String lockKey, String lockValue) {
        try {
            redisTemplate.execute((RedisCallback) redisConnection -> {
                byte[] valueBytes = redisConnection.get(lockKey.getBytes());
                String value = valueBytes == null ? null : new String(valueBytes);
                if (!StringUtils.isEmpty(value) && lockValue.equals(value)) {
                    if (!redisConnection.eval(REDIS_LOCK_LUA.getBytes(), ReturnType.INTEGER, 1, lockKey.getBytes(), lockValue.getBytes()).equals(0)) {
                        System.out.println(Thread.currentThread().getName() + "\trelease lock:\t" + lockKey);
                    } else {
                        System.out.println(Thread.currentThread().getName() + "\trelease lock:\t" + lockKey +
                                "\tfail,may it has been expire");
                    }
                } else {
                    System.out.println(Thread.currentThread().getName() + "\trelease lock:\t" + lockKey +
                            "\tfail,cause the lock dosen't exist or not belong current lock");
                }
                return true;
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
