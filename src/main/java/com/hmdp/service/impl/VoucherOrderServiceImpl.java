package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    //注入ID生成器
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Override
    public Result secKillVocher(Long voucherId) {
        //查询优惠券
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        if(seckillVoucher == null){
            return Result.fail("秒杀券不存在");
        }
        if(seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("秒杀尚未开始!");
        }
        if(seckillVoucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("秒杀已经结束!");
        }
        if(seckillVoucher.getStock()<1){
            return Result.fail("库存不足");
        }
        //一人一单
        Long id = UserHolder.getUser().getId();
        //创建锁对象
        SimpleRedisLock simpleLock = new SimpleRedisLock("VoucherOrder:" + id, stringRedisTemplate);
        boolean islock = simpleLock.tryLock(10);
        if(!islock){
            return Result.fail("一人一单!!!");
        }
        try {
            //获取代理对象
            IVoucherOrderService proxy =(IVoucherOrderService) AopContext.currentProxy();
            return proxy.CreateOrder(voucherId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            simpleLock.unlock();
        }

    }
    @Transactional(rollbackFor = Exception.class)
    public Result CreateOrder(Long voucherId){
        //一人一单
        Long id = UserHolder.getUser().getId();
            int count = query().eq("user_id", id).eq("voucher_id", voucherId).count();
            if (count > 0) {
                return Result.fail("已经购买过了!");
            }
            //扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId).gt("stock", 0)
                    .update();
            if (!success) {
                return Result.fail("库存不足");
            }
            //创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            long OrderId = redisIdWorker.nextId("voucherOrder");
            voucherOrder.setId(OrderId);

            voucherOrder.setUserId(id);
            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);
            return Result.ok(OrderId);
        }
    }


