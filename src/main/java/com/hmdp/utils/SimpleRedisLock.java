package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }
    private static final String KEY_PREFIX=RedisConstants.SETNX;
    private static final String ID_PREFIX= UUID.randomUUID().toString()+"-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT=new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }
    @Override
    public boolean tryLock(long timeoutSec) {
        //获取线程 标识/ID
        String threadId =ID_PREFIX+Thread.currentThread().getId();
        //stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX+name,threadId,timeoutSec, TimeUnit.SECONDS);
        //获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX+name,threadId,timeoutSec, TimeUnit.SECONDS);
        //Boolean->boolean,自动拆箱，可能返回空指针当值为null
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        //调用lua脚本
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX+name),
                ID_PREFIX+Thread.currentThread().getId()
                );
    }
//    @Override
//    public void unlock() {
//        //获取线程标识
//        String threadId =ID_PREFIX+Thread.currentThread().getId();
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        //判断标识是否一致
//        if(threadId.equals(id)){
//            //释放锁
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//
//    }
}
