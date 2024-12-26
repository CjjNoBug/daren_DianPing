-- Lua script template
-- 获取锁中的线程标识
local id=redis.call('get', KEYS[1])
-- 判断是否是当前线程持有的锁
if id == ARGV[1] then
    -- 释放锁
    return redis.call('del', KEYS[1])
end
return 0