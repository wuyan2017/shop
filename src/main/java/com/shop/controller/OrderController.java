package com.shop.controller;

import com.shop.model.*;
import com.shop.service.OrderService;
import com.shop.service.ProductService;
import com.shop.service.WalletService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.*;

@Controller
public class OrderController {

    @Resource
    private OrderService orderService;
    @Resource
    private WalletService walletService;
    @Resource
    private ProductService productService;

    //根据订单id查询订单
    @RequestMapping(value = "findByOid/{oid}")
    public String findByOid(@PathVariable("oid") Integer oid,
                            Map<String, Object> map) {
        Order order = orderService.findByOid(oid);
        map.put("order", order);
        System.out.println(map);
        return "order";
    }

    //查询订单
    @RequestMapping(value = "findOrderByUid/{page}")
    public String findOrderByUid(HttpSession session, Map<String, Object> map
            , @PathVariable("page") Integer page) {
        //从session获取user对象
        User user = (User) session.getAttribute("user");
        if (user == null) {
            map.put("notLogin", "notLogin");
            return "msg";
        }
        //查询总共有多少页的数据
        Integer count = orderService.findCountByUid(user.getUid());
        if (page > count) {
            page = 1;
        }
        //根据用户分页查询订单
        List<Order> orders = orderService.findByUid(user.getUid(), page);
        map.put("orders", orders);
        map.put("page", page);
        map.put("count", count);
        return "orderList";
    }

    //保存订单
    @RequestMapping(value = "/saveOrder")
    public String saveOrder(HttpSession session, Map<String, Object> map) {
        //判断用户是否登陆,
        User user = (User) session.getAttribute("user");
        if (user == null) {
            map.put("notLogin", "noLogin");
            return "msg";
        }
        //从session获取购物车对象
        Cart cart = (Cart) session.getAttribute("cart");
        //如果购物车为空，则返回到我的购物车页面
        if (cart == null) {
            return "redirect:myCart";
        }

        //订单对象
        Order order = new Order();
        order.setTotal(cart.getTotal());
        // 1:未付款. 2.已经付款，没有发货 3.发货，没有确认发货 4.交易完成
        order.setState(1);
        // 设置订单时间
        order.setOrdertime(new Date());
        // 设置订单关联的客户:
        order.setUser(user);
        // 设置订单项集合:
        Set<OrderItem> sets = new HashSet<OrderItem>();
        for (CartItem cartItem : cart.getCartItems()) {
            // 订单项的信息从购物项获得的.
            OrderItem orderItem = new OrderItem();
            orderItem.setCount(cartItem.getCount());
            orderItem.setSubtotal(cartItem.getSubtotal());
            orderItem.setProduct(cartItem.getProduct());
            //双向关联时在多的一方设置一的一方的属性
            orderItem.setOrder(order);
            //把orderItem对象添加到集合中
            sets.add(orderItem);
        }
        //双向关联时在一的一方设置多的一方的属性
        order.setOrderItems(sets);
        orderService.save(order);
        //清除购物车
        cart.clearCart();
        map.put("order", order);
        return "order";
    }

    /* 确认订单的操作,为订单付款:
     * addr:收货人地址 name:收货人 phone:联系电话  oid:订单的oid
     * pd_FrpId:接收支付通道编码
     */
    @RequestMapping(value = "/payOrder")
    public String payOrder(Integer oid, String addr, String name, String phone, String total, HttpSession session,
                           Map<String, Object> map) {
        Order order = orderService.findByOid(oid);
        order.setAddr(addr);
        order.setName(name);
        order.setPhone(phone);
        orderService.update(order);

        User user = order.getUser();
        Wallet wallet = user.getWallet();
        Float money = wallet.getMoney();
        Float total1 = Float.parseFloat(total);
        if (money >= total1) {       //更新商品数量
            for (OrderItem orderItem : order.getOrderItems()) {
                Integer pid = orderItem.getProduct().getPid();
                Integer inventory = orderItem.getProduct().getInventory();
                Product product = orderItem.getProduct();
                product.setInventory(inventory - orderItem.getCount());
                productService.update(product);
            }
            wallet.setMoney(money - total1);
            walletService.update(wallet);
            // 修改订单的状态:
            Order currOrder = orderService.findByOid(oid);
            // 修改订单状态为2:已经付款:
            currOrder.setState(2);
            orderService.save(currOrder);

            map.put("paymentSuccess", "success");
            return "orderList";
        }
        map.put("paymentFalse", "false");
        return "orderList";
    }

    /*
     * 付款成功后跳转回来的路径:
     * r6_Order,r3_Amt 接收付款成功后的参数:
     */
    @RequestMapping(value = "/callBack")
    public String callBack(Integer r6_Order, Integer r3_Amt, Map<String, Object> map) {
        // 修改订单的状态:
        Order currOrder = orderService.findByOid(r6_Order);
        // 修改订单状态为2:已经付款:
        currOrder.setState(2);
        orderService.save(currOrder);
        map.put("success", "支付成功!订单编号为: " + r6_Order + " 付款金额为: " + r3_Amt);
        return "msg";
    }

    //确认收货
    @RequestMapping(value = "updateState/{oid}")
    public ModelAndView updateState(@PathVariable("oid") Integer oid) {
        System.out.println(oid);
        Order order = orderService.findByOid(oid);
        order.setState(4);
        orderService.save(order);
        ModelAndView model = new ModelAndView("redirect:/findOrderByUid/1");
        return model;
    }
}
