package Action.Template;

// 1. 抽象类：订单处理模板
abstract class AbstractOrderProcess {
    // 模板方法：定义算法骨架（final防止子类修改）
    public final void processOrder(String orderId) {
        System.out.println("=== 订单处理开始：" + orderId + " ===");
        validateOrder(orderId);    // 步骤1：校验订单（公共）
        lockStock(orderId);        // 步骤2：锁定库存（子类实现）
        calculatePrice(orderId);   // 步骤3：计算价格（公共）
        createOrder(orderId);      // 步骤4：创建订单（公共）
        System.out.println("=== 订单处理完成：" + orderId + " ===");
    }

    // 具体步骤：公共逻辑，父类实现
    protected void validateOrder(String orderId) {
        System.out.println("校验订单：" + orderId);
    }

    protected void calculatePrice(String orderId) {
        System.out.println("计算价格：" + orderId);
    }

    protected void createOrder(String orderId) {
        System.out.println("创建订单：" + orderId);
    }

    // 抽象步骤：可变逻辑，子类实现
    protected abstract void lockStock(String orderId);
}

// 2. 具体子类：普通订单处理
class NormalOrderProcess extends AbstractOrderProcess {
    @Override
    protected void lockStock(String orderId) {
        System.out.println("普通订单：锁定库存（普通锁）");
    }
}

// 2. 具体子类：秒杀订单处理
class SeckillOrderProcess extends AbstractOrderProcess {
    @Override
    protected void lockStock(String orderId) {
        System.out.println("秒杀订单：锁定库存（分布式锁+预扣减）");
    }
}

// 测试类
class TemplateMethodTest {
    public static void main(String[] args) {
        // 普通订单
        AbstractOrderProcess normalOrder = new NormalOrderProcess();
        normalOrder.processOrder("ORD001");

        System.out.println();

        // 秒杀订单
        AbstractOrderProcess seckillOrder = new SeckillOrderProcess();
        seckillOrder.processOrder("ORD002");
    }
}