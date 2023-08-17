package com.sky.service.impl;

import com.sky.constant.StatusConstant;
import com.sky.entity.Orders;
import com.sky.mapper.DishMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.WorkSpaceService;
import com.sky.vo.BusinessDataVO;
import com.sky.vo.DishOverViewVO;
import com.sky.vo.OrderOverViewVO;
import com.sky.vo.SetmealOverViewVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class WorkSpaceServiceImpl implements WorkSpaceService {
    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 查询今日运营数据
     * @param begin
     * @param end
     * @return
     */
    @Override
    public BusinessDataVO getBusinessData(LocalDateTime begin, LocalDateTime end) {
        //今日运营数据包括：营业额、有效订单、订单完成率、平均客单价、新增用户量
        Map map=new HashMap();
        map.put("beginTime",begin);
        map.put("endTime",end);

        //查询总订单数
        Integer totalOrderCount = orderMapper.countByMap(map);

        //查询有效订单数(状态为已完成的订单)
        map.put("status", Orders.COMPLETED);
        Integer validOrderCount= orderMapper.countByMap(map);

        //统计当日营业额(状态为已完成的订单的总金额)
        Double turnover = orderMapper.sumAmountByMap(map);
        turnover= turnover==null?0.0:turnover;  //若为空，则转化为0.0

        //计算订单完成率和平均客单价
        Double orderCompletionRate=0.0;
        Double unitPrice=0.0;
        if(totalOrderCount!=0 && validOrderCount!=0){
            orderCompletionRate= validOrderCount.doubleValue()/totalOrderCount;
            unitPrice=turnover/validOrderCount;  //平均客单价=营业额/有效订单数
        }

        //统计新增用户量
        Integer newUsers = userMapper.countByMap(map);

        //封装返回结果
        return BusinessDataVO.builder()
                .turnover(turnover)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .unitPrice(unitPrice)
                .newUsers(newUsers)
                .build();
    }


    /**
     * 查询订单管理数据
     * @param begin
     * @return
     */
    @Override
    public OrderOverViewVO getOrderOverView(LocalDateTime begin) {
        Map map=new HashMap();
        map.put("beginTime",begin);

        //全部订单
        Integer allOrders = orderMapper.countByMap(map);
        //待接单
        map.put("status",Orders.TO_BE_CONFIRMED);
        Integer waitingOrders = orderMapper.countByMap(map);
        //待派送
        map.put("status",Orders.CONFIRMED);
        Integer deliveredOrders = orderMapper.countByMap(map);
        //已完成
        map.put("status",Orders.COMPLETED);
        Integer completedOrders = orderMapper.countByMap(map);
        //已取消
        map.put("status",Orders.CANCELLED);
        Integer cancelledOrders = orderMapper.countByMap(map);

        //封装返回结果
        return OrderOverViewVO.builder()
                .allOrders(allOrders)
                .waitingOrders(waitingOrders)
                .deliveredOrders(deliveredOrders)
                .completedOrders(completedOrders)
                .cancelledOrders(cancelledOrders)
                .build();
    }


    /**
     * 查询菜品总览
     * @return
     */
    @Override
    public DishOverViewVO getDishOverView() {
        Map map=new HashMap();

        //统计已启售的菜品数量
        map.put("status",StatusConstant.ENABLE);
        Integer sold=dishMapper.countByMap(map);

        //统计已停售的菜品数量
        map.put("status",StatusConstant.DISABLE);
        Integer discontinued = dishMapper.countByMap(map);

        //封装返回结果
        return DishOverViewVO.builder()
                .sold(sold)
                .discontinued(discontinued)
                .build();
    }


    /**
     * 查询套餐总览
     * @return
     */
    @Override
    public SetmealOverViewVO getSetmealOverView() {
        Map map=new HashMap();

        //统计已启售的套餐数量
        map.put("status",StatusConstant.ENABLE);
        Integer sold=setmealMapper.countByMap(map);

        //统计已停售的套餐数量
        map.put("status",StatusConstant.DISABLE);
        Integer discontinued =setmealMapper.countByMap(map);

        //封装返回结果
        return SetmealOverViewVO.builder()
                .sold(sold)
                .discontinued(discontinued)
                .build();
    }
}
