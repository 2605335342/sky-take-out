package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.entity.Setmeal;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
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
     * @param dishDTO
     */
    @Transactional   //操作多张表，开启事务，同成功，同失败
    @Override
    public void saveWithFlavor(DishDTO dishDTO) {
        Dish dish =new Dish();
        BeanUtils.copyProperties(dishDTO,dish);

        //往菜品表插入一条数据
        dishMapper.insert(dish);

        Long id = dish.getId();  //获取dish表执行insert语句后返回的主键值（即id值）

        //往口味表插入n条数据
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if(flavors!=null && flavors.size()>0){
            //遍历flavors集合，为每个数据的dishId赋值，都为菜品id
            flavors.forEach(dishFlavor -> {
                dishFlavor.setDishId(id);
            });

            dishFlavorMapper.insertBatch(flavors);
        }
    }


    /**
     * 菜品分页查询
     * @param dishPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
        PageHelper.startPage(dishPageQueryDTO.getPage(),dishPageQueryDTO.getPageSize());
        Page<DishVO> page=dishMapper.pageQuery(dishPageQueryDTO);
        return new PageResult(page.getTotal(),page.getResult());
    }


    /**
     * 菜品批量删除
     * @param ids
     */
    @Transactional  //操作多张表，开启事务，同成功，同失败
    @Override
    public void deleteBatch(List<Long> ids) {
        //判断当前菜品能否删除
        //1、判断是否存在状态为起售中的菜品
        for (Long id : ids) {
            Dish dish=dishMapper.getById(id);
            if(dish.getStatus()== StatusConstant.ENABLE){
                //菜品为起售中
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
            }
        }

        //2、判断是否存在与套餐想关联的菜品
        List<Long> setmealIds = setmealDishMapper.getSetmealIdsByDishIds(ids);
        if(setmealIds!=null && setmealIds.size()>0){
            //存在与套餐想关联的菜品
            throw  new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }

        //没有以上情况，则删除菜品表和口味表对应的数据
        for (Long id : ids) {
            dishMapper.deleteById(id);
            dishFlavorMapper.deleteByDishId(id);
        }
    }


    /**
     * 根据id查询菜品数据和对应的口味数据
     * @param id
     * @return
     */
    @Override
    public DishVO getByIdWithFlavor(Long id) {
        //查询菜品数据
        Dish dish = dishMapper.getById(id);

        //查询口味数据
        List<DishFlavor> dishFlavors=dishFlavorMapper.getByDishId(id);

        //将查询到的数据封装成DishVo
        DishVO dishVO = new DishVO();
        BeanUtils.copyProperties(dish,dishVO);
        dishVO.setFlavors(dishFlavors);

        return dishVO;
    }


    /**
     * 修改菜品和对应的口味数据
     * @param dishDTO
     */
    @Transactional  //操作多张表，开启事务，同成功，同失败
    @Override
    public void updateWithFlavor(DishDTO dishDTO) {
        //修改菜品表
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO,dish);
        dishMapper.update(dish);

        //修改口味表：先删除原有数据，再重新插入现有数据
        dishFlavorMapper.deleteByDishId(dishDTO.getId());

        List<DishFlavor> dishFlavors = dishDTO.getFlavors();
        if(dishFlavors!=null && dishFlavors.size()>0){
            dishFlavors.forEach(dishFlavor -> {
                dishFlavor.setDishId(dishDTO.getId());
            });

            dishFlavorMapper.insertBatch(dishFlavors);
        }
    }


    /**
     * 菜品起售、停售
     * @param status
     * @param id
     */
    @Override
    public void startOrStop(Integer status, Long id) {
        Dish dish=new Dish();
        dish.setId(id);
        dish.setStatus(status);

        dishMapper.update(dish);

        //如果是将菜品停售，则包含当前菜品的套餐也要停售
        if(status==StatusConstant.DISABLE){
            List<Long> dishIds=new ArrayList<>();
            dishIds.add(id);

            List<Long> setmealIds = setmealDishMapper.getSetmealIdsByDishIds(dishIds);
            Setmeal setmeal=new Setmeal();
            for (Long setmealId : setmealIds) {
                setmeal.setId(setmealId);
                setmeal.setStatus(StatusConstant.DISABLE);
                setmealMapper.update(setmeal);
            }
        }
    }


    /**
     * 根据分类id查询菜品
     * @param categoryId
     * @return
     */
    @Override
    public List<Dish> list(Long categoryId) {
        Dish dish =new Dish();
        dish.setCategoryId(categoryId);
        dish.setStatus(StatusConstant.ENABLE);

        List<Dish> dishes=dishMapper.list(dish);

        return dishes;
    }


    /**
     * 条件查询菜品和对应的口味数据
     * @param dish
     * @return
     */
    @Override
    public List<DishVO> listWithFlavor(Dish dish) {
        //查询菜品数据
        List<Dish> dishes = dishMapper.list(dish);

        List<DishVO> voList=new ArrayList<>();
        for (Dish d : dishes) {
            //查询每个菜品对应的口味数据
            List<DishFlavor> flavors = dishFlavorMapper.getByDishId(d.getId());

            //构造DishVO
            DishVO dishVO = new DishVO();
            BeanUtils.copyProperties(d,dishVO);
            dishVO.setFlavors(flavors);

            //添加到集合中
            voList.add(dishVO);
        }
        return voList;
    }
}
