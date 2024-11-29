package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        //缓存穿透
        Shop shop=cacheClient.queryWithPassThrough(
                RedisConstants.CACHE_SHOP_KEY,id,Shop.class,RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES,this::getById);

        //互斥锁解决缓存击穿
//        Shop shop=queryWithMutex(id);
        if(shop==null){
            return Result.fail("店铺不存在");
        }
        //返回成功
        return Result.ok(shop);
    }
    public Shop queryWithMutex(Long id){
//从redis查询缓存
        String shopKey=RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        //存在直接返回
        if(StrUtil.isNotBlank(shopJson)){
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //判断是否是空值
        if(shopJson==null||shopJson==""){
            return null;
        }
        String lockKey=RedisConstants.LOCK_SHOP_KEY + id;
        Shop shop = null;

        try {
            //获取锁
            boolean lock = tryLock(lockKey);
            //未获取，休眠一段时间重新尝试
            if(!lock){
                    Thread.sleep(RedisConstants.CACHE_SHOP_TTL);
                    return queryWithMutex(id);
            }
            //获取成功，查询数据库
            shop = getById(id);
            //数据库也没有，返回错误,存入空值
            if(shop==null){
                stringRedisTemplate.opsForValue().set(shopKey,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            //数据库存在，将数据返回到缓存存下来
            stringRedisTemplate.opsForValue().set(shopKey,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            delLock(lockKey);
        }
        //释放互斥锁

        //返回成功
        return shop;

    }
//    public Shop queryWithPassThrough(Long id){
//        //从redis查询缓存
//        String shopKey=RedisConstants.CACHE_SHOP_KEY + id;
//        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
//        //存在直接返回
//        if(StrUtil.isNotBlank(shopJson)){
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        //判断是否是空值
//        if(shopJson==null||shopJson==""){
//            return null;
//        }
//        //不存在，查询数据库
//        Shop shop = getById(id);
//        //数据库也没有，返回错误
//        if(shop==null){
//            stringRedisTemplate.opsForValue().set(shopKey,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
//            return null;
//        }
//        //数据库存在，将数据返回到缓存存下来
//
//        stringRedisTemplate.opsForValue().set(shopKey,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        //返回成功
//        return shop;
//
//    }

    private boolean tryLock(String key){
       boolean bl= stringRedisTemplate.opsForValue().setIfAbsent(key,"1",RedisConstants.LOCK_SHOP_TTL,TimeUnit.SECONDS);
       return BooleanUtil.isTrue(bl);
    }
    private void delLock(String key){
       stringRedisTemplate.delete(key);
    }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        if(shop.getId()==null){
            return Result.fail("店铺不存在");
        }
        String shopKey=RedisConstants.CACHE_SHOP_KEY + shop.getId();
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(shopKey);
        return Result.ok();
    }
}
