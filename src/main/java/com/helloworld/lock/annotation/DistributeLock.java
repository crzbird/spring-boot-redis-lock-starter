package com.helloworld.lock.annotation;

import org.springframework.lang.Nullable;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.lang.annotation.*;

/**
 * @program: spring-boot-redis-lock
 * @description: annotation use for aop
 * @author: crzbird
 * @create: 2020-02-04 17:19
 **/
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributeLock {

    //the redis lock key,default method name.
    @Nullable
    String lockKey();

    //expire time millionSec unit
    @Nullable
    long expire() default 6000L;

    //try to get lock interval , default 50 ms.
    @Nullable
    long tryInterval() default 50;


}
