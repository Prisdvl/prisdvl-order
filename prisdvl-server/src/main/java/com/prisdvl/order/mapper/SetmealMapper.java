package com.prisdvl.order.mapper;

import com.github.pagehelper.Page;
import com.prisdvl.order.annotation.AutoFill;
import com.prisdvl.order.dto.SetmealPageQueryDTO;
import com.prisdvl.order.entity.Setmeal;
import com.prisdvl.order.enumeration.OperationType;
import com.prisdvl.order.vo.DishItemVO;
import com.prisdvl.order.vo.SetmealVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SetmealMapper {

    /**
     * 根据分类id查询套餐的数量
     *
     * @param id
     * @return
     */
    @Select("select count(id) from setmeal where category_id = #{categoryId}")
    Integer countByCategoryId(Long id);

    /**
     * 根据id修改套餐
     *
     * @param setmeal
     */
    @AutoFill(OperationType.UPDATE)
    void update(Setmeal setmeal);

    /**
     * 新增套餐
     *
     * @param setmeal
     */
    @AutoFill(OperationType.INSERT)
    void insert(Setmeal setmeal);

    /**
     * 分页查询
     * @param setmealPageQueryDTO
     * @return
     */
    Page<SetmealVO> pageQuery(SetmealPageQueryDTO setmealPageQueryDTO);

    /**
     * 根据id查询套餐
     * @param id
     * @return
     */
    @Select("select * from setmeal where id = #{id}")
    Setmeal getById(Long id);

    /**
     * 根据主键集合批量查询套餐，用于批量删除前的统一校验。
     *
     * @param ids 套餐id集合
     * @return 套餐集合
     */
    List<Setmeal> getByIds(@Param("ids") List<Long> ids);

    /**
     * 根据id删除套餐
     * @param setmealId
     */
    @Delete("delete from setmeal where id = #{id}")
    void deleteById(Long setmealId);

    /**
     * 根据主键集合批量删除套餐。
     *
     * @param ids 套餐id集合
     */
    void deleteByIds(@Param("ids") List<Long> ids);

    /**
     * 批量更新套餐状态，菜品停售时联动停售关联套餐。
     *
     * @param ids    套餐id集合
     * @param status 目标状态
     */
    void updateStatusByIds(@Param("ids") List<Long> ids, @Param("status") Integer status);

    /**
     * 根据id查询套餐和套餐菜品关系
     * @param id
     * @return
     */
    SetmealVO getByIdWithDish(Long id);

    /**
     * 动态条件查询套餐
     * @param setmeal
     * @return
     */
    List<Setmeal> list(Setmeal setmeal);

    /**
     * 根据套餐id查询菜品选项
     * @param setmealId
     * @return
     */
    @Select("select sd.name, sd.copies, d.image, d.description " +
            "from setmeal_dish sd left join dish d on sd.dish_id = d.id " +
            "where sd.setmeal_id = #{setmealId}")
    List<DishItemVO> getDishItemBySetmealId(Long setmealId);

    /**
     * 根据条件统计套餐数量
     * @param map
     * @return
     */
    Integer countByMap(Map map);
}
