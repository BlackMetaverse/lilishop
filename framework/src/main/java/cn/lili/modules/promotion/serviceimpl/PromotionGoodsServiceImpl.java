package cn.lili.modules.promotion.serviceimpl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.text.CharSequenceUtil;
import cn.lili.common.enums.PromotionTypeEnum;
import cn.lili.common.enums.ResultCode;
import cn.lili.common.exception.ServiceException;
import cn.lili.common.vo.PageVO;
import cn.lili.modules.goods.entity.dos.GoodsSku;
import cn.lili.modules.goods.service.GoodsSkuService;
import cn.lili.modules.promotion.entity.dos.PromotionGoods;
import cn.lili.modules.promotion.entity.dos.SeckillApply;
import cn.lili.modules.promotion.entity.dto.search.PromotionGoodsSearchParams;
import cn.lili.modules.promotion.entity.dto.search.SeckillSearchParams;
import cn.lili.modules.promotion.entity.enums.PromotionsScopeTypeEnum;
import cn.lili.modules.promotion.entity.enums.PromotionsStatusEnum;
import cn.lili.modules.promotion.mapper.PromotionGoodsMapper;
import cn.lili.modules.promotion.service.PromotionGoodsService;
import cn.lili.modules.promotion.service.SeckillApplyService;
import cn.lili.modules.promotion.tools.PromotionTools;
import cn.lili.mybatis.util.PageUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 促销商品业务层实现
 *
 * @author Chopper
 * @since 2021/3/18 9:22 上午
 */
@Service
public class PromotionGoodsServiceImpl extends ServiceImpl<PromotionGoodsMapper, PromotionGoods> implements PromotionGoodsService {

    private static final String SKU_ID_COLUMN = "sku_id";

    /**
     * Redis
     */
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 秒杀活动申请
     */
    @Autowired
    private SeckillApplyService seckillApplyService;
    /**
     * 规格商品
     */
    @Autowired
    private GoodsSkuService goodsSkuService;

    @Override
    public List<PromotionGoods> findSkuValidPromotion(String skuId, String storeIds) {

        GoodsSku sku = goodsSkuService.getGoodsSkuByIdFromCache(skuId);
        if (sku == null) {
            return new ArrayList<>();
        }
        QueryWrapper<PromotionGoods> queryWrapper = new QueryWrapper<>();

        queryWrapper.and(i -> i.or(j -> j.eq(SKU_ID_COLUMN, skuId))
                .or(n -> n.eq("scope_type", PromotionsScopeTypeEnum.ALL.name()))
                .or(n -> n.and(k -> k.eq("scope_type", PromotionsScopeTypeEnum.PORTION_GOODS_CATEGORY.name())
                        .and(l -> l.like("scope_id", sku.getCategoryPath())))));
        queryWrapper.and(i -> i.or(PromotionTools.queryPromotionStatus(PromotionsStatusEnum.START)).or(PromotionTools.queryPromotionStatus(PromotionsStatusEnum.NEW)));
        queryWrapper.in("store_id", Arrays.asList(storeIds.split(",")));
        return this.list(queryWrapper);
    }

    @Override
    public IPage<PromotionGoods> pageFindAll(PromotionGoodsSearchParams searchParams, PageVO pageVo) {
        return this.page(PageUtil.initPage(pageVo), searchParams.queryWrapper());
    }

    /**
     * 获取促销商品信息
     *
     * @param searchParams 查询参数
     * @return 促销商品列表
     */
    @Override
    public List<PromotionGoods> listFindAll(PromotionGoodsSearchParams searchParams) {
        return this.list(searchParams.queryWrapper());
    }

    /**
     * 获取促销商品信息
     *
     * @param searchParams 查询参数
     * @return 促销商品信息
     */
    @Override
    public PromotionGoods getPromotionsGoods(PromotionGoodsSearchParams searchParams) {
        return this.getOne(searchParams.queryWrapper(), false);
    }

    /**
     * 获取当前有效时间特定促销类型的促销商品信息
     *
     * @param skuId          查询参数
     * @param promotionTypes 特定促销类型
     * @return 促销商品信息
     */
    @Override
    public PromotionGoods getValidPromotionsGoods(String skuId, List<String> promotionTypes) {
        QueryWrapper<PromotionGoods> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(SKU_ID_COLUMN, skuId);
        queryWrapper.in("promotion_type", promotionTypes);
        queryWrapper.and(PromotionTools.queryPromotionStatus(PromotionsStatusEnum.START));
        return this.getOne(queryWrapper, false);
    }

    /**
     * 获取当前有效时间特定促销类型的促销商品价格
     *
     * @param skuId          skuId
     * @param promotionTypes 特定促销类型
     * @return 促销商品价格
     */
    @Override
    public Double getValidPromotionsGoodsPrice(String skuId, List<String> promotionTypes) {
        QueryWrapper<PromotionGoods> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(SKU_ID_COLUMN, skuId);
        queryWrapper.in("promotion_type", promotionTypes);
        queryWrapper.and(PromotionTools.queryPromotionStatus(PromotionsStatusEnum.START));
        return this.baseMapper.selectPromotionsGoodsPrice(queryWrapper);
    }

    @Override
    public Integer findInnerOverlapPromotionGoods(String promotionType, String skuId, Date startTime, Date endTime, String promotionId) {
        if (promotionId != null) {
            return this.baseMapper.selectInnerOverlapPromotionGoodsWithout(promotionType, skuId, startTime, endTime, promotionId);
        } else {
            return this.baseMapper.selectInnerOverlapPromotionGoods(promotionType, skuId, startTime, endTime);
        }
    }

    /**
     * 获取促销活动商品库存
     *
     * @param typeEnum    促销商品类型
     * @param promotionId 促销活动id
     * @param skuId       商品skuId
     * @return 促销活动商品库存
     */
    @Override
    public Integer getPromotionGoodsStock(PromotionTypeEnum typeEnum, String promotionId, String skuId) {
        String promotionStockKey = PromotionGoodsService.getPromotionGoodsStockCacheKey(typeEnum, promotionId, skuId);
        String promotionGoodsStock = stringRedisTemplate.opsForValue().get(promotionStockKey);

        //库存如果不为空，则直接返回
        if (promotionGoodsStock != null && CharSequenceUtil.isNotEmpty(promotionGoodsStock)) {
            return Convert.toInt(promotionGoodsStock);
        }
        //如果为空
        else {
            //获取促销商品，如果不存在促销商品，则返回0
            PromotionGoodsSearchParams searchParams = new PromotionGoodsSearchParams();
            searchParams.setPromotionType(typeEnum.name());
            searchParams.setPromotionId(promotionId);
            searchParams.setSkuId(skuId);
            PromotionGoods promotionGoods = this.getPromotionsGoods(searchParams);
            if (promotionGoods == null) {
                return 0;
            }
            //否则写入新的促销商品库存
            stringRedisTemplate.opsForValue().set(promotionStockKey, promotionGoods.getQuantity().toString());
            return promotionGoods.getQuantity();
        }
    }

    @Override
    public List<Integer> getPromotionGoodsStock(PromotionTypeEnum typeEnum, String promotionId, List<String> skuId) {
        PromotionGoodsSearchParams searchParams = new PromotionGoodsSearchParams();
        searchParams.setPromotionType(typeEnum.name());
        searchParams.setPromotionId(promotionId);
        searchParams.setSkuIds(skuId);
        //获取促销商品，如果不存在促销商品，则返回0
        List<PromotionGoods> promotionGoods = this.listFindAll(searchParams);
        //接收数据
        List<Integer> result = new ArrayList<>(skuId.size());
        for (String sid : skuId) {
            Integer stock = null;
            for (PromotionGoods pg : promotionGoods) {
                if (sid.equals(pg.getSkuId())) {
                    stock = pg.getQuantity();
                }
            }
            //如果促销商品不存在，给一个默认值
            if (stock == null) {
                stock = 0;
            }
            result.add(stock);
        }
        return result;
    }

    /**
     * 更新促销活动商品库存
     *
     * @param typeEnum    促销商品类型
     * @param promotionId 促销活动id
     * @param skuId       商品skuId
     * @param quantity    更新后的库存数量
     */
    @Override
    public void updatePromotionGoodsStock(PromotionTypeEnum typeEnum, String promotionId, String skuId, Integer quantity) {
        String promotionStockKey = PromotionGoodsService.getPromotionGoodsStockCacheKey(typeEnum, promotionId, skuId);
        if (typeEnum.equals(PromotionTypeEnum.SECKILL)) {
            SeckillSearchParams searchParams = new SeckillSearchParams();
            searchParams.setSeckillId(promotionId);
            searchParams.setSkuId(skuId);
            SeckillApply seckillApply = this.seckillApplyService.getSeckillApply(searchParams);
            if (seckillApply == null) {
                throw new ServiceException(ResultCode.SECKILL_NOT_EXIST_ERROR);
            }
            seckillApplyService.updateSeckillApplyQuantity(promotionId, skuId, quantity);
        } else {
            LambdaUpdateWrapper<PromotionGoods> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(PromotionGoods::getPromotionType, typeEnum.name()).eq(PromotionGoods::getPromotionId, promotionId).eq(PromotionGoods::getSkuId, skuId);
            updateWrapper.set(PromotionGoods::getQuantity, quantity);
            this.update(updateWrapper);
        }

        stringRedisTemplate.opsForValue().set(promotionStockKey, quantity.toString());
    }

    /**
     * 更新促销活动商品库存
     *
     * @param promotionGoods 促销信息
     */
    @Override
    public void updatePromotionGoodsByPromotions(PromotionGoods promotionGoods) {
        LambdaQueryWrapper<PromotionGoods> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PromotionGoods::getPromotionId, promotionGoods.getPromotionId());
        this.remove(queryWrapper);
        this.save(promotionGoods);
    }

    /**
     * 删除促销商品
     *
     * @param promotionId 促销活动id
     * @param skuIds      skuId
     */
    @Override
    public void deletePromotionGoods(String promotionId, List<String> skuIds) {
        LambdaQueryWrapper<PromotionGoods> queryWrapper = new LambdaQueryWrapper<PromotionGoods>()
                .eq(PromotionGoods::getPromotionId, promotionId).in(PromotionGoods::getSkuId, skuIds);
        this.remove(queryWrapper);
    }

    /**
     * 删除促销促销商品
     *
     * @param promotionIds 促销活动id
     */
    @Override
    public void deletePromotionGoods(List<String> promotionIds) {
        LambdaQueryWrapper<PromotionGoods> queryWrapper = new LambdaQueryWrapper<PromotionGoods>()
                .in(PromotionGoods::getPromotionId, promotionIds);
        this.remove(queryWrapper);
    }

    /**
     * 根据参数删除促销商品
     *
     * @param searchParams 查询参数
     */
    @Override
    public void deletePromotionGoods(PromotionGoodsSearchParams searchParams) {
        this.remove(searchParams.queryWrapper());
    }

}