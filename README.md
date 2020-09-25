# spring-boot-redis-lock

#### 介绍
基于spring-boot-data-redis的分布式锁（注解方式实现加锁）


#### 安装教程

maven 打包引入即可

#### 使用说明

在需要同步的方法上加@DistributeLock(lockKey = "theLockKey"(锁键redisKey), exSecond = 10L(过期时间))

