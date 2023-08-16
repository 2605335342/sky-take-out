package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.entity.User;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportServiceImpl implements ReportService {
    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private UserMapper userMapper;

    /**
     * 统计指定时间区间内的营业额数据
     * @param begin
     * @param end
     * @return
     */
    @Override
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
        //dateList用于存放从begin到end区间的日期
        List<LocalDate> dateList=new ArrayList<>();

        dateList.add(begin);
        while (!begin.equals(end)){
            //日期计算
            begin=begin.plusDays(1);
            dateList.add(begin);
        }

        //turnoverList用于存放日期区间每天对应的营业额
        List<Double> turnoverList=new ArrayList<>();
        for (LocalDate date : dateList) {
            //查找date日期对应的营业额，即状态为“已完成”的订单金额合计
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);  //开始时间（00:00:00）
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);  //结束时间（23:59:59）

            //select sum(amount) from orders where order_time > beginTime and order_time < endTime and status =5
            //用map集合存放参数（beginTime,endTime,status）
            Map map =new HashMap();
            map.put("beginTime",beginTime);
            map.put("endTime",endTime);
            map.put("status", Orders.COMPLETED);

            Double turnover=orderMapper.sumAmountByMap(map);  //date日期对应的营业额

            //若date日期对应的营业额为空，转化为0.0
            if(turnover==null){
                turnover=0.0;
            }

            turnoverList.add(turnover);
        }
        //将日期集合和营业额集合转化为字符串，各个元素之间逗号隔开
        String dateStr = StringUtils.join(dateList, ",");
        String turnoverStr = StringUtils.join(turnoverList, ",");

        //封装返回结果
        TurnoverReportVO turnoverReportVO = TurnoverReportVO.builder()
                .dateList(dateStr)
                .turnoverList(turnoverStr)
                .build();
        return turnoverReportVO;
    }


    /**
     * 统计指定时间区间内的用户数据
     * @param begin
     * @param end
     * @return
     */
    @Override
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        //存放从begin到end区间的日期
       List<LocalDate> dateList=new ArrayList<>();
        //存放从begin到end区间每天的总用户数量
        List<Integer> totalUserList=new ArrayList<>();
        //存放从begin到end区间每天的新增用户数量
        List<Integer> newUserList=new ArrayList<>();

        dateList.add(begin);
        while(!begin.equals(end)){
            begin=begin.plusDays(1);
            dateList.add(begin);
        }

        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);  //开始时间（00:00:00）
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);  //结束时间（23:59:59）

            //1、查询每天的总用户数量:select count(id) from user where create_time < endTime
            //2、查询每天的新增用户数量：select count(id) from user where create_time > beginTime and create_time < endTime
            Map map =new HashMap();
            map.put("endTime",endTime);
            Integer totalUser=userMapper.countByMap(map);  //总用户数量
            totalUserList.add(totalUser);

            map.put("beginTime",beginTime);
            Integer newUser=userMapper.countByMap(map);  //新增用户数量
            newUserList.add(newUser);
        }
        //将集合转化为字符串，各个元素之间逗号隔开
        String dateStr = StringUtils.join(dateList, ",");
        String totalUserStr = StringUtils.join(totalUserList, ",");
        String newUserStr = StringUtils.join(newUserList, ",");

        //封装返回结果
        UserReportVO userReportVO = UserReportVO.builder()
                .dateList(dateStr)
                .totalUserList(totalUserStr)
                .newUserList(newUserStr)
                .build();
        return userReportVO;
    }


    /**
     * 统计指定时间区间内的订单数据
     * @param begin
     * @param end
     * @return
     */
    @Override
    public OrderReportVO getOrderStatistics(LocalDate begin, LocalDate end) {
        //存放从begin到end区间的日期
        List<LocalDate> dateList=new ArrayList<>();
        //存放从begin到end区间的每日订单数
        List<Integer> orderCountList=new ArrayList<>();
        //存放从begin到end区间的每日有效订单数
        List<Integer> validOrderCountList=new ArrayList<>();

        dateList.add(begin);
        while(!begin.equals(end)){
            begin=begin.plusDays(1);
            dateList.add(begin);
        }

        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);  //开始时间（00:00:00）
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);  //结束时间（23:59:59）

            //1、查询每日订单数:select count(id) from orders where order_time > ? and order_time < ?
            //2、查询每日有效订单数:select count(id) from orders where order_time > ? and order_time < ? and status = 5
            Map map=new HashMap();
            map.put("beginTime",beginTime);
            map.put("endTime",endTime);
            Integer orderCount=orderMapper.countByMap(map);  //每日订单数
            orderCountList.add(orderCount);

            map.put("status",Orders.COMPLETED);
            Integer validOrderCount=orderMapper.countByMap(map);  //每日有效订单数
            validOrderCountList.add(validOrderCount);
        }

        //计算订单总数、有效订单数(集合遍历累加求和)
        Integer totalOrderCount = orderCountList.stream().reduce(Integer::sum).get();  //订单总数
        Integer validOrderCount = validOrderCountList.stream().reduce(Integer::sum).get();  //有效订单数

        //计算订单完成率
        Double orderCompletionRate=0.0;
        if(totalOrderCount!=0){
            orderCompletionRate=validOrderCount.doubleValue() / totalOrderCount;
        }

        //封装返回结果
        OrderReportVO orderReportVO = OrderReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .orderCountList(StringUtils.join(orderCountList, ","))
                .validOrderCountList(StringUtils.join(validOrderCountList, ","))
                .totalOrderCount(totalOrderCount)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .build();
        return orderReportVO;
    }


    /**
     * 统计指定时间区间内的销量top10数据
     * @param begin
     * @param end
     * @return
     */
    @Override
    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);
        List<GoodsSalesDTO> salesTop10 = orderMapper.getSalesTop10(beginTime, endTime);

        //将salesTop10的每个name取出来，组成一个新集合
        List<String> names = salesTop10.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList());
        //将salesTop10的每个number取出来，组成一个新集合
        List<Integer> numbers = salesTop10.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList());

        //将集合names和numbers转为字符串，各个元素之间用逗号隔开
        String nameList = StringUtils.join(names, ",");
        String numberList = StringUtils.join(numbers, ",");

        //封装返回结果
        SalesTop10ReportVO salesTop10ReportVO = SalesTop10ReportVO.builder()
                .nameList(nameList)
                .numberList(numberList)
                .build();
        return salesTop10ReportVO;
    }
}
