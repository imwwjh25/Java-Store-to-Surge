package Creator.Builder;

// 测试类
class BuilderTest {
    public static void main(String[] args) {
        // 链式调用，分步构建复杂订单
        Order order = new Order.OrderBuilder()
                .baseInfo("ORD001", "U001")
                .addItem("商品A")
                .addItem("商品B")
                .address("北京市朝阳区")
                .discount(10)
                .build();
        System.out.println(order);
    }
}
