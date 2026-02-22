

Java 8 为函数式接口提供了`@FunctionalInterface`注解（可选但推荐），作用是：

1. 编译器会校验接口是否符合 “只有一个抽象方法” 的规则，不符合则报错；
2. 明确标识该接口是函数式接口，提升代码可读性。

函数式接口的核心价值是**支持 Lambda 表达式**——Lambda 表达式本质上是函数式接口中抽象方法的 “简洁实现”，可以直接赋值给函数式接口的变量，让代码更简洁。

#### 示例：自定义函数式接口





```
// 用@FunctionalInterface注解标识（可选）
@FunctionalInterface
public interface MyFunctionalInterface {
    // 唯一的抽象方法
    int calculate(int a, int b);
    
    // 允许有默认方法（不影响函数式接口的判定）
    default void printResult(int result) {
        System.out.println("计算结果：" + result);
    }
    
    // 允许有静态方法
    static String getDesc() {
        return "这是一个自定义函数式接口";
    }
    
    // 重写Object的方法（不算抽象方法）
    @Override
    boolean equals(Object obj);
}

// 使用Lambda表达式实现该接口
public class Test {
    public static void main(String[] args) {
        // Lambda表达式替代匿名内部类，实现calculate方法
        MyFunctionalInterface add = (a, b) -> a + b;
        int sum = add.calculate(10, 20); // 输出30
        add.printResult(sum); // 输出：计算结果：30
    }
}
```

### 8. 常见的抽象函数式接口有哪些？

Java 8 在`java.util.function`包中提供了大量预定义的函数式接口，覆盖了绝大多数常见场景，你无需重复自定义。以下是最常用的核心接口：


|      接口名称       |       抽象方法        |          参数 / 返回值          |             核心用途             |
| :-----------------: | :-------------------: | :-----------------------------: | :------------------------------: |
|    `Consumer<T>`    |  `void accept(T t)`   |        入参 T，无返回值         |     消费数据（如遍历、打印）     |
|    `Supplier<T>`    |       `T get()`       |         无入参，返回 T          |  生产数据（如创建对象、生成值）  |
|   `Function<T,R>`   |    `R apply(T t)`     |         入参 T，返回 R          | 数据转换（如 String 转 Integer） |
|   `Predicate<T>`    |  `boolean test(T t)`  |      入参 T，返回 boolean       |    条件判断（如过滤集合元素）    |
| `UnaryOperator<T>`  |    `T apply(T t)`     | 入参 T，返回 T（Function 子类） |      一元操作（如数值翻倍）      |
| `BinaryOperator<T>` | `T apply(T t1, T t2)` |       入参两个 T，返回 T        |      二元操作（如两数相加）      |

#### 常用接口示例





```
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class CommonFunctionalInterfaces {
    public static void main(String[] args) {
        // 1. Consumer：消费数据（打印字符串）
        Consumer<String> printConsumer = s -> System.out.println("消费数据：" + s);
        printConsumer.accept("Hello Functional Interface");

        // 2. Supplier：生产数据（生成随机数）
        Supplier<Double> randomSupplier = Math::random;
        System.out.println("生产数据：" + randomSupplier.get());

        // 3. Function：转换数据（字符串转整数）
        Function<String, Integer> strToIntFunction = Integer::parseInt;
        System.out.println("转换结果：" + strToIntFunction.apply("123") + 1); // 124

        // 4. Predicate：条件判断（判断数字是否大于10）
        Predicate<Integer> greaterThan10 = num -> num > 10;
        System.out.println("判断结果：" + greaterThan10.test(15)); // true
    }
}
```

### 总结

1. 函数式接口的核心特征是**仅包含一个抽象方法**，可通过`@FunctionalInterface`注解校验，支持 Lambda 表达式；
2. Java 8 预定义的核心函数式接口包括`Consumer`（消费）、`Supplier`（生产）、`Function`（转换）、`Predicate`（判断）等，覆盖了绝大多数日常开发场景；
3. 这些接口都在`java.util.function`包下，无需自定义即可直接使用，大幅简化 Lambda 表达式的编写。
