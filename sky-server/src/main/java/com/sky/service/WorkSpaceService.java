package com.sky.service;

import com.sky.vo.BusinessDataVO;
import com.sky.vo.DishOverViewVO;
import com.sky.vo.OrderOverViewVO;
import com.sky.vo.SetmealOverViewVO;

import java.time.LocalDateTime;

public interface WorkSpaceService {

    /**
     * 查询今日运营数据
     * @param begin
     * @param end
     * @return
     */
    BusinessDataVO getBusinessData(LocalDateTime begin, LocalDateTime end);

    /**
     * 查询订单管理数据
     * @param begin
     * @return
     */
    OrderOverViewVO getOrderOverView(LocalDateTime begin);

    /**
     * 查询菜品总览
     * @return
     */
    DishOverViewVO getDishOverView();

    /**
     * 查询套餐总览
     * @return
     */
    SetmealOverViewVO getSetmealOverView();
}
