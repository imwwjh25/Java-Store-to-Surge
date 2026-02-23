package Creator.Builder;

import java.util.ArrayList;
import java.util.List;

// 1. 产品：复杂订单对象
class Order {
    private String orderId;
    private String userId;
    private List<String> items;
    private String address;
    private double discount;

    // 私有构造，仅通过Builder构建
    private Order(OrderBuilder builder) {
        this.orderId = builder.orderId;
        this.userId = builder.userId;
        this.items = builder.items;
        this.address = builder.address;
        this.discount = builder.discount;
    }

    @Override
    public String toString() {
        return "订单{" +
                "orderId='" + orderId + '\'' +
                ", userId='" + userId + '\'' +
                ", items=" + items +
                ", address='" + address + '\'' +
                ", discount=" + discount +
                '}';
    }

    // 2. 静态内部类：具体建造者
    public static class OrderBuilder {
        private String orderId;
        private String userId;
        private List<String> items = new ArrayList<>();
        private String address;
        private double discount = 0;

        // 基础信息构建
        public OrderBuilder baseInfo(String orderId, String userId) {
            this.orderId = orderId;
            this.userId = userId;
            return this;
        }

        // 商品构建
        public OrderBuilder addItem(String item) {
            this.items.add(item);
            return this;
        }

        // 地址构建
        public OrderBuilder address(String address) {
            this.address = address;
            return this;
        }

        // 优惠构建
        public OrderBuilder discount(double discount) {
            this.discount = discount;
            return this;
        }

        // 最终构建产品
        public Order build() {
            return new Order(this);
        }
    }
}

