package com.luckyh.cloud.common.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis工具类
 * 
 * @author LuckyH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisUtils {

    private final StringRedisTemplate stringRedisTemplate;

    // =============================common============================

    /**
     * 指定缓存失效时间
     * 
     * @param key  键
     * @param time 时间(秒)
     * @return true成功 false失败
     */
    public boolean expire(String key, long time) {
        try {
            if (time > 0) {
                stringRedisTemplate.expire(key, time, TimeUnit.SECONDS);
            }
            return true;
        } catch (Exception e) {
            log.error("设置缓存失效时间失败", e);
            return false;
        }
    }

    /**
     * 指定缓存失效时间
     * 
     * @param key  键
     * @param time 时间
     * @param unit 时间单位
     * @return true成功 false失败
     */
    public boolean expire(String key, long time, TimeUnit unit) {
        try {
            if (time > 0) {
                stringRedisTemplate.expire(key, time, unit);
            }
            return true;
        } catch (Exception e) {
            log.error("设置缓存失效时间失败", e);
            return false;
        }
    }

    /**
     * 根据key 获取过期时间
     * 
     * @param key 键 不能为null
     * @return 时间(秒) 返回0代表为永久有效
     */
    public long getExpire(String key) {
        return stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    /**
     * 判断key是否存在
     * 
     * @param key 键
     * @return true 存在 false不存在
     */
    public boolean hasKey(String key) {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("判断key是否存在失败", e);
            return false;
        }
    }

    /**
     * 删除缓存
     * 
     * @param key 可以传一个值 或多个
     */
    public void del(String... key) {
        if (key != null && key.length > 0) {
            if (key.length == 1) {
                stringRedisTemplate.delete(key[0]);
            } else {
                stringRedisTemplate.delete(List.of(key));
            }
        }
    }

    /**
     * 删除缓存
     * 
     * @param keys 键集合
     */
    public void del(Collection<String> keys) {
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    // ============================String=============================

    /**
     * 普通缓存获取
     * 
     * @param key 键
     * @return 值
     */
    public String get(String key) {
        return key == null ? null : stringRedisTemplate.opsForValue().get(key);
    }

    /**
     * 普通缓存放入
     * 
     * @param key   键
     * @param value 值
     * @return true成功 false失败
     */
    public boolean set(String key, String value) {
        try {
            stringRedisTemplate.opsForValue().set(key, value);
            return true;
        } catch (Exception e) {
            log.error("缓存存储失败", e);
            return false;
        }
    }

    /**
     * 普通缓存放入并设置时间
     * 
     * @param key   键
     * @param value 值
     * @param time  时间(秒) time要大于0 如果time小于等于0 将设置无限期
     * @return true成功 false 失败
     */
    public boolean set(String key, String value, long time) {
        try {
            if (time > 0) {
                stringRedisTemplate.opsForValue().set(key, value, time, TimeUnit.SECONDS);
            } else {
                set(key, value);
            }
            return true;
        } catch (Exception e) {
            log.error("缓存存储失败", e);
            return false;
        }
    }

    /**
     * 普通缓存放入并设置时间
     * 
     * @param key   键
     * @param value 值
     * @param time  时间
     * @param unit  时间单位
     * @return true成功 false 失败
     */
    public boolean set(String key, String value, long time, TimeUnit unit) {
        try {
            if (time > 0) {
                stringRedisTemplate.opsForValue().set(key, value, time, unit);
            } else {
                set(key, value);
            }
            return true;
        } catch (Exception e) {
            log.error("缓存存储失败", e);
            return false;
        }
    }

    /**
     * 递增
     * 
     * @param key   键
     * @param delta 要增加几(大于0)
     * @return 递增后的值
     */
    public long incr(String key, long delta) {
        if (delta < 0) {
            throw new RuntimeException("递增因子必须大于0");
        }
        Long result = stringRedisTemplate.opsForValue().increment(key, delta);
        return result != null ? result : 0L;
    }

    /**
     * 递减
     * 
     * @param key   键
     * @param delta 要减少几(小于0)
     * @return 递减后的值
     */
    public long decr(String key, long delta) {
        if (delta < 0) {
            throw new RuntimeException("递减因子必须大于0");
        }
        Long result = stringRedisTemplate.opsForValue().increment(key, -delta);
        return result != null ? result : 0L;
    }

    // ================================Hash=================================

    /**
     * HashGet
     * 
     * @param key  键 不能为null
     * @param item 项 不能为null
     * @return 值
     */
    public String hget(String key, String item) {
        return (String) stringRedisTemplate.opsForHash().get(key, item);
    }

    /**
     * 获取hashKey对应的所有键值
     * 
     * @param key 键
     * @return 对应的多个键值
     */
    public Map<Object, Object> hmget(String key) {
        return stringRedisTemplate.opsForHash().entries(key);
    }

    /**
     * HashSet
     * 
     * @param key 键
     * @param map 对应多个键值
     * @return true 成功 false 失败
     */
    public boolean hmset(String key, Map<String, String> map) {
        try {
            stringRedisTemplate.opsForHash().putAll(key, map);
            return true;
        } catch (Exception e) {
            log.error("Hash存储失败", e);
            return false;
        }
    }

    /**
     * HashSet 并设置时间
     * 
     * @param key  键
     * @param map  对应多个键值
     * @param time 时间(秒)
     * @return true成功 false失败
     */
    public boolean hmset(String key, Map<String, String> map, long time) {
        try {
            stringRedisTemplate.opsForHash().putAll(key, map);
            if (time > 0) {
                expire(key, time);
            }
            return true;
        } catch (Exception e) {
            log.error("Hash存储失败", e);
            return false;
        }
    }

    /**
     * 向一张hash表中放入数据,如果不存在将创建
     * 
     * @param key   键
     * @param item  项
     * @param value 值
     * @return true 成功 false失败
     */
    public boolean hset(String key, String item, String value) {
        try {
            stringRedisTemplate.opsForHash().put(key, item, value);
            return true;
        } catch (Exception e) {
            log.error("Hash存储失败", e);
            return false;
        }
    }

    /**
     * 向一张hash表中放入数据,如果不存在将创建
     * 
     * @param key   键
     * @param item  项
     * @param value 值
     * @param time  时间(秒) 注意:如果已存在的hash表有时间,这里将会替换原有的时间
     * @return true 成功 false失败
     */
    public boolean hset(String key, String item, String value, long time) {
        try {
            stringRedisTemplate.opsForHash().put(key, item, value);
            if (time > 0) {
                expire(key, time);
            }
            return true;
        } catch (Exception e) {
            log.error("Hash存储失败", e);
            return false;
        }
    }

    /**
     * 删除hash表中的值
     * 
     * @param key  键 不能为null
     * @param item 项 可以使多个 不能为null
     */
    public void hdel(String key, Object... item) {
        stringRedisTemplate.opsForHash().delete(key, item);
    }

    /**
     * 判断hash表中是否有该项的值
     * 
     * @param key  键 不能为null
     * @param item 项 不能为null
     * @return true 存在 false不存在
     */
    public boolean hHasKey(String key, String item) {
        return stringRedisTemplate.opsForHash().hasKey(key, item);
    }

    // ============================Set=============================

    /**
     * 根据key获取Set中的所有值
     * 
     * @param key 键
     * @return 值集合
     */
    public Set<String> sGet(String key) {
        try {
            return stringRedisTemplate.opsForSet().members(key);
        } catch (Exception e) {
            log.error("获取Set失败", e);
            return null;
        }
    }

    /**
     * 根据value从一个set中查询,是否存在
     * 
     * @param key   键
     * @param value 值
     * @return true 存在 false不存在
     */
    public boolean sHasKey(String key, String value) {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(key, value));
        } catch (Exception e) {
            log.error("查询Set成员失败", e);
            return false;
        }
    }

    /**
     * 将数据放入set缓存
     * 
     * @param key    键
     * @param values 值 可以是多个
     * @return 成功个数
     */
    public long sSet(String key, String... values) {
        try {
            Long result = stringRedisTemplate.opsForSet().add(key, values);
            return result != null ? result : 0L;
        } catch (Exception e) {
            log.error("Set存储失败", e);
            return 0;
        }
    }

    /**
     * 将set数据放入缓存
     * 
     * @param key    键
     * @param time   时间(秒)
     * @param values 值 可以是多个
     * @return 成功个数
     */
    public long sSetAndTime(String key, long time, String... values) {
        try {
            Long count = stringRedisTemplate.opsForSet().add(key, values);
            if (time > 0) {
                expire(key, time);
            }
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.error("Set存储失败", e);
            return 0;
        }
    }

    /**
     * 获取set缓存的长度
     * 
     * @param key 键
     * @return 长度
     */
    public long sGetSetSize(String key) {
        try {
            Long result = stringRedisTemplate.opsForSet().size(key);
            return result != null ? result : 0L;
        } catch (Exception e) {
            log.error("获取Set大小失败", e);
            return 0;
        }
    }

    /**
     * 移除值为value的
     * 
     * @param key    键
     * @param values 值 可以是多个
     * @return 移除的个数
     */
    public long setRemove(String key, Object... values) {
        try {
            Long result = stringRedisTemplate.opsForSet().remove(key, values);
            return result != null ? result : 0L;
        } catch (Exception e) {
            log.error("Set移除失败", e);
            return 0;
        }
    }

    // ===============================List=================================

    /**
     * 获取list缓存的内容
     * 
     * @param key   键
     * @param start 开始
     * @param end   结束 0 到 -1代表所有值
     * @return 列表内容
     */
    public List<String> lGet(String key, long start, long end) {
        try {
            return stringRedisTemplate.opsForList().range(key, start, end);
        } catch (Exception e) {
            log.error("获取List失败", e);
            return null;
        }
    }

    /**
     * 获取list缓存的长度
     * 
     * @param key 键
     * @return 长度
     */
    public long lGetListSize(String key) {
        try {
            Long result = stringRedisTemplate.opsForList().size(key);
            return result != null ? result : 0L;
        } catch (Exception e) {
            log.error("获取List大小失败", e);
            return 0;
        }
    }

    /**
     * 通过索引 获取list中的值
     * 
     * @param key   键
     * @param index 索引 index>=0时， 0 表头，1 第二个元素，依次类推；index<0时，-1，表尾，-2倒数第二个元素，依次类推
     * @return 值
     */
    public String lGetIndex(String key, long index) {
        try {
            return stringRedisTemplate.opsForList().index(key, index);
        } catch (Exception e) {
            log.error("获取List元素失败", e);
            return null;
        }
    }

    /**
     * 将list放入缓存
     * 
     * @param key   键
     * @param value 值
     * @return true成功 false失败
     */
    public boolean lSet(String key, String value) {
        try {
            stringRedisTemplate.opsForList().rightPush(key, value);
            return true;
        } catch (Exception e) {
            log.error("List存储失败", e);
            return false;
        }
    }

    /**
     * 将list放入缓存
     * 
     * @param key   键
     * @param value 值
     * @param time  时间(秒)
     * @return true成功 false失败
     */
    public boolean lSet(String key, String value, long time) {
        try {
            stringRedisTemplate.opsForList().rightPush(key, value);
            if (time > 0) {
                expire(key, time);
            }
            return true;
        } catch (Exception e) {
            log.error("List存储失败", e);
            return false;
        }
    }

    /**
     * 将list放入缓存
     * 
     * @param key   键
     * @param value 值
     * @return true成功 false失败
     */
    public boolean lSet(String key, List<String> value) {
        try {
            stringRedisTemplate.opsForList().rightPushAll(key, value);
            return true;
        } catch (Exception e) {
            log.error("List存储失败", e);
            return false;
        }
    }

    /**
     * 将list放入缓存
     * 
     * @param key   键
     * @param value 值
     * @param time  时间(秒)
     * @return true成功 false失败
     */
    public boolean lSet(String key, List<String> value, long time) {
        try {
            stringRedisTemplate.opsForList().rightPushAll(key, value);
            if (time > 0) {
                expire(key, time);
            }
            return true;
        } catch (Exception e) {
            log.error("List存储失败", e);
            return false;
        }
    }

    /**
     * 根据索引修改list中的某条数据
     * 
     * @param key   键
     * @param index 索引
     * @param value 值
     * @return true成功 false失败
     */
    public boolean lUpdateIndex(String key, long index, String value) {
        try {
            stringRedisTemplate.opsForList().set(key, index, value);
            return true;
        } catch (Exception e) {
            log.error("List更新失败", e);
            return false;
        }
    }

    /**
     * 移除N个值为value
     * 
     * @param key   键
     * @param count 移除多少个
     * @param value 值
     * @return 移除的个数
     */
    public long lRemove(String key, long count, String value) {
        try {
            Long result = stringRedisTemplate.opsForList().remove(key, count, value);
            return result != null ? result : 0L;
        } catch (Exception e) {
            log.error("List移除失败", e);
            return 0;
        }
    }

    // ============================Token管理============================

    /**
     * 将Token加入黑名单
     * 
     * @param token      令牌
     * @param expiration 过期时间(秒)
     * @return true成功 false失败
     */
    public boolean addTokenToBlacklist(String token, long expiration) {
        String key = "blacklist:token:" + token;
        return set(key, "1", expiration, TimeUnit.SECONDS);
    }

    /**
     * 检查Token是否在黑名单中
     * 
     * @param token 令牌
     * @return true在黑名单中 false不在黑名单中
     */
    public boolean isTokenInBlacklist(String token) {
        String key = "blacklist:token:" + token;
        return hasKey(key);
    }

    /**
     * 从黑名单中移除Token
     * 
     * @param token 令牌
     */
    public void removeTokenFromBlacklist(String token) {
        String key = "blacklist:token:" + token;
        del(key);
    }

    // ============================缓存管理============================

    /**
     * 获取匹配的所有key
     * 
     * @param pattern 匹配模式
     * @return key集合
     */
    public Set<String> keys(String pattern) {
        return stringRedisTemplate.keys(pattern);
    }

    /**
     * 批量删除匹配的key
     * 
     * @param pattern 匹配模式
     * @return 删除的数量
     */
    public long deleteKeys(String pattern) {
        Set<String> keys = keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            del(keys);
            return keys.size();
        }
        return 0;
    }
}