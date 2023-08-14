package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private AddressBookMapper addressBookMapper;

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private WebSocketServer webSocketServer;

    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    @Transactional
    @Override
    public OrderSubmitVO submit(OrdersSubmitDTO ordersSubmitDTO) {
        //处理各种异常（地址簿为空，购物车为空）
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if(addressBook==null){
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        ShoppingCart shoppingCart=new ShoppingCart();
        Long userId = BaseContext.getCurrentId();  //用户id
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);
        if(shoppingCartList==null || shoppingCartList.size()==0){
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        //1、向订单表插入1条数据
        Orders orders=new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO,orders);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));  //设置订单号
        orders.setStatus(Orders.PENDING_PAYMENT);  //设置订单状态：待付款
        orders.setUserId(userId);  //设置下单用户id
        orders.setOrderTime(LocalDateTime.now());  //设置下单时间
        orders.setPayStatus(Orders.UN_PAID);  //设置支付状态：未支付
        orders.setPhone(addressBook.getPhone());  //设置手机号
        orders.setConsignee(addressBook.getConsignee());  //设置收货人

        orderMapper.insert(orders);

        //2、向订单详细表插入n条数据
        List<OrderDetail> orderDetailList=new ArrayList<>();
        for (ShoppingCart cart : shoppingCartList) {
            OrderDetail orderDetail=new OrderDetail();
            BeanUtils.copyProperties(cart,orderDetail);
            orderDetail.setOrderId(orders.getId());  //设置订单id
            orderDetailList.add(orderDetail);
        }

        orderDetailMapper.insertBatch(orderDetailList);
        
        //3、清空购物车
        shoppingCartMapper.deleteByUserId(userId);

        //4、封装返回结果
        OrderSubmitVO orderSubmitVO = new OrderSubmitVO();
        orderSubmitVO.setId(orders.getId());  //设置订单id
        orderSubmitVO.setOrderNumber(orders.getNumber());  //设置订单号
        orderSubmitVO.setOrderAmount(orders.getAmount());  //设置订单金额
        orderSubmitVO.setOrderTime(orders.getOrderTime());  //设置下单时间

        return orderSubmitVO;
    }


    /**
     * 订单支付
     * @param ordersPaymentDTO
     */
    @Override
    public void payment(OrdersPaymentDTO ordersPaymentDTO) {
        //根据订单号查询订单
        String orderNumber = ordersPaymentDTO.getOrderNumber();
        Orders orders=orderMapper.getByNumber(orderNumber);

        //根据订单id更新订单的状态、支付状态、结账时间
        Orders orders1 = new Orders();
        orders1.setId(orders.getId());  //设置订单id
        orders1.setStatus(Orders.TO_BE_CONFIRMED);  //更新订单的状态:待接单
        orders1.setPayStatus(Orders.PAID);  //更新支付状态:已支付
        orders1.setCheckoutTime(LocalDateTime.now());  //更新结账时间

        orderMapper.update(orders1);

        //通过websocket向客户端浏览器推送消息（参数：type、orderId、content）
        Map map=new HashMap();
        map.put("type",1);  //1：来单提醒  2：客户催单
        map.put("orderId",orders.getId());  //订单id
        map.put("content","订单号："+orderNumber);  //消息内容

        String json = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);
    }


    /**
     * 用户端历史订单分页查询
     * @param page
     * @param pageSize
     * @param status
     * @return
     */
    @Override
    public PageResult pageQuery4User(int page, int pageSize, Integer status) {
        PageHelper.startPage(page,pageSize);  //设置分页
        OrdersPageQueryDTO ordersPageQueryDTO=new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        ordersPageQueryDTO.setStatus(status);

        Page<Orders> ordersPage=orderMapper.pageQuery(ordersPageQueryDTO);  //分页条件查询

        List<OrderVO> orderVOS=new ArrayList<>();  //OrderVO集合
        //查询出订单明细，并封装入OrderVO进行响应
        for (Orders orders : ordersPage) {
            Long orderId = orders.getId();  //获取订单id
            List<OrderDetail> orderDetails=orderDetailMapper.getByOrderId(orderId);  //查询订单明细

            //封装入OrderVO
            OrderVO orderVO = new OrderVO();
            BeanUtils.copyProperties(orders,orderVO);
            orderVO.setOrderDetailList(orderDetails);

            orderVOS.add(orderVO);
        }

        return new PageResult(ordersPage.getTotal(),orderVOS);
    }


    /**
     * 查询订单详情
     * @param id
     * @return
     */
    @Override
    public OrderVO details(Long id) {
        //根据id查询订单
        Orders orders=orderMapper.getById(id);

        //根据订单id查询订单详情
        List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(id);

        //封装成OrderVO并返回
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders,orderVO);
        orderVO.setOrderDetailList(orderDetails);
        return orderVO;
    }


    /**
     * 用户取消订单
     * @param id
     */
    @Override
    public void userCancelById(Long id) {
        //根据id查询订单
        Orders orders = orderMapper.getById(id);

        //校验订单是否存在
        if(orders==null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        //订单状态大于2无法取消
        if(orders.getStatus()>2){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        //更新订单状态、取消原因、取消时间、支付状态
        Orders orders1 = new Orders();
        orders1.setId(orders.getId());  //设置订单id
        orders1.setStatus(Orders.CANCELLED);  //更新订单状态：已取消
        orders1.setCancelReason("用户取消");  //更新取消原因
        orders1.setCancelTime(LocalDateTime.now());  //更新取消时间
        orders1.setPayStatus(Orders.REFUND);  //更新支付状态：退款

        orderMapper.update(orders1);
    }


    /**
     * 再来一单
     * @param id
     */
    @Override
    public void repetition(Long id) {
        //查询当前用户id
        Long userId = BaseContext.getCurrentId();

        //根据订单id查询当前订单详情
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);

        //将订单详情对象转换为购物车对象
        List<ShoppingCart> shoppingCartList = orderDetailList.stream().map(x -> {
            ShoppingCart shoppingCart = new ShoppingCart();

            //将原订单详情里面的菜品信息重新复制到购物车对象中
            BeanUtils.copyProperties(x, shoppingCart, "id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());

            return shoppingCart;
        }).collect(Collectors.toList());

        //将购物车对象批量添加到数据库
        shoppingCartMapper.insertBatch(shoppingCartList);
    }


    /**
     * 管理端订单搜索
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(),ordersPageQueryDTO.getPageSize());
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        //部分订单状态，需要额外返回订单菜品信息，将Orders转化为OrderVO
        List<OrderVO> orderVOList=getOrderVoList(page);
        return new PageResult(page.getTotal(),orderVOList);
    }

    private List<OrderVO> getOrderVoList(Page<Orders> page) {
        //需要返回订单菜品信息，自定义OrderVO响应结果
        List<Orders> ordersList = page.getResult();

        List<OrderVO> orderVOList=new ArrayList<>();
        if(!CollectionUtils.isEmpty(ordersList)){
            for (Orders orders : ordersList) {
                //将共同字段复制到OrderVO
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders,orderVO);

                //获取菜品信息字符串，封装进OrderVO中，最后添加到orderVOList中
                String orderDishes=getOrderDishesStr(orders);
                orderVO.setOrderDishes(orderDishes);
                orderVOList.add(orderVO);
            }
        }
        return orderVOList;
    }

    //根据订单id获取菜品信息字符串
    private String getOrderDishesStr(Orders orders) {
        //查询订单菜品详情信息（订单中的菜品和数量）
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        //将每一条订单菜品信息拼接为字符串（格式：宫保鸡丁*3；）
        List<String> orderDishList = orderDetailList.stream().map(x -> {
            String orderDish = x.getName() + "*" + x.getNumber() + ";";
            return orderDish;
        }).collect(Collectors.toList());

        //将该订单对应的所有菜品信息拼接在一起
        return String.join("", orderDishList);
    }


    /**
     * 各个状态的订单数量统计
     * @return
     */
    @Override
    public OrderStatisticsVO statistics() {
        //1、根据状态，分别查询出待接单、待派送、派送中的订单数量
        Integer toBeConfirmed=orderMapper.countStatus(Orders.TO_BE_CONFIRMED);  //待接单
        Integer confirmed = orderMapper.countStatus(Orders.CONFIRMED);  //待派送
        Integer deliveryInProgress = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);  //派送中

        //2、封装成OrderStatisticsVO
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);
        return orderStatisticsVO;
    }


    /**
     * 接单
     * @param ordersConfirmDTO
     */
    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders orders = Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED)
                .build();

        orderMapper.update(orders);
    }


    /**
     * 拒单
     * @param ordersRejectionDTO
     */
    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) {
        //根据id查询订单
        Orders orders = orderMapper.getById(ordersRejectionDTO.getId());

        //订单只有存在且状态为2（待接单）才可以拒单
        if(orders==null || !orders.getStatus().equals(Orders.TO_BE_CONFIRMED)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        //根据订单id更新订单状态、拒单原因、取消时间
        Orders orders1 = new Orders();
        orders1.setId(ordersRejectionDTO.getId());  //设置订单id
        orders1.setStatus(Orders.CANCELLED);  //更新订单状态：已取消
        orders1.setCancelReason(ordersRejectionDTO.getRejectionReason());  //设置拒单原因
        orders1.setCancelTime(LocalDateTime.now());  //设置取消时间

        orderMapper.update(orders1);
    }


    /**
     * 商家取消订单
     * @param ordersCancelDTO
     */
    @Override
    public void cancel(OrdersCancelDTO ordersCancelDTO) {
        //管理端取消订单，根据订单id更新订单状态、取消原因、取消时间
        Orders orders = new Orders();
        orders.setId(ordersCancelDTO.getId());  //设置订单id
        orders.setStatus(Orders.CANCELLED);  //更新订单状态：已取消
        orders.setCancelReason(ordersCancelDTO.getCancelReason());  //设置取消原因
        orders.setCancelTime(LocalDateTime.now());  //设置取消时间

        orderMapper.update(orders);
    }


    /**
     * 派送订单
     * @param id
     */
    @Override
    public void delivery(Long id) {
        //根据id查询订单
        Orders orders = orderMapper.getById(id);

        //校验订单是否存在，并且状态只有为已接单（即3）才可派送
        if(orders==null || !orders.getStatus().equals(Orders.CONFIRMED)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        //更新订单状态,状态转为派送中
        Orders orders1 = new Orders();
        orders1.setId(id);
        orders1.setStatus(Orders.DELIVERY_IN_PROGRESS);

        orderMapper.update(orders1);
    }


    /**
     * 完成订单
     * @param id
     */
    @Override
    public void complete(Long id) {
        //根据id查询订单
        Orders orders = orderMapper.getById(id);

        //校验订单是否存在，并且状态只有为派送中（即4）才可完成
        if(orders==null || !orders.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        //更新订单状态,状态转为已完成,更新送达时间
        Orders orders1 = new Orders();
        orders1.setId(id);
        orders1.setStatus(Orders.COMPLETED);
        orders1.setDeliveryTime(LocalDateTime.now());  //设置送达时间

        orderMapper.update(orders1);
    }


    /**
     * 催单
     * @param id
     */
    @Override
    public void reminder(Long id) {
        //根据id查询订单
        Orders orders = orderMapper.getById(id);

        //判断订单是否存在
        if(orders==null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //通过websocket向客户端浏览器推送消息（参数：type、orderId、content）
        Map map=new HashMap();
        map.put("type",2);  //1：来单提醒  2：客户催单
        map.put("orderId",id);  //订单id
        map.put("content","订单号："+orders.getNumber());  //消息内容

        String json = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);
    }
}
