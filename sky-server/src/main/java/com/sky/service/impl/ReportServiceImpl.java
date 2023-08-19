package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.entity.User;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.service.WorkSpaceService;
import com.sky.vo.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
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

    @Autowired
    private WorkSpaceService workSpaceService;

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


    /**
     * 导出Excel运营数据报表
     * @param response
     */
    @Override
    public void exportBusinessData(HttpServletResponse response) {
        //1、查询数据库，获取运营数据
        //获取最近30天内的第一天和最后一天
        LocalDate beginDay = LocalDate.now().minusDays(30);
        LocalDate endDay = LocalDate.now().minusDays(1);

        LocalDateTime beginTime = LocalDateTime.of(beginDay, LocalTime.MIN);  //开始时间
        LocalDateTime endTime = LocalDateTime.of(endDay, LocalTime.MAX);  //结束时间

        //查询近30天内的运营数据
        BusinessDataVO businessData = workSpaceService.getBusinessData(beginTime, endTime);

        //2、通过poi将数据写入excel文件中
        //获取输入流
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("template/运营数据报表模板.xlsx");
        try {
            //基于模板文件创建一个新的excel文件
            XSSFWorkbook excel = new XSSFWorkbook(inputStream);
            //获取表格文件的sheet页
            XSSFSheet sheet1 = excel.getSheet("Sheet1");

            //填充概览数据
            //第2行第2个单元格填充日期
            sheet1.getRow(1).getCell(1).setCellValue("时间："+beginDay+"至"+endDay);

            //获取第4行
            XSSFRow row = sheet1.getRow(3);
            row.getCell(2).setCellValue(businessData.getTurnover());  //第3个单元格填充营业额
            row.getCell(4).setCellValue(businessData.getOrderCompletionRate());  //第5个单元格填充订单完成率
            row.getCell(6).setCellValue(businessData.getNewUsers());  //第7个单元格填充新增用户数

            //获取第5行
            row=sheet1.getRow(4);
            row.getCell(2).setCellValue(businessData.getValidOrderCount());  //第3个单元格填充有效订单数
            row.getCell(4).setCellValue(businessData.getUnitPrice());  //第5个单元格填充平均客单价

            //填充明细数据
            for(int i=0;i<30;i++){
                LocalDate date=beginDay.plusDays(i);  //定义日期，根据i动态增加,初始日期为开始日期

                //查询当天运营数据
                LocalDateTime dateBegin = LocalDateTime.of(date, LocalTime.MIN);
                LocalDateTime dateEnd = LocalDateTime.of(date, LocalTime.MAX);
                BusinessDataVO businessData1 = workSpaceService.getBusinessData(dateBegin, dateEnd);

                //填充当天明细数据
                row=sheet1.getRow(7+i);  //动态获取行数
                row.getCell(1).setCellValue(date.toString());  //日期
                row.getCell(2).setCellValue(businessData1.getTurnover());  //营业额
                row.getCell(3).setCellValue(businessData1.getValidOrderCount());  //有效订单
                row.getCell(4).setCellValue(businessData1.getOrderCompletionRate());  //订单完成率
                row.getCell(5).setCellValue(businessData1.getUnitPrice());  //平均客单价
                row.getCell(6).setCellValue(businessData1.getNewUsers());  //新增用户数
            }

            //3、通过输出流将excel文件下载到客户端浏览器
            ServletOutputStream outputStream = response.getOutputStream();
            excel.write(outputStream);

            //关闭资源
            outputStream.close();
            excel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
