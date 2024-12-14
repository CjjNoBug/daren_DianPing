package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient  {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(value),time,timeUnit);
    }
    //逻辑过期
//    public void setLogical(String key, Object value, Long time, TimeUnit timeUnit) {
//        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(value),time,timeUnit);
//    }

    /**
     *
     * @param prefix
     * @param id
     * @param type
     * @param dbFallBack
     * @return
     * @param <R>
     * @param <ID>
     *     Type parameters:
     * <T> – the type of the input to the function <R> – the type of the result of the function
     */
    public <R,ID> R queryWithPassThrough(
            String prefix, ID id, Class<R> type,Long time, TimeUnit timeUnit, Function<ID,R> dbFallBack){
        //从redis查询缓存
        String key=prefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //存在直接返回
        if(StrUtil.isNotBlank(json)){
            return JSONUtil.toBean(json, type);
        }
        //判断是否是空值
        if(json==null||json==""){
            return null;
        }
        //不存在，查询数据库
        R r = dbFallBack.apply(id);
        //数据库也没有，返回错误
        if(r==null){
            stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        //数据库存在，将数据返回到缓存存下来

        this.set(key,r,time,timeUnit);
        //返回成功
        return r;

    }
}
