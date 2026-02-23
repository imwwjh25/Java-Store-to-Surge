package Action.Observer;

import java.util.ArrayList;
import java.util.List;

// 1. 抽象观察者：订单状态观察者
interface OrderObserver {
    void update(String orderId, String status);
}

// 2. 抽象主题：订单状态主题
interface OrderSubject {
    void attach(OrderObserver observer);
    void detach(OrderObserver observer);
    void notifyObservers(String orderId, String status);
}

// 3. 具体主题：订单状态管理
class OrderStatusSubject implements OrderSubject {
    private List<OrderObserver> observers = new ArrayList<>();

    @Override
    public void attach(OrderObserver observer) {
        observers.add(observer);
    }

    @Override
    public void detach(OrderObserver observer) {
        observers.remove(observer);
    }

    @Override
    public void notifyObservers(String orderId, String status) {
        for (OrderObserver observer : observers) {
            observer.update(orderId, status);
        }
    }

    // 订单状态变更
    public void changeStatus(String orderId, String status) {
        System.out.println("订单[" + orderId + "]状态变更为：" + status);
        notifyObservers(orderId, status);
    }
}

// 4. 具体观察者：用户端通知
class UserObserver implements OrderObserver {
    @Override
    public void update(String orderId, String status) {
        System.out.println("用户端：推送订单[" + orderId + "]状态：" + status);
    }
}

// 4. 具体观察者：物流系统通知
class LogisticsObserver implements OrderObserver {
    @Override
    public void update(String orderId, String status) {
        if ("已付款".equals(status)) {
            System.out.println("物流系统：订单[" + orderId + "]准备发货");
        }
    }
}

// 4. 具体观察者：财务系统通知
class FinanceObserver implements OrderObserver {
    @Override
    public void update(String orderId, String status) {
        if ("已付款".equals(status)) {
            System.out.println("财务系统：订单[" + orderId + "]记录收款");
        }
    }
}

// 测试类
class ObserverTest {
    public static void main(String[] args) {
        // 创建主题和观察者
        OrderStatusSubject subject = new OrderStatusSubject();
        subject.attach(new UserObserver());
        subject.attach(new LogisticsObserver());
        subject.attach(new FinanceObserver());

        // 订单状态变更，自动通知所有观察者
        subject.changeStatus("ORD001", "已付款");
    }
}