package com.prisdvl.order.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.prisdvl.order.constant.CacheConstant;
import com.prisdvl.order.constant.MessageConstant;
import com.prisdvl.order.constant.StatusConstant;
import com.prisdvl.order.dto.DishDTO;
import com.prisdvl.order.dto.DishPageQueryDTO;
import com.prisdvl.order.entity.Dish;
import com.prisdvl.order.entity.DishFlavor;
import com.prisdvl.order.exception.DeletionNotAllowedException;
import com.prisdvl.order.mapper.DishFlavorMapper;
import com.prisdvl.order.mapper.DishMapper;
import com.prisdvl.order.mapper.SetmealDishMapper;
import com.prisdvl.order.mapper.SetmealMapper;
import com.prisdvl.order.result.PageResult;
import com.prisdvl.order.service.DishService;
import com.prisdvl.order.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DishServiceImpl implements DishService {

    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private DishFlavorMapper dishFlavorMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;
    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 新增菜品和对应的口味
     *
     * @param dishDTO
     */
    @Transactional
    @CacheEvict(cacheNames = {CacheConstant.DISH_CACHE, CacheConstant.SETMEAL_CACHE}, allEntries = true)
    public void saveWithFlavor(DishDTO dishDTO) {

        Dish dish = new Dish();

        BeanUtils.copyProperties(dishDTO, dish);

        //向菜品表插入1条数据
        dishMapper.insert(dish);

        //获取insert语句生成的主键值
        Long dishId = dish.getId();

        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && flavors.size() > 0) {
            flavors.forEach(dishFlavor -> {
                dishFlavor.setDishId(dishId);
            });
            //向口味表插入n条数据
            dishFlavorMapper.insertBatch(flavors);
        }
    }

    /**
     * 菜品分页查询
     *
     * @param dishPageQueryDTO
     * @return
     */
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
        PageHelper.startPage(dishPageQueryDTO.getPage(), dishPageQueryDTO.getPageSize());
        Page<DishVO> page = dishMapper.pageQuery(dishPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 菜品批量删除
     *
     * @param ids
     */
    @Transactional
    @CacheEvict(cacheNames = {CacheConstant.DISH_CACHE, CacheConstant.SETMEAL_CACHE}, allEntries = true)
    public void deleteBatch(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }

        // 一次查出所有待删除菜品，替代 for 循环逐条 getById
        List<Dish> dishes = dishMapper.getByIds(ids);
        boolean hasOnSale = dishes.stream()
                .anyMatch(dish -> StatusConstant.ENABLE.equals(dish.getStatus()));
        if (hasOnSale) {
            //当前菜品处于起售中，不能删除
            throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
        }

        //判断当前菜品是否能够删除---是否被套餐关联了？？
        List<Long> setmealIds = setmealDishMapper.getSetmealIdsByDishIds(ids);
        if (setmealIds != null && setmealIds.size() > 0) {
            //当前菜品被套餐关联了，不能删除
            throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }

        // 菜品与口味各一条批量 delete，替代 2N 条逐条删除
        dishMapper.deleteByIds(ids);
        dishFlavorMapper.deleteByDishIds(ids);
    }

    /**
     * 根据id查询菜品和对应的口味数据
     *
     * @param id
     * @return
     */
    public DishVO getByIdWithFlavor(Long id) {
        //根据id查询菜品数据
        Dish dish = dishMapper.getById(id);

        //根据菜品id查询口味数据
        List<DishFlavor> dishFlavors = dishFlavorMapper.getByDishId(id);

        //将查询到的数据封装到VO
        DishVO dishVO = new DishVO();
        BeanUtils.copyProperties(dish, dishVO);
        dishVO.setFlavors(dishFlavors);

        return dishVO;
    }

    /**
     * 根据id修改菜品基本信息和对应的口味信息
     *
     * @param dishDTO
     */
    @Transactional
    @CacheEvict(cacheNames = {CacheConstant.DISH_CACHE, CacheConstant.SETMEAL_CACHE}, allEntries = true)
    public void updateWithFlavor(DishDTO dishDTO) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);

        //修改菜品表基本信息
        dishMapper.update(dish);

        //删除原有的口味数据
        dishFlavorMapper.deleteByDishId(dishDTO.getId());

        //重新插入口味数据
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && flavors.size() > 0) {
            flavors.forEach(dishFlavor -> {
                dishFlavor.setDishId(dishDTO.getId());
            });
            //向口味表插入n条数据
            dishFlavorMapper.insertBatch(flavors);
        }
    }

    /**
     * 菜品起售停售
     *
     * @param status
     * @param id
     */
    @Transactional
    @CacheEvict(cacheNames = {CacheConstant.DISH_CACHE, CacheConstant.SETMEAL_CACHE}, allEntries = true)
    public void startOrStop(Integer status, Long id) {
        Dish dish = Dish.builder()
                .id(id)
                .status(status)
                .build();
        dishMapper.update(dish);

        if (StatusConstant.DISABLE.equals(status)) {
            // 如果是停售操作，还需要将包含当前菜品的套餐也停售
            List<Long> dishIds = new ArrayList<>();
            dishIds.add(id);
            // select setmeal_id from setmeal_dish where dish_id in (?,?,?)
            List<Long> setmealIds = setmealDishMapper.getSetmealIdsByDishIds(dishIds);
            if (setmealIds != null && setmealIds.size() > 0) {
                // 一条 update ... where id in (...) 联动停售，替代 N 条逐条 update
                setmealMapper.updateStatusByIds(setmealIds, StatusConstant.DISABLE);
            }
        }
    }

    /**
     * 根据分类id查询菜品
     *
     * @param categoryId
     * @return
     */
    public List<Dish> list(Long categoryId) {
        Dish dish = Dish.builder()
                .categoryId(categoryId)
                .status(StatusConstant.ENABLE)
                .build();
        return dishMapper.list(dish);
    }

    /**
     * 条件查询菜品和口味
     * @param dish
     * @return
     */
    @Cacheable(cacheNames = CacheConstant.DISH_CACHE,
            key = "#dish.categoryId",
            condition = "#dish.categoryId != null and #dish.status != null and #dish.status == 1",
            sync = true)
    public List<DishVO> listWithFlavor(Dish dish) {
        List<Dish> dishList = dishMapper.list(dish);
        if (CollectionUtils.isEmpty(dishList)) {
            return new ArrayList<>();
        }

        // 一次 IN 查询取出当前页全部口味，再按 dishId 分组，避免每个菜品查一次（1+N -> 2）
        List<Long> dishIds = dishList.stream().map(Dish::getId).collect(Collectors.toList());
        Map<Long, List<DishFlavor>> flavorMap = dishFlavorMapper.getByDishIds(dishIds).stream()
                .collect(Collectors.groupingBy(DishFlavor::getDishId));

        List<DishVO> dishVOList = new ArrayList<>(dishList.size());
        for (Dish d : dishList) {
            DishVO dishVO = new DishVO();
            BeanUtils.copyProperties(d, dishVO);
            dishVO.setFlavors(flavorMap.getOrDefault(d.getId(), Collections.emptyList()));
            dishVOList.add(dishVO);
        }

        return dishVOList;
    }
}
