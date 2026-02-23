package Action.Decorator;

// 1. 抽象组件：订单价格计算器
interface OrderPriceCalculator {
    double calculate(double originalPrice);
}

// 2. 具体组件：基础价格计算器（仅计算商品总价）
class BasePriceCalculator implements OrderPriceCalculator {
    @Override
    public double calculate(double originalPrice) {
        return originalPrice;
    }
}

// 3. 抽象装饰器：价格装饰器
abstract class PriceDecorator implements OrderPriceCalculator {
    protected OrderPriceCalculator calculator;

    public PriceDecorator(OrderPriceCalculator calculator) {
        this.calculator = calculator;
    }
}

// 4. 具体装饰器：满减装饰器
class FullReductionDecorator extends PriceDecorator {
    private double fullAmount;
    private double reductionAmount;

    public FullReductionDecorator(OrderPriceCalculator calculator, double fullAmount, double reductionAmount) {
        super(calculator);
        this.fullAmount = fullAmount;
        this.reductionAmount = reductionAmount;
    }

    @Override
    public double calculate(double originalPrice) {
        double price = calculator.calculate(originalPrice);
        // 满减逻辑
        if (price >= fullAmount) {
            price -= reductionAmount;
            System.out.println("满减：满" + fullAmount + "减" + reductionAmount);
        }
        return price;
    }
}

// 4. 具体装饰器：优惠券装饰器
class CouponDecorator extends PriceDecorator {
    private double couponAmount;

    public CouponDecorator(OrderPriceCalculator calculator, double couponAmount) {
        super(calculator);
        this.couponAmount = couponAmount;
    }

    @Override
    public double calculate(double originalPrice) {
        double price = calculator.calculate(originalPrice);
        // 优惠券逻辑
        price -= couponAmount;
        System.out.println("优惠券：减" + couponAmount);
        return price;
    }
}

// 测试类
class DecoratorTest {
    public static void main(String[] args) {
        // 原始价格
        double originalPrice = 300;
        System.out.println("原始价格：" + originalPrice);

        // 动态叠加：基础价格 → 满减（300-50） → 优惠券（20）
        OrderPriceCalculator calculator = new CouponDecorator(
                new FullReductionDecorator(
                        new BasePriceCalculator(), 300, 50
                ), 20
        );

        double finalPrice = calculator.calculate(originalPrice);
        System.out.println("最终价格：" + finalPrice);
    }
}