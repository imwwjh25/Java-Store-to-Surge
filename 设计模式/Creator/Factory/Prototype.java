package Creator.Factory;

// 1. 原型接口：支持克隆
interface Prototype {
    Prototype clone();
}

// 2. 具体原型：SKU对象
class SkuPrototype implements Prototype {
    // 通用属性（SPU共享）
    private String spuId;
    private String productName;
    private String category;
    // 差异化属性（每个SKU不同）
    private String color;
    private String size;
    private double price;

    // 构造方法：初始化通用属性
    public SkuPrototype(String spuId, String productName, String category) {
        this.spuId = spuId;
        this.productName = productName;
        this.category = category;
    }

    // 差异化属性设置
    public void setDiffAttr(String color, String size, double price) {
        this.color = color;
        this.size = size;
        this.price = price;
    }

    // 浅克隆实现
    @Override
    public Prototype clone() {
        try {
            return (SkuPrototype) super.clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public String toString() {
        return "SKU{" +
                "spuId='" + spuId + '\'' +
                ", productName='" + productName + '\'' +
                ", category='" + category + '\'' +
                ", color='" + color + '\'' +
                ", size='" + size + '\'' +
                ", price=" + price +
                '}';
    }
}

// 测试类
class PrototypeTest {
    public static void main(String[] args) {
        // 1. 创建原型SKU（初始化通用属性）
        SkuPrototype prototypeSku = new SkuPrototype("SPU001", "T恤", "服装");

        // 2. 克隆原型，创建红色M码SKU
        SkuPrototype sku1 = (SkuPrototype) prototypeSku.clone();
        sku1.setDiffAttr("红色", "M", 99.9);

        // 3. 克隆原型，创建蓝色L码SKU
        SkuPrototype sku2 = (SkuPrototype) prototypeSku.clone();
        sku2.setDiffAttr("蓝色", "L", 109.9);

        System.out.println(sku1);
        System.out.println(sku2);
    }
}