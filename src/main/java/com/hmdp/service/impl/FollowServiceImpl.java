package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.sql.Wrapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IUserService userService;

    @Override
    public Result isFollow(Long id, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        //关注
        if(isFollow){

            Follow follow = new Follow();
            follow.setFollowUserId(id);
            follow.setUserId(userId);
            boolean isSuccess= save(follow);
            if(isSuccess){
                String followKey=RedisConstants.FOLLOWS+userId;
                stringRedisTemplate.opsForSet().add(followKey,id.toString());
            }
        }
        //取关
        else{

            boolean isSuccess = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", id));
            if(isSuccess){
                String followKey=RedisConstants.FOLLOWS+userId;
                stringRedisTemplate.opsForSet().remove(followKey,id.toString());
            }
        }


        return Result.ok();
    }

    @Override
    public Result follower(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();

        return Result.ok(count>0);
    }

    @Override
    public Result findSameFollow(Long targetId) {
        Long userId = UserHolder.getUser().getId();
        String key1 = RedisConstants.FOLLOWS+userId;
        String key2 = RedisConstants.FOLLOWS+targetId;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        List<UserDTO> list=new ArrayList<>();
        for (String id : intersect) {
            User user = userService.getById(id);
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            list.add(userDTO);
        }
        return Result.ok(list);
    }
}
