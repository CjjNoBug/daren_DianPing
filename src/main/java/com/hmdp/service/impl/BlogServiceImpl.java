package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author cjj
 *
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Autowired
    private IUserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IFollowService followService;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog->{
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        //是否被点赞了

        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        if (userId == null) {
            return Result.fail("用户未登录");
        }
        String key= RedisConstants.BLOG_LIKED_KEY+id;
        Double isMember = stringRedisTemplate.opsForZSet().score(key,userId.toString());
        if (isMember == null) {
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        }
        else{
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryLikesList(Long id) {
        String key=RedisConstants.BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(top5==null||top5.size()==0){
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids=top5.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        boolean isSuccess = save(blog);
        if (!isSuccess) {
            return Result.fail("保存blog失败");
        }
        //查询所有粉丝
        List<Follow> followUserId = followService.query().eq("follow_user_id", user.getId()).list();
        for(Follow follow:followUserId){
            Long followId = follow.getUserId();
            String key=RedisConstants.FEED_KEY+followId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());

        }
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryFollowBlog(Long max, Integer offset) {
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.FEED_KEY+userId;
        //ZSetOperations.TypedTuple<String>就是一个元素，key是blogId，值是score
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 3);
        if(typedTuples==null||typedTuples.size()==0){
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids=new ArrayList<>(typedTuples.size());
        long minTime=0;
        int count=1;
        for (ZSetOperations.TypedTuple<String> blog : typedTuples) {
            ids.add(Long.valueOf(blog.getValue()));
            long time = blog.getScore().longValue();
            if(time==minTime){
                count++;
            }
            else{
                minTime = time;
                count=1;
            }
        }
        String idstr= StrUtil.join(",",ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id,"+idstr+")").list();
        for(Blog blog:blogs){
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        }
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(count);
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);
    }

    @Override
    public Result queryById(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("blog不存在");
        }
        queryBlogUser(blog);
        //是否被点赞了
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void queryBlogUser(Blog blog){
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
    private void isBlogLiked(Blog blog) {
        UserDTO userDTO= UserHolder.getUser();
        if(userDTO==null){
            return;
        }
        Long userId = userDTO.getId();
        String  key= RedisConstants.BLOG_LIKED_KEY+blog.getId();
        Double isMember = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(isMember!=null);
    }

}
