package com.sky.controller.admin;

import com.sky.result.Result;
import com.sky.service.WorkSpaceService;
import com.sky.vo.BusinessDataVO;
import com.sky.vo.DishOverViewVO;
import com.sky.vo.OrderOverViewVO;
import com.sky.vo.SetmealOverViewVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 工作台管理
 */
@RestController
@RequestMapping("/admin/workspace")
@Api(tags = "工作台接口")
@Slf4j
public class WorkSpaceController {
    @Autowired
    private WorkSpaceService workSpaceService;

    /**
     * 查询今日运营数据
     * @return
     */
    @GetMapping("/businessData")
    @ApiOperation("查询今日运营数据")
    public Result<BusinessDataVO> businessData(){
        LocalDateTime begin = LocalDateTime.now().with(LocalTime.MIN);  //获得当天的开始时间
        LocalDateTime end = LocalDateTime.now().with(LocalTime.MAX);  //获得当天的结束时间
        BusinessDataVO businessDataVO=workSpaceService.getBusinessData(begin,end);
        return Result.success(businessDataVO);
    }


    /**
     * 查询订单管理数据
     * @return
     */
    @GetMapping("/overviewOrders")
    @ApiOperation("查询订单管理数据")
    public Result<OrderOverViewVO> orderOverView(){
        LocalDateTime begin = LocalDateTime.now().with(LocalTime.MIN);  //获得当天的开始时间
        OrderOverViewVO orderOverViewVO=workSpaceService.getOrderOverView(begin);
        return Result.success(orderOverViewVO);
    }


    /**
     * 查询菜品总览
     * @return
     */
    @GetMapping("/overviewDishes")
    @ApiOperation("查询菜品总览")
    public Result<DishOverViewVO> dishOverView(){
        DishOverViewVO dishOverViewVO=workSpaceService.getDishOverView();
        return Result.success(dishOverViewVO);
    }


    /**
     * 查询套餐总览
     * @return
     */
    @GetMapping("/overviewSetmeals")
    @ApiOperation("查询套餐总览")
    public Result<SetmealOverViewVO> setmealOverView(){
        SetmealOverViewVO setmealOverViewVO=workSpaceService.getSetmealOverView();
        return Result.success(setmealOverViewVO);
    }
}
