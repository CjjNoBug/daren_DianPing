package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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

    @Override
    public Result queryById(Long id) {
        //从redis查询缓存
        String shopKey=RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        //存在直接返回
        if(StrUtil.isNotBlank(shopJson)){
            Shop shopBean = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shopBean);
        }
        //不存在，查询数据库
        Shop shop = getById(id);
        //数据库也没有，返回错误
        if(shop==null){
            return Result.fail("店铺不存在");
        }
        //数据库存在，将数据返回到缓存存下来

        stringRedisTemplate.opsForValue().set(shopKey,JSONUtil.toJsonStr(shop));
        //返回成功
        return Result.ok(shop);
    }
}
