package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result getShopList() {
        List<String> shopList = stringRedisTemplate.opsForList().range(RedisConstants.CACHE_SHOP_LIST_KEY, 0, -1);
        log.info("shopList.size:{}",shopList.size());
        if(shopList.size() != 0){
            List<ShopType> shopTypeList = new ArrayList<>();
            for(String shop : shopList){
               ShopType s= JSONUtil.toBean(shop, ShopType.class);
               shopTypeList.add(s);
            }

            return Result.ok(shopTypeList);
        }
        List<ShopType> typeList =query().orderByAsc("sort").list();
        log.info("typeList.size:{}",typeList.size());
        if(typeList == null && typeList.size() == 0){
            return Result.fail("shop list 不存在");
        }
        for(ShopType shopType : typeList){
            String shopStr = JSONUtil.toJsonStr(shopType);
            stringRedisTemplate.opsForList().rightPush(RedisConstants.CACHE_SHOP_LIST_KEY, shopStr);
        }
        return Result.ok(typeList);
    }
}
