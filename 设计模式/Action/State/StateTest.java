package Action.State;

// 1. 抽象状态：订单状态
interface OrderState {
    void pay(OrderContext context);
    void ship(OrderContext context);
    void receive(OrderContext context);
}

// 2. 上下文：订单上下文
class OrderContext {
    private OrderState currentState;

    public OrderContext() {
        // 初始状态：待付款
        this.currentState = new WaitPayState();
    }

    public void setState(OrderState state) {
        this.currentState = state;
    }

    // 委托给当前状态处理
    public void pay() {
        currentState.pay(this);
    }

    public void ship() {
        currentState.ship(this);
    }

    public void receive() {
        currentState.receive(this);
    }
}

// 3. 具体状态：待付款
class WaitPayState implements OrderState {
    @Override
    public void pay(OrderContext context) {
        System.out.println("订单已付款，状态变更为：待发货");
        context.setState(new WaitSendState());
    }

    @Override
    public void ship(OrderContext context) {
        System.out.println("待付款状态，无法发货");
    }

    @Override
    public void receive(OrderContext context) {
        System.out.println("待付款状态，无法收货");
    }
}

// 3. 具体状态：待发货
class WaitSendState implements OrderState {
    @Override
    public void pay(OrderContext context) {
        System.out.println("已付款状态，无需重复付款");
    }

    @Override
    public void ship(OrderContext context) {
        System.out.println("订单已发货，状态变更为：待收货");
        context.setState(new WaitReceiveState());
    }

    @Override
    public void receive(OrderContext context) {
        System.out.println("待发货状态，无法收货");
    }
}

// 3. 具体状态：待收货
class WaitReceiveState implements OrderState {
    @Override
    public void pay(OrderContext context) {
        System.out.println("已付款状态，无需重复付款");
    }

    @Override
    public void ship(OrderContext context) {
        System.out.println("已发货状态，无需重复发货");
    }

    @Override
    public void receive(OrderContext context) {
        System.out.println("订单已收货，状态变更为：已完成");
        context.setState(new FinishedState());
    }
}

// 3. 具体状态：已完成
class FinishedState implements OrderState {
    @Override
    public void pay(OrderContext context) {
        System.out.println("已完成状态，无需操作");
    }

    @Override
    public void ship(OrderContext context) {
        System.out.println("已完成状态，无需操作");
    }

    @Override
    public void receive(OrderContext context) {
        System.out.println("已完成状态，无需操作");
    }
}

// 测试类
class StateTest {
    public static void main(String[] args) {
        OrderContext order = new OrderContext();
        // 模拟订单状态流转
        order.pay();    // 待付款 → 待发货
        order.ship();   // 待发货 → 待收货
        order.receive();// 待收货 → 已完成
        order.pay();    // 已完成，无操作
    }
}