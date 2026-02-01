### 一、核心坑点及解决方案

#### 1. Stream 只能遍历一次（消费一次）

这是 Stream 最基础也最容易踩的坑。Stream 是一次性的，一旦调用了终止操作（如`forEach`、`collect`、`count`等），这个 Stream 就会被关闭，无法再次使用。

**错误示例**：


```
import java.util.Arrays;
import java.util.stream.Stream;

public class StreamPitfall {
    public static void main(String[] args) {
        Stream<String> stream = Arrays.asList("a", "b", "c").stream();
        
        // 第一次消费Stream（终止操作）
        stream.forEach(System.out::println);
        
        // 第二次消费，抛出异常
        try {
            long count = stream.count();
        } catch (IllegalStateException e) {
            System.out.println("异常：" + e.getMessage()); // 输出：stream has already been operated upon or closed
        }
    }
}
```

**解决方案**：

每次需要遍历的时候重新生成 Stream，而不是复用同一个 Stream 对象：

```
List<String> list = Arrays.asList("a", "b", "c");
// 第一次遍历
list.stream().forEach(System.out::println);
// 第二次遍历（重新生成Stream）
long count = list.stream().count();
System.out.println("数量：" + count); // 输出：3
```

#### 2. 并行流（Parallel Stream）的线程安全问题

并行流会使用 Fork/Join 池多线程处理数据，如果操作的集合不是线程安全的，或者共享变量没有正确同步，会导致数据错乱、并发修改异常等问题。

**错误示例**（非线程安全集合）：


```
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class StreamPitfall {
    public static void main(String[] args) {
        List<Integer> list = new ArrayList<>();
        // 并行流往非线程安全的ArrayList添加元素，大概率出现数据丢失/数组越界异常
        IntStream.range(0, 1000).parallel().forEach(list::add);
        
        System.out.println("实际大小：" + list.size()); // 结果大概率小于1000
    }
}
```

**解决方案**：

- 使用线程安全的集合（如`CopyOnWriteArrayList`）；
- 优先使用`collect`而不是`forEach`修改外部集合（推荐）；
- 对共享变量加锁（不推荐，失去并行流优势）。

**正确示例**：



```
// 方案1：使用collect收集结果（推荐）
List<Integer> safeList = IntStream.range(0, 1000)
                                  .parallel()
                                  .boxed()
                                  .collect(Collectors.toList());
System.out.println("安全大小：" + safeList.size()); // 输出：1000

// 方案2：使用线程安全集合
List<Integer> threadSafeList = new CopyOnWriteArrayList<>();
IntStream.range(0, 1000).parallel().forEach(threadSafeList::add);
System.out.println("线程安全集合大小：" + threadSafeList.size()); // 输出：1000
```

#### 3. 自动装箱 / 拆箱的性能损耗

基本类型（int、long、double）使用 Stream 时，如果用`Stream<Integer>`而非`IntStream`，会频繁触发自动装箱 / 拆箱，导致性能下降（尤其是数据量大时）。

**低效示例**：

```
// Stream<Integer> 会自动装箱，性能差
long sum = Arrays.asList(1, 2, 3, 4, 5)
                 .stream()
                 .mapToInt(Integer::intValue) // 额外的拆箱操作
                 .sum();
```

**高效示例**：


```
// 直接使用IntStream，避免装箱拆箱
long sum = IntStream.of(1, 2, 3, 4, 5).sum();

// 集合转基本类型Stream
long listSum = Arrays.asList(1, 2, 3, 4, 5)
                     .stream()
                     .mapToInt(i -> i) // 拆箱，但比先装箱再拆箱高效
                     .sum();
```

#### 4. 忽视中间操作的惰性执行

Stream 的中间操作（如`filter`、`map`、`sorted`）是**惰性的**，只有调用终止操作时才会执行。如果误解这一点，可能会写出逻辑错误的代码。

**错误示例**（以为中间操作会立即执行）：



```
List<String> list = Arrays.asList("a", "b", "c");
// 仅调用中间操作，无终止操作，这段代码不会执行任何过滤逻辑
list.stream().filter(s -> {
    System.out.println("过滤：" + s); // 不会输出任何内容
    return s.startsWith("a");
});
```

**正确示例**（添加终止操作触发执行）：


```
list.stream().filter(s -> {
    System.out.println("过滤：" + s); // 输出：过滤：a 过滤：b 过滤：c
    return s.startsWith("a");
}).collect(Collectors.toList()); // 终止操作触发执行
```

#### 5. 无限流未做限制导致死循环

`Stream.generate()`或`Stream.iterate()`会生成无限流，如果不通过`limit()`限制长度，终止操作会一直执行，导致程序卡死。

**错误示例**：


```
// 无限流，无limit限制，会一直执行，最终OOM或卡死
Stream.generate(() -> "test")
      .forEach(System.out::println);
```

**正确示例**：


```
// 限制长度，仅生成10个元素
Stream.generate(() -> "test")
      .limit(10)
      .forEach(System.out::println); // 输出10次test
```

#### 6. 误用 peek 方法

`peek`是中间操作，设计初衷是 “调试”（查看流中元素），而非修改元素或执行业务逻辑。如果依赖`peek`执行关键操作（如更新数据库），但没有终止操作，会导致逻辑不执行；即使有终止操作，某些场景下（如短路操作）`peek`也可能不执行。

**风险示例**：


```
List<User> userList = Arrays.asList(new User("张三", 20), new User("李四", 25));
// 试图通过peek修改用户年龄，但如果后续无终止操作，修改不会生效
userList.stream().peek(u -> u.setAge(u.getAge() + 1));

// 即使有终止操作，短路操作（如findFirst）也只会执行部分peek
User first = userList.stream()
                     .peek(u -> System.out.println("处理：" + u.getName())) // 仅输出“处理：张三”
                     .findFirst()
                     .get();
```

**解决方案**：

- 调试用`peek`，业务逻辑用`map`（有返回值）或`forEach`（终止操作）；
- 不要依赖`peek`执行关键操作。

#### 7. Stream 中修改源数据

在 Stream 操作中修改数据源（如遍历 List 时 add/remove 元素），可能导致`ConcurrentModificationException`，即使是串行流也可能出现。

**错误示例**：


```
List<String> list = new ArrayList<>(Arrays.asList("a", "b", "c"));
// 遍历中修改源数据，抛出ConcurrentModificationException
list.stream().forEach(s -> {
    if (s.equals("b")) {
        list.remove(s);
    }
});
```

**解决方案**：

- 遍历前复制数据源；
- 使用`filter`过滤后生成新集合，而非修改原集合。

**正确示例**：



```
List<String> list = new ArrayList<>(Arrays.asList("a", "b", "c"));
// 生成新集合，不修改原集合
List<String> newList = list.stream()
                           .filter(s -> !s.equals("b"))
                           .collect(Collectors.toList());
System.out.println(newList); // 输出：[a, c]
```

### 二、总结

1. **核心规则**：Stream 只能消费一次，并行流需注意线程安全，避免修改源数据；
2. **性能优化**：优先使用基本类型 Stream（IntStream/LongStream）减少装箱拆箱，避免无限制的无限流；
3. **逻辑避坑**：牢记中间操作惰性执行，`peek`仅用于调试，业务逻辑依赖终止操作触发执行。
