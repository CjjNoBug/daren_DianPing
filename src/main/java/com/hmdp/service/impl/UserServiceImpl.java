package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if(RegexUtils.isPhoneInvalid(phone)) {
            //不符合，返回错误信息
            return Result.fail("手机号码无效");
        }
        //手机号正确，生成验证码
        String code = RandomUtil.randomNumbers(6);
        //保存验证码到session
//        session.setAttribute("code", code);
        //保存带redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //发送验证码
        log.debug("验证码已发送:{}",code);
        return Result.ok();
    }

    @Override
    public Result loginForm(LoginFormDTO loginForm, HttpSession session) {
        String phone=loginForm.getPhone();
        //校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号码无效");
        }
        //校验验证码
        String Cachecode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        String code=loginForm.getCode();
        //不一致报错
        if(Cachecode==null || !Cachecode.equals(code)){
            return Result.fail("验证码错误");
        }

        //检查手机号是否存在

        User user = query().eq("phone", phone).one();
        //不存在，创建用户并保存
        if(user==null){
            user = CreateUserByPhone(phone);
        }
        //存在，保存用户到redis
        //随机生成token，作为登录令牌
        String token= UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user,UserDTO.class);
        //TODO 难点
        Map<String, Object> UserDTOMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((FieldName,FieldValue)->FieldValue.toString()));
        //将用户转为Hash存储
        String TokenKey=LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(TokenKey,UserDTOMap);
        //设置有效期
        stringRedisTemplate.expire(TokenKey,CACHE_SHOP_TTL, TimeUnit.MINUTES);


        //返回token给前端
        return Result.ok(token);
    }

    private User CreateUserByPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setPassword(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
