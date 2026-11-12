package com.prisdvl.order.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.prisdvl.order.constant.CacheConstant;
import com.prisdvl.order.constant.MessageConstant;
import com.prisdvl.order.constant.StatusConstant;
import com.prisdvl.order.dto.SetmealDTO;
import com.prisdvl.order.dto.SetmealPageQueryDTO;
import com.prisdvl.order.entity.Dish;
import com.prisdvl.order.entity.Setmeal;
import com.prisdvl.order.entity.SetmealDish;
import com.prisdvl.order.exception.DeletionNotAllowedException;
import com.prisdvl.order.exception.SetmealEnableFailedException;
import com.prisdvl.order.mapper.DishMapper;
import com.prisdvl.order.mapper.SetmealDishMapper;
import com.prisdvl.order.mapper.SetmealMapper;
import com.prisdvl.order.result.PageResult;
import com.prisdvl.order.service.SetmealService;
import com.prisdvl.order.vo.DishItemVO;
import com.prisdvl.order.vo.SetmealVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * 套餐业务实现
 */
@Service
@Slf4j
public class SetmealServiceImpl implements SetmealService {

    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;
    @Autowired
    private DishMapper dishMapper;

    /**
     * 新增套餐，同时需要保存套餐和菜品的关联关系
     *
     * @param setmealDTO
     */
    @Transactional
    @CacheEvict(cacheNames = CacheConstant.SETMEAL_CACHE, allEntries = true)
    public void saveWithDish(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);

        //向套餐表插入数据
        setmealMapper.insert(setmeal);

        //获取生成的套餐id
        Long setmealId = setmeal.getId();

        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        if (CollectionUtils.isEmpty(setmealDishes)) {
            return;
        }
        setmealDishes.forEach(setmealDish -> {
            setmealDish.setSetmealId(setmealId);
        });

        //保存套餐和菜品的关联关系
        setmealDishMapper.insertBatch(setmealDishes);
    }

    /**
     * 分页查询
     *
     * @param setmealPageQueryDTO
     * @return
     */
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        int pageNum = setmealPageQueryDTO.getPage();
        int pageSize = setmealPageQueryDTO.getPageSize();

        PageHelper.startPage(pageNum, pageSize);
        Page<SetmealVO> page = setmealMapper.pageQuery(setmealPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 批量删除套餐
     *
     * @param ids
     */
    @Transactional
    @CacheEvict(cacheNames = CacheConstant.SETMEAL_CACHE, allEntries = true)
    public void deleteBatch(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }

        // 一次查出全部待删除套餐，替代逐条 getById
        List<Setmeal> setmeals = setmealMapper.getByIds(ids);
        boolean hasOnSale = setmeals.stream()
                .anyMatch(setmeal -> StatusConstant.ENABLE.equals(setmeal.getStatus()));
        if (hasOnSale) {
            //起售中的套餐不能删除
            throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
        }

        // 套餐与关联关系各一条批量 delete，替代 2N 条逐条删除
        setmealMapper.deleteByIds(ids);
        setmealDishMapper.deleteBySetmealIds(ids);
    }

    /**
     * 根据id查询套餐和套餐菜品关系
     *
     * @param id
     * @return
     */
    public SetmealVO getByIdWithDish(Long id) {
        SetmealVO setmealVO = setmealMapper.getByIdWithDish(id);
        return setmealVO;
    }

    /**
     * 修改套餐
     *
     * @param setmealDTO
     */
    @Transactional
    @CacheEvict(cacheNames = CacheConstant.SETMEAL_CACHE, allEntries = true)
    public void update(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);

        //1、修改套餐表，执行update
        setmealMapper.update(setmeal);

        //套餐id
        Long setmealId = setmealDTO.getId();

        //2、删除套餐和菜品的关联关系，操作setmeal_dish表，执行delete
        setmealDishMapper.deleteBySetmealId(setmealId);

        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        if (CollectionUtils.isEmpty(setmealDishes)) {
            return;
        }
        setmealDishes.forEach(setmealDish -> {
            setmealDish.setSetmealId(setmealId);
        });
        //3、重新插入套餐和菜品的关联关系，操作setmeal_dish表，执行insert
        setmealDishMapper.insertBatch(setmealDishes);
    }

    /**
     * 套餐起售、停售
     *
     * @param status
     * @param id
     */
    @Transactional
    @CacheEvict(cacheNames = CacheConstant.SETMEAL_CACHE, allEntries = true)
    public void startOrStop(Integer status, Long id) {
        //起售套餐时，判断套餐内是否有停售菜品，有停售菜品提示"套餐内包含未启售菜品，无法启售"
        if (StatusConstant.ENABLE.equals(status)) {
            //select a.* from dish a left join setmeal_dish b on a.id = b.dish_id where b.setmeal_id = ?
            List<Dish> dishList = dishMapper.getBySetmealId(id);
            if (dishList != null && dishList.size() > 0) {
                dishList.forEach(dish -> {
                    if (StatusConstant.DISABLE.equals(dish.getStatus())) {
                        throw new SetmealEnableFailedException(MessageConstant.SETMEAL_ENABLE_FAILED);
                    }
                });
            }
        }

        Setmeal setmeal = Setmeal.builder()
                .id(id)
                .status(status)
                .build();
        setmealMapper.update(setmeal);
    }

    /**
     * 条件查询
     * @param setmeal
     * @return
     */
    @Cacheable(cacheNames = CacheConstant.SETMEAL_CACHE,
            key = "#setmeal.categoryId",
            condition = "#setmeal.categoryId != null and #setmeal.status != null and #setmeal.status == 1",
            sync = true)
    public List<Setmeal> list(Setmeal setmeal) {
        List<Setmeal> list = setmealMapper.list(setmeal);
        return list;
    }

    /**
     * 根据id查询菜品选项
     * @param id
     * @return
     */
    public List<DishItemVO> getDishItemById(Long id) {
        return setmealMapper.getDishItemBySetmealId(id);
    }
}
