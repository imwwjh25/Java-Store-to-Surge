## 一、结构型模式（剩余 4 种）：聚焦类 / 对象组合，解耦结构与功能

### 13. 桥接模式（Bridge）

**模式定义**：将抽象与实现解耦，使二者可以独立变化。它用组合关系代替多层继承，彻底避免多维度变化导致的类爆炸问题。

**核心角色**：抽象化角色、扩展抽象化角色、实现化角色、具体实现化角色

**电商场景**：商品品类与商品属性两个独立维度解耦。电商中「商品品类（服装、家电、食品）」和「属性维度（基本属性、销售属性、跨境属性）」是两个频繁独立扩展的维度，用桥接模式避免品类 × 属性的类爆炸。





```
// 1. 实现化角色：属性维度接口（独立变化的维度）
interface ProductAttribute {
    void showAttribute();
}

// 2. 具体实现化角色：基本属性
class BaseAttribute implements ProductAttribute {
    @Override
    public void showAttribute() {
        System.out.println("【基本属性】商品名称、类目、品牌、价格");
    }
}

// 2. 具体实现化角色：销售属性
class SaleAttribute implements ProductAttribute {
    @Override
    public void showAttribute() {
        System.out.println("【销售属性】规格、库存、限购数量、发货时效");
    }
}

// 2. 具体实现化角色：跨境属性
class CrossBorderAttribute implements ProductAttribute {
    @Override
    public void showAttribute() {
        System.out.println("【跨境属性】保税仓、税费、清关要求、运输时效");
    }
}

// 3. 抽象化角色：商品品类（另一个独立变化的维度）
abstract class ProductCategory {
    // 组合属性维度，桥接两个独立维度
    protected ProductAttribute attribute;

    public ProductCategory(ProductAttribute attribute) {
        this.attribute = attribute;
    }

    // 抽象方法：展示品类信息
    public abstract void showCategory();
}

// 4. 扩展抽象化角色：服装品类
class ClothingCategory extends ProductCategory {
    public ClothingCategory(ProductAttribute attribute) {
        super(attribute);
    }

    @Override
    public void showCategory() {
        System.out.println("===== 服装品类 =====");
        attribute.showAttribute();
    }
}

// 4. 扩展抽象化角色：家电品类
class ApplianceCategory extends ProductCategory {
    public ApplianceCategory(ProductAttribute attribute) {
        super(attribute);
    }

    @Override
    public void showCategory() {
        System.out.println("===== 家电品类 =====");
        attribute.showAttribute();
    }
}

// 4. 扩展抽象化角色：跨境食品品类
class CrossBorderFoodCategory extends ProductCategory {
    public CrossBorderFoodCategory(ProductAttribute attribute) {
        super(attribute);
    }

    @Override
    public void showCategory() {
        System.out.println("===== 跨境食品品类 =====");
        attribute.showAttribute();
    }
}

// 测试类
class BridgeTest {
    public static void main(String[] args) {
        // 服装品类 + 销售属性
        ProductCategory clothing = new ClothingCategory(new SaleAttribute());
        clothing.showCategory();

        // 家电品类 + 基本属性
        ProductCategory appliance = new ApplianceCategory(new BaseAttribute());
        appliance.showCategory();

        // 跨境食品 + 跨境属性
        ProductCategory crossFood = new CrossBorderFoodCategory(new CrossBorderAttribute());
        crossFood.showCategory();

        // 新增品类/属性，仅需新增对应类，无需修改原有代码
    }
}
```

------

### 14. 组合模式（Composite）

**模式定义**：将对象组合成树形结构以表示「整体 - 部分」的层次关系，使得用户对单个对象和组合对象的使用具有一致性。

**核心角色**：抽象组件、叶子节点、组合节点

**电商场景**：商品分类树形结构管理。电商的商品分类是典型的树形结构（一级分类→二级分类→具体商品），用组合模式可以统一处理分类和商品，无需区分是单个商品还是分类集合。









```
import java.util.ArrayList;
import java.util.List;

// 1. 抽象组件：分类/商品统一接口
interface CategoryComponent {
    // 展示信息
    void showInfo(int depth);
    // 计算总价
    double getTotalPrice();
}

// 2. 叶子节点：具体商品（最小单元，不能包含子节点）
class ProductLeaf implements CategoryComponent {
    private String productName;
    private double price;

    public ProductLeaf(String productName, double price) {
        this.productName = productName;
        this.price = price;
    }

    @Override
    public void showInfo(int depth) {
        System.out.println("  ".repeat(depth) + "└─ 商品：" + productName + "，价格：" + price);
    }

    @Override
    public double getTotalPrice() {
        return price;
    }
}

// 3. 组合节点：商品分类（可包含子分类/商品）
class CategoryComposite implements CategoryComponent {
    private String categoryName;
    // 子节点列表（可包含分类/商品）
    private List<CategoryComponent> children = new ArrayList<>();

    public CategoryComposite(String categoryName) {
        this.categoryName = categoryName;
    }

    // 新增子节点
    public void add(CategoryComponent component) {
        children.add(component);
    }

    // 删除子节点
    public void remove(CategoryComponent component) {
        children.remove(component);
    }

    @Override
    public void showInfo(int depth) {
        System.out.println("  ".repeat(depth) + "├─ 分类：" + categoryName);
        // 递归展示子节点
        for (CategoryComponent component : children) {
            component.showInfo(depth + 1);
        }
    }

    @Override
    public double getTotalPrice() {
        double total = 0;
        // 递归计算所有子节点总价
        for (CategoryComponent component : children) {
            total += component.getTotalPrice();
        }
        return total;
    }
}

// 测试类
class CompositeTest {
    public static void main(String[] args) {
        // 1. 构建树形结构
        // 根分类
        CategoryComposite root = new CategoryComposite("全部商品");
        // 一级分类
        CategoryComposite clothing = new CategoryComposite("服装");
        CategoryComposite digital = new CategoryComposite("数码");
        root.add(clothing);
        root.add(digital);

        // 二级分类
        CategoryComposite menClothing = new CategoryComposite("男装");
        CategoryComposite womenClothing = new CategoryComposite("女装");
        clothing.add(menClothing);
        clothing.add(womenClothing);

        // 叶子商品
        menClothing.add(new ProductLeaf("男士T恤", 99.9));
        menClothing.add(new ProductLeaf("男士牛仔裤", 199.9));
        womenClothing.add(new ProductLeaf("女士连衣裙", 299.9));
        digital.add(new ProductLeaf("智能手机", 2999.9));

        // 2. 统一调用：无需区分分类/商品
        System.out.println("=== 商品分类树形结构 ===");
        root.showInfo(0);

        System.out.println("\n=== 全部分类商品总价 ===" + root.getTotalPrice());
        System.out.println("=== 服装分类商品总价 ===" + clothing.getTotalPrice());
    }
}
```

------

### 15. 享元模式（Flyweight）

**模式定义**：运用共享技术有效地支持大量细粒度的对象，通过拆分「内部状态（固定不变）」和「外部状态（随场景变化）」，减少重复对象创建，降低内存占用。

**核心角色**：抽象享元、具体享元、享元工厂、客户端

**电商场景**：商品规格值共享。电商平台有百万级商品，大量商品共用「红色、XL、500g」等同规格值，用享元模式将固定的规格值作为内部状态共享，避免重复创建对象。





```
import java.util.HashMap;
import java.util.Map;

// 1. 抽象享元：规格值接口
interface SpecValueFlyweight {
    // 展示规格信息，外部状态通过参数传入
    void showSpec(String productId, String specName);
    // 获取内部状态（固定的规格值）
    String getSpecValue();
}

// 2. 具体享元：规格值享元对象（内部状态固定不变）
class SpecValueConcreteFlyweight implements SpecValueFlyweight {
    // 内部状态：固定的规格值，共享的核心
    private final String specValue;

    // 构造时初始化内部状态，后续不可修改
    public SpecValueConcreteFlyweight(String specValue) {
        this.specValue = specValue;
    }

    @Override
    public void showSpec(String productId, String specName) {
        System.out.println("商品ID[" + productId + "]，规格名[" + specName + "]，规格值[" + specValue + "]");
    }

    @Override
    public String getSpecValue() {
        return specValue;
    }
}

// 3. 享元工厂：管理享元池，保证享元对象全局唯一
class SpecValueFlyweightFactory {
    // 享元池：缓存所有规格值享元对象
    private static final Map<String, SpecValueFlyweight> FLYWEIGHT_POOL = new HashMap<>();

    // 私有构造，禁止实例化
    private SpecValueFlyweightFactory() {}

    // 获取享元对象，不存在则创建并放入池
    public static SpecValueFlyweight getFlyweight(String specValue) {
        if (!FLYWEIGHT_POOL.containsKey(specValue)) {
            FLYWEIGHT_POOL.put(specValue, new SpecValueConcreteFlyweight(specValue));
            System.out.println("新建规格值享元对象：" + specValue);
        }
        return FLYWEIGHT_POOL.get(specValue);
    }

    // 获取池内对象数量
    public static int getPoolSize() {
        return FLYWEIGHT_POOL.size();
    }
}

// 测试类
class FlyweightTest {
    public static void main(String[] args) {
        // 模拟1000个商品共用"红色"规格值
        String[] productIds = new String[1000];
        for (int i = 0; i < 1000; i++) {
            productIds[i] = "P" + (i + 1);
        }

        // 所有商品共用同一个"红色"享元对象
        for (String productId : productIds) {
            SpecValueFlyweight flyweight = SpecValueFlyweightFactory.getFlyweight("红色");
            flyweight.showSpec(productId, "颜色");
        }

        // 新增其他规格值
        SpecValueFlyweight xl = SpecValueFlyweightFactory.getFlyweight("XL");
        xl.showSpec("P001", "尺码");

        SpecValueFlyweight l = SpecValueFlyweightFactory.getFlyweight("L");
        l.showSpec("P002", "尺码");

        // 查看享元池大小：仅3个对象，却支撑了1000+商品的规格展示
        System.out.println("\n享元池内对象总数：" + SpecValueFlyweightFactory.getPoolSize());
    }
}
```

------

### 16. 代理模式（Proxy）

**模式定义**：为其他对象提供一种代理以控制对这个对象的访问，在不修改原有对象的前提下，实现访问控制、功能增强、远程调用封装等能力。

**核心角色**：抽象主题、真实主题、代理类

**电商场景**：商品详情缓存代理。热门商品详情查询高频访问数据库，用代理类封装缓存逻辑，先查 Redis 缓存，命中直接返回，未命中再调用真实服务查询数据库并回写缓存。








```
// 1. 抽象主题：商品详情服务接口
interface ProductDetailService {
    String getProductDetail(String productId);
}

// 2. 真实主题：商品详情真实服务（查询数据库）
class ProductDetailServiceImpl implements ProductDetailService {
    @Override
    public String getProductDetail(String productId) {
        // 模拟数据库查询（耗时操作）
        System.out.println("【真实服务】查询数据库，商品ID：" + productId);
        return "商品详情：" + productId + "，iPhone 15 Pro 256G 黑色";
    }
}

// 3. 代理类：商品详情缓存代理
class ProductDetailCacheProxy implements ProductDetailService {
    // 持有真实主题对象
    private ProductDetailService realService;
    // 模拟Redis缓存
    private final Map<String, String> cache = new HashMap<>();

    public ProductDetailCacheProxy(ProductDetailService realService) {
        this.realService = realService;
    }

    @Override
    public String getProductDetail(String productId) {
        // 1. 先查缓存
        if (cache.containsKey(productId)) {
            System.out.println("【代理服务】命中缓存，商品ID：" + productId);
            return cache.get(productId);
        }

        // 2. 缓存未命中，调用真实服务查询数据库
        String detail = realService.getProductDetail(productId);

        // 3. 回写缓存
        cache.put(productId, detail);
        System.out.println("【代理服务】缓存已写入，商品ID：" + productId);

        return detail;
    }
}

// 测试类
class ProxyTest {
    public static void main(String[] args) {
        // 创建真实服务和代理
        ProductDetailService realService = new ProductDetailServiceImpl();
        ProductDetailService proxy = new ProductDetailCacheProxy(realService);

        // 第一次查询：缓存未命中，查数据库
        System.out.println("===== 第一次查询 =====");
        System.out.println(proxy.getProductDetail("P001"));

        // 第二次查询：命中缓存，不查数据库
        System.out.println("\n===== 第二次查询 =====");
        System.out.println(proxy.getProductDetail("P001"));

        // 第三次查询：命中缓存
        System.out.println("\n===== 第三次查询 =====");
        System.out.println(proxy.getProductDetail("P001"));
    }
}
```

------

## 二、行为型模式（剩余 7 种）：聚焦对象间通信与职责分配，解耦行为与实现

### 17. 责任链模式（Chain of Responsibility）

**模式定义**：使多个对象都有机会处理请求，从而避免请求的发送者和接收者之间的耦合关系。将这些对象连成一条链，并沿着这条链传递该请求，直到有一个对象处理它为止。

**核心角色**：抽象处理者、具体处理者、客户端

**电商场景**：订单创建全链路校验。用户提交订单时，需要经过「用户状态校验→库存充足校验→优惠规则校验→风控反刷单校验」的串行链路，每个校验节点独立封装，可灵活插拔、调整顺序。








```
// 1. 抽象处理者：订单校验处理器
abstract class OrderValidateHandler {
    // 下一个处理节点
    protected OrderValidateHandler nextHandler;

    // 设置下一个节点，构建链式结构
    public void setNextHandler(OrderValidateHandler nextHandler) {
        this.nextHandler = nextHandler;
    }

    // 抽象校验方法
    public abstract boolean validate(String orderId, String userId);

    // 传递给下一个节点
    protected boolean passToNext(String orderId, String userId) {
        if (nextHandler == null) {
            // 无下一个节点，校验全部通过
            return true;
        }
        return nextHandler.validate(orderId, userId);
    }
}

// 2. 具体处理者：用户状态校验
class UserStatusValidateHandler extends OrderValidateHandler {
    @Override
    public boolean validate(String orderId, String userId) {
        System.out.println("【1】用户状态校验：" + userId);
        // 模拟校验：用户状态正常
        boolean isNormal = !"黑名单用户".equals(userId);
        if (!isNormal) {
            System.out.println("用户状态校验失败：用户已被拉黑");
            return false;
        }
        // 校验通过，传递给下一个节点
        return passToNext(orderId, userId);
    }
}

// 2. 具体处理者：库存校验
class StockValidateHandler extends OrderValidateHandler {
    @Override
    public boolean validate(String orderId, String userId) {
        System.out.println("【2】库存充足校验：" + orderId);
        // 模拟校验：库存充足
        boolean hasStock = true;
        if (!hasStock) {
            System.out.println("库存校验失败：商品库存不足");
            return false;
        }
        return passToNext(orderId, userId);
    }
}

// 2. 具体处理者：优惠规则校验
class PromotionValidateHandler extends OrderValidateHandler {
    @Override
    public boolean validate(String orderId, String userId) {
        System.out.println("【3】优惠规则校验：" + orderId);
        // 模拟校验：优惠规则有效
        boolean isPromotionValid = true;
        if (!isPromotionValid) {
            System.out.println("优惠规则校验失败：优惠已过期");
            return false;
        }
        return passToNext(orderId, userId);
    }
}

// 2. 具体处理者：风控校验
class RiskControlValidateHandler extends OrderValidateHandler {
    @Override
    public boolean validate(String orderId, String userId) {
        System.out.println("【4】风控反刷单校验：" + userId);
        // 模拟校验：无刷单风险
        boolean isSafe = true;
        if (!isSafe) {
            System.out.println("风控校验失败：账号存在刷单风险");
            return false;
        }
        return passToNext(orderId, userId);
    }
}

// 测试类
class ChainOfResponsibilityTest {
    public static void main(String[] args) {
        // 1. 构建责任链
        OrderValidateHandler userHandler = new UserStatusValidateHandler();
        OrderValidateHandler stockHandler = new StockValidateHandler();
        OrderValidateHandler promotionHandler = new PromotionValidateHandler();
        OrderValidateHandler riskHandler = new RiskControlValidateHandler();

        // 链式组装：用户校验→库存校验→优惠校验→风控校验
        userHandler.setNextHandler(stockHandler);
        stockHandler.setNextHandler(promotionHandler);
        promotionHandler.setNextHandler(riskHandler);

        // 2. 发起校验请求
        System.out.println("===== 正常用户订单校验 =====");
        boolean result = userHandler.validate("ORD001", "U001");
        System.out.println("订单校验最终结果：" + (result ? "通过" : "失败"));

        // 3. 黑名单用户校验（中途终止）
        System.out.println("\n===== 黑名单用户订单校验 =====");
        boolean blackResult = userHandler.validate("ORD002", "黑名单用户");
        System.out.println("订单校验最终结果：" + (blackResult ? "通过" : "失败"));
    }
}
```

------

### 18. 命令模式（Command）

**模式定义**：将一个请求封装为一个对象，从而使你可用不同的请求对客户进行参数化；对请求排队或记录请求日志，以及支持可撤销的操作。

**核心角色**：命令接口、具体命令、调用者、接收者

**电商场景**：订单全生命周期操作封装。将订单的创建、支付、取消操作封装为独立命令对象，支持操作日志记录、异步排队、撤销重做，满足电商订单操作可追溯、可回滚的核心诉求。







```
// 1. 命令接口
interface OrderCommand {
    // 执行命令
    void execute();
    // 撤销命令
    void undo();
}

// 2. 接收者：订单服务（真正执行业务逻辑的类）
class OrderService {
    private String orderId;

    public OrderService(String orderId) {
        this.orderId = orderId;
    }

    // 创建订单
    public void createOrder() {
        System.out.println("订单服务：创建订单[" + orderId + "]，锁定库存");
    }

    // 取消订单
    public void cancelOrder() {
        System.out.println("订单服务：取消订单[" + orderId + "]，解锁库存、恢复优惠券");
    }

    // 恢复订单（撤销取消操作）
    public void restoreOrder() {
        System.out.println("订单服务：恢复订单[" + orderId + "]，重新锁定库存");
    }
}

// 3. 具体命令：创建订单命令
class CreateOrderCommand implements OrderCommand {
    private OrderService orderService;

    public CreateOrderCommand(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public void execute() {
        orderService.createOrder();
    }

    @Override
    public void undo() {
        orderService.cancelOrder();
    }
}

// 3. 具体命令：取消订单命令
class CancelOrderCommand implements OrderCommand {
    private OrderService orderService;

    public CancelOrderCommand(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public void execute() {
        orderService.cancelOrder();
    }

    @Override
    public void undo() {
        orderService.restoreOrder();
    }
}

// 4. 调用者：订单操作控制器（发起命令，不关心具体实现）
class OrderInvoker {
    // 记录执行过的命令，支持撤销
    private final java.util.Stack<OrderCommand> commandHistory = new java.util.Stack<>();

    // 执行命令
    public void executeCommand(OrderCommand command) {
        command.execute();
        commandHistory.push(command);
    }

    // 撤销上一个命令
    public void undoLastCommand() {
        if (!commandHistory.isEmpty()) {
            OrderCommand command = commandHistory.pop();
            command.undo();
            System.out.println("已撤销上一个操作");
        } else {
            System.out.println("无操作可撤销");
        }
    }
}

// 测试类
class CommandTest {
    public static void main(String[] args) {
        // 接收者
        OrderService orderService = new OrderService("ORD001");
        // 调用者
        OrderInvoker invoker = new OrderInvoker();

        // 执行创建订单命令
        System.out.println("===== 执行创建订单 =====");
        invoker.executeCommand(new CreateOrderCommand(orderService));

        // 执行取消订单命令
        System.out.println("\n===== 执行取消订单 =====");
        invoker.executeCommand(new CancelOrderCommand(orderService));

        // 撤销取消订单
        System.out.println("\n===== 撤销取消订单 =====");
        invoker.undoLastCommand();

        // 撤销创建订单
        System.out.println("\n===== 撤销创建订单 =====");
        invoker.undoLastCommand();
    }
}
```

------

### 19. 备忘录模式（Memento）

**模式定义**：在不破坏封装性的前提下，捕获一个对象的内部状态，并在该对象之外保存这个状态，以便以后可以将该对象恢复到原先保存的状态。

**核心角色**：原发器、备忘录、备忘录管理者

**电商场景**：购物车状态快照与恢复。用户在结算页修改商品数量、选择优惠券后，若想返回购物车恢复之前的状态，用备忘录模式保存购物车快照，不暴露购物车内部结构，支持状态回滚。




```
import java.util.ArrayList;
import java.util.List;

// 1. 备忘录：购物车状态快照（存储原发器的内部状态，对外不可见）
class CartMemento {
    // 购物车内部状态
    private final List<String> items;
    private final String selectedCoupon;
    private final String address;

    // 构造时保存状态，仅原发器可访问
    public CartMemento(List<String> items, String selectedCoupon, String address) {
        this.items = new ArrayList<>(items);
        this.selectedCoupon = selectedCoupon;
        this.address = address;
    }

    // 提供get方法，仅原发器可获取状态
    public List<String> getItems() {
        return items;
    }

    public String getSelectedCoupon() {
        return selectedCoupon;
    }

    public String getAddress() {
        return address;
    }
}

// 2. 原发器：购物车（需要保存状态的对象）
class ShoppingCart {
    // 购物车内部状态
    private List<String> items = new ArrayList<>();
    private String selectedCoupon;
    private String address;

    // 购物车操作
    public void addItem(String item) {
        items.add(item);
    }

    public void setSelectedCoupon(String selectedCoupon) {
        this.selectedCoupon = selectedCoupon;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    // 创建备忘录：保存当前状态
    public CartMemento createMemento() {
        return new CartMemento(items, selectedCoupon, address);
    }

    // 从备忘录恢复状态
    public void restoreFromMemento(CartMemento memento) {
        this.items = new ArrayList<>(memento.getItems());
        this.selectedCoupon = memento.getSelectedCoupon();
        this.address = memento.getAddress();
    }

    // 展示当前状态
    public void showCart() {
        System.out.println("购物车商品：" + items);
        System.out.println("选中优惠券：" + selectedCoupon);
        System.out.println("收货地址：" + address);
    }
}

// 3. 备忘录管理者：管理快照，不访问备忘录内部状态
class CartCaretaker {
    // 存储备忘录快照
    private final java.util.Stack<CartMemento> mementoStack = new java.util.Stack<>();

    // 保存快照
    public void saveMemento(CartMemento memento) {
        mementoStack.push(memento);
    }

    // 获取上一个快照
    public CartMemento getLastMemento() {
        if (!mementoStack.isEmpty()) {
            return mementoStack.pop();
        }
        return null;
    }
}

// 测试类
class MementoTest {
    public static void main(String[] args) {
        // 原发器：购物车
        ShoppingCart cart = new ShoppingCart();
        // 管理者：快照管理
        CartCaretaker caretaker = new CartCaretaker();

        // 1. 初始化购物车，保存快照
        System.out.println("===== 初始购物车状态 =====");
        cart.addItem("商品A");
        cart.addItem("商品B");
        cart.setSelectedCoupon("满200减20");
        cart.setAddress("北京市朝阳区");
        cart.showCart();
        caretaker.saveMemento(cart.createMemento());

        // 2. 修改购物车状态
        System.out.println("\n===== 修改后购物车状态 =====");
        cart.addItem("商品C");
        cart.setSelectedCoupon("满300减50");
        cart.setAddress("上海市浦东新区");
        cart.showCart();

        // 3. 恢复到之前的快照
        System.out.println("\n===== 恢复快照后状态 =====");
        cart.restoreFromMemento(caretaker.getLastMemento());
        cart.showCart();
    }
}
```

------

### 20. 迭代器模式（Iterator）

**模式定义**：提供一种方法顺序访问一个聚合对象中的各个元素，而又不暴露该对象的内部表示。

**核心角色**：迭代器接口、具体迭代器、聚合接口、具体聚合

**电商场景**：多类型商品列表统一遍历。电商有分页商品列表、秒杀商品列表、收藏商品列表等，内部存储结构不同（数组、List、分页结果集），用迭代器模式提供统一的遍历方式，客户端无需关心列表内部结构。




```
import java.util.ArrayList;
import java.util.List;

// 1. 迭代器接口：统一遍历规范
interface ProductIterator {
    // 是否有下一个元素
    boolean hasNext();
    // 获取下一个元素
    String next();
}

// 2. 聚合接口：商品列表聚合
interface ProductList {
    // 获取迭代器
    ProductIterator createIterator();
    // 添加商品
    void addProduct(String product);
}

// 3. 具体聚合：秒杀商品列表（内部用数组存储）
class SeckillProductList implements ProductList {
    // 内部存储结构：数组
    private String[] products;
    private int size = 0;
    private static final int DEFAULT_CAPACITY = 10;

    public SeckillProductList() {
        products = new String[DEFAULT_CAPACITY];
    }

    @Override
    public void addProduct(String product) {
        if (size >= products.length) {
            // 数组扩容
            String[] newProducts = new String[products.length * 2];
            System.arraycopy(products, 0, newProducts, 0, products.length);
            products = newProducts;
        }
        products[size++] = product;
    }

    @Override
    public ProductIterator createIterator() {
        return new SeckillProductIterator();
    }

    // 4. 具体迭代器：秒杀商品列表迭代器（私有内部类，访问内部结构）
    private class SeckillProductIterator implements ProductIterator {
        private int currentIndex = 0;

        @Override
        public boolean hasNext() {
            return currentIndex < size;
        }

        @Override
        public String next() {
            if (hasNext()) {
                return products[currentIndex++];
            }
            return null;
        }
    }
}

// 3. 具体聚合：收藏商品列表（内部用List存储）
class FavoriteProductList implements ProductList {
    // 内部存储结构：List
    private List<String> products = new ArrayList<>();

    @Override
    public void addProduct(String product) {
        products.add(product);
    }

    @Override
    public ProductIterator createIterator() {
        return new FavoriteProductIterator();
    }

    // 4. 具体迭代器：收藏商品列表迭代器
    private class FavoriteProductIterator implements ProductIterator {
        private int currentIndex = 0;

        @Override
        public boolean hasNext() {
            return currentIndex < products.size();
        }

        @Override
        public String next() {
            if (hasNext()) {
                return products.get(currentIndex++);
            }
            return null;
        }
    }
}

// 测试类
class IteratorTest {
    public static void main(String[] args) {
        // 秒杀商品列表
        ProductList seckillList = new SeckillProductList();
        seckillList.addProduct("秒杀商品A");
        seckillList.addProduct("秒杀商品B");
        seckillList.addProduct("秒杀商品C");

        // 收藏商品列表
        ProductList favoriteList = new FavoriteProductList();
        favoriteList.addProduct("收藏商品1");
        favoriteList.addProduct("收藏商品2");

        // 统一遍历方式，无需关心内部存储结构
        System.out.println("===== 遍历秒杀商品列表 =====");
        ProductIterator seckillIterator = seckillList.createIterator();
        while (seckillIterator.hasNext()) {
            System.out.println(seckillIterator.next());
        }

        System.out.println("\n===== 遍历收藏商品列表 =====");
        ProductIterator favoriteIterator = favoriteList.createIterator();
        while (favoriteIterator.hasNext()) {
            System.out.println(favoriteIterator.next());
        }
    }
}
```

------

### 21. 中介者模式（Mediator）

**模式定义**：用一个中介对象来封装一系列的对象交互，中介者使各对象不需要显式地相互引用，从而使其耦合松散，而且可以独立地改变它们之间的交互。

**核心角色**：抽象中介者、具体中介者、抽象同事类、具体同事类

**电商场景**：订单状态变更全系统协调。订单状态变更会触发库存、物流、财务、消息推送等多个系统联动，用中介者模式将网状耦合转为星型耦合，所有系统仅与中介者交互，无需感知其他系统的存在。






```
// 1. 抽象中介者：订单中介者接口
interface OrderMediator {
    // 订单状态变更通知
    void orderStatusChanged(String orderId, String status, Colleague colleague);
    // 注册同事类
    void registerColleague(Colleague colleague);
}

// 2. 抽象同事类：系统模块
abstract class Colleague {
    protected String name;
    protected OrderMediator mediator;

    public Colleague(String name, OrderMediator mediator) {
        this.name = name;
        this.mediator = mediator;
        // 注册到中介者
        mediator.registerColleague(this);
    }

    // 接收通知
    public abstract void receive(String orderId, String status);

    public String getName() {
        return name;
    }
}

// 3. 具体中介者：订单状态协调中介者
class OrderStatusMediator implements OrderMediator {
    // 所有注册的同事类
    private final List<Colleague> colleagues = new ArrayList<>();

    @Override
    public void registerColleague(Colleague colleague) {
        colleagues.add(colleague);
    }

    @Override
    public void orderStatusChanged(String orderId, String status, Colleague trigger) {
        System.out.println("\n【中介者】订单[" + orderId + "]状态变更为：" + status + "，由[" + trigger.getName() + "]触发");
        // 通知所有其他同事类
        for (Colleague colleague : colleagues) {
            // 不通知触发者自己
            if (!colleague.equals(trigger)) {
                colleague.receive(orderId, status);
            }
        }
    }
}

// 4. 具体同事类：库存系统
class StockColleague extends Colleague {
    public StockColleague(OrderMediator mediator) {
        super("库存系统", mediator);
    }

    @Override
    public void receive(String orderId, String status) {
        if ("已付款".equals(status)) {
            System.out.println("【库存系统】收到通知：扣减订单[" + orderId + "]商品库存");
        } else if ("已取消".equals(status)) {
            System.out.println("【库存系统】收到通知：解锁订单[" + orderId + "]商品库存");
        }
    }
}

// 4. 具体同事类：物流系统
class LogisticsColleague extends Colleague {
    public LogisticsColleague(OrderMediator mediator) {
        super("物流系统", mediator);
    }

    @Override
    public void receive(String orderId, String status) {
        if ("已付款".equals(status)) {
            System.out.println("【物流系统】收到通知：生成订单[" + orderId + "]配送单");
        }
    }
}

// 4. 具体同事类：财务系统
class FinanceColleague extends Colleague {
    public FinanceColleague(OrderMediator mediator) {
        super("财务系统", mediator);
    }

    @Override
    public void receive(String orderId, String status) {
        if ("已付款".equals(status)) {
            System.out.println("【财务系统】收到通知：记录订单[" + orderId + "]收款凭证");
        }
    }
}

// 4. 具体同事类：消息推送系统
class MessageColleague extends Colleague {
    public MessageColleague(OrderMediator mediator) {
        super("消息推送系统", mediator);
    }

    @Override
    public void receive(String orderId, String status) {
        System.out.println("【消息推送系统】收到通知：给用户推送订单[" + orderId + "]状态变更消息");
    }
}

// 测试类
class MediatorTest {
    public static void main(String[] args) {
        // 创建中介者
        OrderMediator mediator = new OrderStatusMediator();

        // 注册所有同事类（系统模块）
        Colleague stock = new StockColleague(mediator);
        Colleague logistics = new LogisticsColleague(mediator);
        Colleague finance = new FinanceColleague(mediator);
        Colleague message = new MessageColleague(mediator);

        // 订单状态变更，仅需通知中介者，由中介者协调所有系统
        System.out.println("===== 订单已付款状态变更 =====");
        mediator.orderStatusChanged("ORD001", "已付款", stock);

        System.out.println("\n===== 订单已取消状态变更 =====");
        mediator.orderStatusChanged("ORD001", "已取消", message);
    }
}
```

------

### 22. 解释器模式（Interpreter）

**模式定义**：给定一个语言，定义它的文法的一种表示，并定义一个解释器，这个解释器使用该表示来解释语言中的句子。

**核心角色**：抽象表达式、终结符表达式、非终结符表达式、上下文

**电商场景**：商家自定义促销规则解析。商家可自定义促销规则，如「订单金额满 300 元 且 包含美妆品类 且 用户是 VIP 会员」，用解释器模式解析自定义规则表达式，判断订单是否符合促销条件。










```
import java.util.HashMap;
import java.util.Map;

// 1. 上下文：存储规则执行的上下文数据（订单信息）
class PromotionContext {
    // 订单数据
    private double orderAmount;
    private String category;
    private boolean isVip;

    public PromotionContext(double orderAmount, String category, boolean isVip) {
        this.orderAmount = orderAmount;
        this.category = category;
        this.isVip = isVip;
    }

    public double getOrderAmount() {
        return orderAmount;
    }

    public String getCategory() {
        return category;
    }

    public boolean isVip() {
        return isVip;
    }
}

// 2. 抽象表达式：规则表达式接口
interface Expression {
    // 解释表达式，返回是否符合规则
    boolean interpret(PromotionContext context);
}

// 3. 终结符表达式：订单金额大于指定值
class AmountGreaterThanExpression implements Expression {
    private double threshold;

    public AmountGreaterThanExpression(double threshold) {
        this.threshold = threshold;
    }

    @Override
    public boolean interpret(PromotionContext context) {
        return context.getOrderAmount() > threshold;
    }
}

// 3. 终结符表达式：包含指定品类
class CategoryContainsExpression implements Expression {
    private String targetCategory;

    public CategoryContainsExpression(String targetCategory) {
        this.targetCategory = targetCategory;
    }

    @Override
    public boolean interpret(PromotionContext context) {
        return targetCategory.equals(context.getCategory());
    }
}

// 3. 终结符表达式：用户是VIP
class VipUserExpression implements Expression {
    @Override
    public boolean interpret(PromotionContext context) {
        return context.isVip();
    }
}

// 4. 非终结符表达式：AND逻辑（与）
class AndExpression implements Expression {
    private Expression left;
    private Expression right;

    public AndExpression(Expression left, Expression right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public boolean interpret(PromotionContext context) {
        return left.interpret(context) && right.interpret(context);
    }
}

// 4. 非终结符表达式：OR逻辑（或）
class OrExpression implements Expression {
    private Expression left;
    private Expression right;

    public OrExpression(Expression left, Expression right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public boolean interpret(PromotionContext context) {
        return left.interpret(context) || right.interpret(context);
    }
}

// 测试类
class InterpreterTest {
    public static void main(String[] args) {
        // 构建规则：订单金额>300 且 包含美妆品类 且 用户是VIP
        Expression promotionRule = new AndExpression(
                new AndExpression(
                        new AmountGreaterThanExpression(300),
                        new CategoryContainsExpression("美妆")
                ),
                new VipUserExpression()
        );

        // 测试订单1：符合规则
        System.out.println("===== 测试订单1 =====");
        PromotionContext order1 = new PromotionContext(399.9, "美妆", true);
        System.out.println("是否符合促销规则：" + promotionRule.interpret(order1));

        // 测试订单2：金额不足，不符合
        System.out.println("\n===== 测试订单2 =====");
        PromotionContext order2 = new PromotionContext(299.9, "美妆", true);
        System.out.println("是否符合促销规则：" + promotionRule.interpret(order2));

        // 测试订单3：非VIP，不符合
        System.out.println("\n===== 测试订单3 =====");
        PromotionContext order3 = new PromotionContext(399.9, "美妆", false);
        System.out.println("是否符合促销规则：" + promotionRule.interpret(order3));
    }
}
```

------

### 23. 访问者模式（Visitor）

**模式定义**：表示一个作用于某对象结构中的各元素的操作，它使你可以在不改变各元素的类的前提下定义作用于这些元素的新操作。

**核心角色**：抽象访问者、具体访问者、抽象元素、具体元素、对象结构

**电商场景**：订单数据多维度统计分析。订单对象结构固定（商品明细、支付信息、优惠信息），但需要频繁新增统计操作（财务对账、运营统计、发票开具），用访问者模式将数据结构与数据操作解耦，新增操作无需修改订单元素代码。







```
import java.util.ArrayList;
import java.util.List;

// 1. 抽象访问者：订单访问者接口，为每个元素定义访问方法
interface OrderVisitor {
    // 访问商品明细
    void visit(ProductItem item);
    // 访问支付信息
    void visit(PayInfo payInfo);
    // 访问优惠信息
    void visit(PromotionInfo promotionInfo);
}

// 2. 抽象元素：订单元素接口
interface OrderElement {
    // 接收访问者
    void accept(OrderVisitor visitor);
}

// 3. 具体元素：商品明细
class ProductItem implements OrderElement {
    private String productName;
    private double price;
    private int quantity;

    public ProductItem(String productName, double price, int quantity) {
        this.productName = productName;
        this.price = price;
        this.quantity = quantity;
    }

    @Override
    public void accept(OrderVisitor visitor) {
        visitor.visit(this);
    }

    // 提供get方法，访问者获取数据
    public String getProductName() {
        return productName;
    }

    public double getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getSubtotal() {
        return price * quantity;
    }
}

// 3. 具体元素：支付信息
class PayInfo implements OrderElement {
    private String payType;
    private double payAmount;
    private String payTime;

    public PayInfo(String payType, double payAmount, String payTime) {
        this.payType = payType;
        this.payAmount = payAmount;
        this.payTime = payTime;
    }

    @Override
    public void accept(OrderVisitor visitor) {
        visitor.visit(this);
    }

    public String getPayType() {
        return payType;
    }

    public double getPayAmount() {
        return payAmount;
    }

    public String getPayTime() {
        return payTime;
    }
}

// 3. 具体元素：优惠信息
class PromotionInfo implements OrderElement {
    private String promotionType;
    private double discountAmount;

    public PromotionInfo(String promotionType, double discountAmount) {
        this.promotionType = promotionType;
        this.discountAmount = discountAmount;
    }

    @Override
    public void accept(OrderVisitor visitor) {
        visitor.visit(this);
    }

    public String getPromotionType() {
        return promotionType;
    }

    public double getDiscountAmount() {
        return discountAmount;
    }
}

// 4. 具体访问者：金额统计访问者
class AmountStatVisitor implements OrderVisitor {
    private double totalProductAmount = 0;
    private double totalDiscount = 0;
    private double finalPayAmount = 0;

    @Override
    public void visit(ProductItem item) {
        totalProductAmount += item.getSubtotal();
    }

    @Override
    public void visit(PayInfo payInfo) {
        finalPayAmount = payInfo.getPayAmount();
    }

    @Override
    public void visit(PromotionInfo promotionInfo) {
        totalDiscount += promotionInfo.getDiscountAmount();
    }

    // 输出统计结果
    public void showStat() {
        System.out.println("===== 订单金额统计 =====");
        System.out.println("商品总价：" + totalProductAmount);
        System.out.println("优惠总金额：" + totalDiscount);
        System.out.println("实付金额：" + finalPayAmount);
    }
}

// 4. 具体访问者：发票开具访问者
class InvoiceVisitor implements OrderVisitor {
    private StringBuilder invoiceContent = new StringBuilder();

    @Override
    public void visit(ProductItem item) {
        invoiceContent.append(item.getProductName())
                .append(" × ").append(item.getQuantity())
                .append(" 单价：").append(item.getPrice())
                .append(" 小计：").append(item.getSubtotal())
                .append("\n");
    }

    @Override
    public void visit(PayInfo payInfo) {
        // 发票不处理支付信息
    }

    @Override
    public void visit(PromotionInfo promotionInfo) {
        invoiceContent.append("优惠减免：").append(promotionInfo.getDiscountAmount()).append("\n");
    }

    public void showInvoice() {
        System.out.println("\n===== 发票内容 =====");
        System.out.println(invoiceContent);
    }
}

// 5. 对象结构：订单对象，包含所有元素
class OrderObjectStructure {
    private List<OrderElement> elements = new ArrayList<>();

    // 添加元素
    public void addElement(OrderElement element) {
        elements.add(element);
    }

    // 接收访问者，遍历所有元素
    public void accept(OrderVisitor visitor) {
        for (OrderElement element : elements) {
            element.accept(visitor);
        }
    }
}

// 测试类
class VisitorTest {
    public static void main(String[] args) {
        // 构建订单对象结构
        OrderObjectStructure order = new OrderObjectStructure();
        order.addElement(new ProductItem("iPhone 15", 5999, 1));
        order.addElement(new ProductItem("手机壳", 99, 2));
        order.addElement(new PromotionInfo("满减优惠", 200));
        order.addElement(new PayInfo("支付宝", 5997, "2024-01-01 10:00:00"));

        // 1. 金额统计访问者
        AmountStatVisitor statVisitor = new AmountStatVisitor();
        order.accept(statVisitor);
        statVisitor.showStat();

        // 2. 发票开具访问者
        InvoiceVisitor invoiceVisitor = new InvoiceVisitor();
        order.accept(invoiceVisitor);
        invoiceVisitor.showInvoice();

        // 新增统计操作，仅需新增访问者，无需修改订单元素代码
    }
}
```