package Action.Facade;

import java.util.Arrays;
import java.util.List;

// 1. 子系统类：购物车服务
class CartService {
    public List<String> getSelectedItems(String userId) {
        System.out.println("购物车：获取用户[" + userId + "]选中商品");
        return Arrays.asList("商品A", "商品B");
    }
}

// 1. 子系统类：商品服务
class ProductService {
    public double getTotalPrice(List<String> items) {
        System.out.println("商品：计算商品总价");
        return 200;
    }
}

// 1. 子系统类：库存服务
class StockService {
    public boolean lockStock(List<String> items) {
        System.out.println("库存：锁定商品库存");
        return true;
    }
}

// 1. 子系统类：物流服务
class LogisticsService {
    public double calculateFreight(String address) {
        System.out.println("物流：计算运费");
        return 10;
    }
}

// 2. 外观类：订单结算统一入口
class OrderSettleFacade {
    private CartService cartService = new CartService();
    private ProductService productService = new ProductService();
    private StockService stockService = new StockService();
    private LogisticsService logisticsService = new LogisticsService();

    // 高层统一接口：一键结算
    public void settleOrder(String userId, String address) {
        System.out.println("=== 订单结算开始 ===");
        // 封装子系统调用顺序
        List<String> items = cartService.getSelectedItems(userId);
        double productPrice = productService.getTotalPrice(items);
        stockService.lockStock(items);
        double freight = logisticsService.calculateFreight(address);
        double totalPrice = productPrice + freight;
        System.out.println("=== 订单结算完成，总价：" + totalPrice + " ===");
    }
}

// 测试类
class FacadeTest {
    public static void main(String[] args) {
        // 客户端仅需调用外观类，无需关心子系统细节
        OrderSettleFacade facade = new OrderSettleFacade();
        facade.settleOrder("U001", "北京市朝阳区");
    }
}
