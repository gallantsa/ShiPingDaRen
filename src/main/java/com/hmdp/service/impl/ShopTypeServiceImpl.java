package com.hmdp.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryShopType() {
        String key = CACHE_SHOP_TYPE_KEY;
        // 1. 查询Redis缓存
        List<String> typeJson = stringRedisTemplate.opsForList().range(key, 0, -1);

        // 2. 判断是否命中
        if (CollectionUtil.isNotEmpty(typeJson)) {
            // 3. 命中则直接返回
            // 防止缓存穿透时存入空对象
            if (StrUtil.isBlank(typeJson.get(0))) {
                return Result.fail("商品分类信息为空!");
            }

            ArrayList<ShopType> typeList = new ArrayList<>();
            for (String jsonString : typeJson) {
                ShopType shopType = JSONUtil.toBean(jsonString, ShopType.class);
                typeList.add(shopType);
            }
            return Result.ok(typeList);
        }

        // 4. 未命中, 去查询数据库
        List<ShopType> typeList = query().orderByAsc("sort").list();

        // 5. 不存在, 返回错误
        if (CollectionUtil.isEmpty(typeList)) {
            // 添加空对象到redis, 解决缓存穿透
            stringRedisTemplate.opsForList().rightPushAll(key, CollectionUtil.newArrayList(""));

            stringRedisTemplate.expire(key, CACHE_NULL_TTL, TimeUnit.MINUTES);

            return Result.fail("商品分类信息为空!");
        }

        // 6. 存在则写入redis缓存, 有顺序只能RPUSH
        List<String> shopTypeList = new ArrayList<>();
        for (ShopType shopType : typeList) {
            String jsonStr = JSONUtil.toJsonStr(shopType);
            shopTypeList.add(jsonStr);
        }
        stringRedisTemplate.opsForList().rightPushAll(key, shopTypeList);

        // 7. 返回
        return Result.ok(typeList);
    }
}
