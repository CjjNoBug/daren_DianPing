package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

//生成唯一id
@Component
public class RedisIdWorker {

    private static final long BEGIN_TIME=1712361600L;
    private static final int LEFT_SHIFT_COUNT=32;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String prefix){
        //生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp= nowSecond-BEGIN_TIME;
        //生成序列号,使用自增长
        //以日期为键，避免超过redis的上限
        String yyyyMMdd = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long increment = stringRedisTemplate.opsForValue().increment("Icr:" + prefix + ":" + yyyyMMdd);
        //拼接并返回,使用位运算

        return timestamp << LEFT_SHIFT_COUNT | increment;
    }


}
