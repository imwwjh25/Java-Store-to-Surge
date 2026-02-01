Java 8+ 的 `Stream` 流（`java.util.stream.Stream`）提供了丰富的中间操作（Intermediate Operations）和终端操作（Terminal Operations），覆盖**数据转换、过滤、聚合、收集、匹配、遍历**等核心场景。除了常用的 `map`（转换）、`filter`（过滤），以下按功能分类整理关键 API，附示例和说明：

### 一、中间操作（返回 Stream，可链式调用）



中间操作不会立即执行，仅记录操作逻辑，直到终端操作触发才会一次性处理（惰性求值）。

#### 1. 转换类（除了 map）



- **`flatMap(Function<? super T, ? extends Stream<? extends R>> mapper)`**「扁平化转换」：将每个元素转换为一个 `Stream`，再合并为一个整体 `Stream`（解决 “集合嵌套集合” 场景）。示例：将 List<List> 拆分为 List


  ```
  List<List<String>> nestedList = Arrays.asList(
      Arrays.asList("a", "b"),
      Arrays.asList("c", "d")
  );
  List<String> flatList = nestedList.stream()
      .flatMap(Collection::stream) // 每个子List转为Stream，再合并
      .collect(Collectors.toList()); // 结果：[a, b, c, d]
  ```

  

- **`mapToInt(ToIntFunction<? super T> mapper)` / `mapToLong` / `mapToDouble`**转换为「数值流」（`IntStream`/`LongStream`/`DoubleStream`），避免自动装箱开销，支持数值专用操作（如求和、求平均）。示例：将字符串转为长度，求和



  ```
  List<String> strs = Arrays.asList("apple", "banana", "cherry");
  int totalLength = strs.stream()
      .mapToInt(String::length) // 转为 IntStream（每个元素是字符串长度）
      .sum(); // 结果：5 + 6 + 6 = 17
  ```

  

- **`boxed()`**数值流转回普通 `Stream`（如 `IntStream → Stream<Integer>`），用于后续需要包装类型的操作。示例：



  ```
  IntStream.of(1, 2, 3)
      .boxed() // 转为 Stream<Integer>
      .collect(Collectors.toSet()); // 结果：{1,2,3}
  ```

  

#### 2. 过滤与去重（除了 filter）



- **`distinct()`**去除流中重复元素（依赖元素的 `equals()` 方法判断）。示例：



  ```
  List<Integer> nums = Arrays.asList(1, 2, 2, 3, 3, 3);
  nums.stream()
      .distinct()
      .forEach(System.out::println); // 结果：1,2,3
  ```

  

- **`limit(long maxSize)`**截取流的前 N 个元素（短路操作，拿到 N 个后停止处理）。示例：取前 2 个元素



  ```
  Stream.of("a", "b", "c", "d")
      .limit(2)
      .forEach(System.out::println); // 结果：a,b
  ```

  

- **`skip(long n)`**跳过流的前 N 个元素。示例：跳过前 2 个元素



  ```
  Stream.of("a", "b", "c", "d")
      .skip(2)
      .forEach(System.out::println); // 结果：c,d
  ```

  

#### 3. 排序类



- **`sorted()`**自然排序（依赖元素实现 `Comparable` 接口，如 `String`、`Integer` 自带排序逻辑）。示例：


  ```
  List<String> strs = Arrays.asList("banana", "apple", "cherry");
  strs.stream()
      .sorted() // 按字母自然排序
      .forEach(System.out::println); // 结果：apple, banana, cherry
  ```

  

- **`sorted(Comparator<? super T> comparator)`**自定义排序（传入 `Comparator` 实现排序规则）。示例：按字符串长度降序



  ```
  strs.stream()
      .sorted((s1, s2) -> Integer.compare(s2.length(), s1.length()))
      .forEach(System.out::println); // 结果：banana(6), cherry(6), apple(5)
  ```

  

#### 4. 并行与顺序切换



- `parallel()`

  将流转为并行流（多线程处理，适合大数据量）。

- `sequential()`

  将流转为顺序流（单线程处理，默认模式）。

  示例：并行求和（注意线程安全）



  ```
  List<Integer> nums = IntStream.range(1, 10000).boxed().collect(Collectors.toList());
  long sum = nums.stream()
      .parallel() // 并行处理
      .mapToInt(Integer::intValue)
      .sum(); // 结果：49995000
  ```

  

#### 5. 调试与日志（peek）



- `peek(Consumer<? super T> action)`

  中间操作中 “偷看” 元素（执行打印、调试等副作用操作，但不改变元素），常用于调试流的处理过程。

  示例：查看流的每一步处理结果


  ```
  List<String> result = Stream.of("a", "b", "c")
      .peek(s -> System.out.println("原始元素：" + s))
      .map(s -> s.toUpperCase())
      .peek(s -> System.out.println("转换后：" + s))
      .collect(Collectors.toList());
  // 输出：
  // 原始元素：a → 转换后：A
  // 原始元素：b → 转换后：B
  // 原始元素：c → 转换后：C
  ```

  

### 二、终端操作（返回非 Stream，触发流执行）



终端操作是流的 “终点”，触发所有中间操作的执行，之后流不能再复用。

#### 1. 遍历与消费



- `forEach(Consumer<? super T> action)`

  遍历流中元素，执行消费操作（最常用）。

- `forEachOrdered(Consumer<? super T> action)`

  有序遍历（即使是并行流，也保证按流的原始顺序处理，牺牲部分并行性能）。

  示例：并行流的有序遍历



  ```
  Stream.of("a", "b", "c")
      .parallel()
      .forEachOrdered(System.out::println); // 结果：a,b,c（顺序不变）
  ```

  

#### 2. 聚合计算（统计类）



- **`count()`**统计流中元素个数。示例：



  ```
  long count = Stream.of(1, 2, 3, 4).count(); // 结果：4
  ```

  

- **`max(Comparator<? super T> comparator)` / `min()`**求最大值 / 最小值（返回 `Optional<T>`，避免空指针）。示例：求字符串最长的元素


  ```
  Optional<String> longest = Stream.of("a", "banana", "cherry")
      .max(Comparator.comparingInt(String::length));
  longest.ifPresent(System.out::println); // 结果：banana（6）
  ```

  

- **`sum()` / `average()` / `min()` / `max()` / `count()`（数值流专用）**数值流（`IntStream`/`LongStream`/`DoubleStream`）的专用聚合方法，无需手动转换。示例：



  ```
  Double average = IntStream.of(1, 2, 3, 4)
      .average() // 求平均值，返回 OptionalDouble
      .orElse(0.0); // 结果：2.5
  ```

  

- **`summaryStatistics()`（数值流专用）**获取数值流的统计信息（总和、平均值、最值、个数），一次性拿到所有统计结果。示例：




  ```
  IntSummaryStatistics stats = IntStream.of(1, 2, 3, 4).summaryStatistics();
  System.out.println("总和：" + stats.getSum()); // 10
  System.out.println("平均值：" + stats.getAverage()); // 2.5
  System.out.println("最大值：" + stats.getMax()); // 4
  System.out.println("个数：" + stats.getCount()); // 4
  ```

  

#### 3. 匹配与判断（短路操作）



- **`anyMatch(Predicate<? super T> predicate)`**是否存在至少一个元素满足条件（短路：找到一个就停止）。示例：判断是否有偶数



  ```
  boolean hasEven = Stream.of(1, 3, 4, 5).anyMatch(n -> n % 2 == 0); // 结果：true
  ```

  

- **`allMatch(Predicate<? super T> predicate)`**是否所有元素都满足条件（短路：找到一个不满足就停止）。示例：判断是否全是正数



  ```
  boolean allPositive = Stream.of(1, 2, 3).allMatch(n -> n > 0); // 结果：true
  ```

  

- **`noneMatch(Predicate<? super T> predicate)`**是否所有元素都不满足条件（短路：找到一个满足就停止）。示例：判断是否没有负数



  ```
  boolean noNegative = Stream.of(1, 2, 3).noneMatch(n -> n < 0); // 结果：true
  ```

  

#### 4. 查找（短路操作）



- **`findFirst()`**查找流中第一个元素（返回 `Optional<T>`，顺序流中是第一个，并行流中不确定但尽量返回第一个）。示例：



  ```
  Optional<String> first = Stream.of("a", "b", "c").findFirst();
  first.ifPresent(System.out::println); // 结果：a
  ```

  

- **`findAny()`**查找流中任意一个元素（短路操作，并行流中性能更高，返回任意一个可用元素）。示例：



  ```
  Optional<String> any = Stream.of("a", "b", "c").parallel().findAny();
  any.ifPresent(System.out::println); // 结果：可能是 a/b/c 中的任意一个
  ```

  

#### 5. 收集（核心操作，Collectors 工具类）



`collect(Collector<? super T, A, R> collector)` 是最强大的终端操作，用于将流转换为集合、Map、字符串等结构，依赖 `java.util.stream.Collectors` 提供的静态方法。

##### （1）转为集合



- `Collectors.toList()`：转为 List（默认 ArrayList）；

- `Collectors.toSet()`：转为 Set（默认 HashSet）；

- ```
  Collectors.toCollection(ArrayList::new)
  ```

  

  ：指定集合类型（如 LinkedList、TreeSet）；

  示例：



  ```
  List<String> list = Stream.of("a", "b").collect(Collectors.toList());
  Set<String> set = Stream.of("a", "b").collect(Collectors.toSet());
  LinkedList<String> linkedList = Stream.of("a", "b")
      .collect(Collectors.toCollection(LinkedList::new));
  ```

  

##### （2）转为 Map



- `Collectors.toMap(Function<? super T, ? extends K> keyMapper, Function<? super T, ? extends U> valueMapper)`：键值映射（注意键唯一，否则抛异常）；

- 重载：

  ```
  toMap(..., BinaryOperator<U> mergeFunction)
  ```

  

  ：解决键冲突（如取第一个 / 最后一个值）；

  示例：将用户列表转为「ID→用户」的 Map


  ```
  class User {
      private Integer id;
      private String name;
      // 构造器、getter
  }
  List<User> users = Arrays.asList(new User(1, "张三"), new User(2, "李四"));
  
  // 键：id，值：name（键唯一）
  Map<Integer, String> idToName = users.stream()
      .collect(Collectors.toMap(User::getId, User::getName)); // {1=张三, 2=李四}
  
  // 键冲突时取后一个值
  List<User> duplicateUsers = Arrays.asList(new User(1, "张三"), new User(1, "张三2"));
  Map<Integer, String> idToName2 = duplicateUsers.stream()
      .collect(Collectors.toMap(User::getId, User::getName, (v1, v2) -> v2)); // {1=张三2}
  ```

  

##### （3）分组（groupingBy）



- `Collectors.groupingBy(Function<? super T, ? extends K> classifier)`：按指定字段分组（键是分组字段，值是 List）；

- 重载：

  ```
  groupingBy(..., Collectors.counting())
  ```

  

  ：分组后统计个数；

  ```
  groupingBy(..., Collectors.mapping(...))
  ```

  

  ：分组后转换值；

  示例：按用户年龄分组



  ```
  List<User> users = Arrays.asList(
      new User(1, "张三", 20),
      new User(2, "李四", 20),
      new User(3, "王五", 25)
  );
  // 分组：年龄 → 该年龄的用户列表
  Map<Integer, List<User>> ageToUsers = users.stream()
      .collect(Collectors.groupingBy(User::getAge)); // {20=[张三,李四], 25=[王五]}
  
  // 分组后统计个数：年龄 → 人数
  Map<Integer, Long> ageToCount = users.stream()
      .collect(Collectors.groupingBy(User::getAge, Collectors.counting())); // {20=2, 25=1}
  ```

  

##### （4）分区（partitioningBy）



- ```
  Collectors.partitioningBy(Predicate<? super T> predicate)
  ```

  

  ：按布尔条件分区（键是

  ```
  boolean
  ```

  

  ，值是 List，仅分两组：true/false）；

  示例：按是否成年分区


  ```
  Map<Boolean, List<User>> adultPartition = users.stream()
      .collect(Collectors.partitioningBy(u -> u.getAge() >= 18)); // {true=[所有用户]}
  ```

  

##### （5）拼接字符串



- `Collectors.joining()`：拼接所有元素为字符串；

- 重载：

  ```
  joining(CharSequence delimiter, CharSequence prefix, CharSequence suffix)
  ```

  

  ：指定分隔符、前缀、后缀；

  示例：


  ```
  String join1 = Stream.of("a", "b", "c").collect(Collectors.joining()); // "abc"
  String join2 = Stream.of("a", "b", "c").collect(Collectors.joining(",", "[", "]")); // "[a,b,c]"
  ```

  

##### （6）归约（reducing）



- ```
  Collectors.reducing(T identity, BinaryOperator<T> accumulator)
  ```

  

  ：自定义聚合（如求和、求最大值，类似

  ```
  reduce
  ```

  

  方法）；

  示例：求所有用户年龄总和



  ```
  int totalAge = users.stream()
      .collect(Collectors.reducing(0, User::getAge, Integer::sum)); // 20+20+25=65
  ```

  

#### 6. 归约（reduce，底层是 collect 的基础）



- **`reduce(T identity, BinaryOperator<T> accumulator)`**自定义归约：从初始值 `identity` 开始，通过 `accumulator` 累加元素（如求和、求乘积）。示例：求 1-5 的和

  java

  运行

  ```
  int sum = Stream.of(1, 2, 3, 4, 5)
      .reduce(0, (a, b) -> a + b); // 0+1+2+3+4+5=15
  ```

  

- **`reduce(BinaryOperator<T> accumulator)`**无初始值的归约（返回 `Optional<T>`，避免流为空时无结果）。示例：


  ```
  Optional<Integer> sumOpt = Stream.of(1, 2, 3).reduce(Integer::sum);
  sumOpt.ifPresent(System.out::println); // 6
  ```

  

### 三、核心总结



Stream 流的 API 可按 “功能 + 操作类型” 记忆：

1. **中间操作**：侧重 “数据处理逻辑”（转换、过滤、排序、调试），返回 Stream 可链式调用；
2. **终端操作**：侧重 “结果输出”（遍历、聚合、收集、匹配），触发流执行；
3. 高频 API 优先级：`filter`/`map`（处理）→ `sorted`（排序）→ `collect`（收集）/`forEach`（遍历）→ `anyMatch`/`findFirst`（匹配查找）。

关键技巧：

- 复杂收集用 `Collectors` 工具类（分组、分区、转 Map / 字符串）；
- 数值操作优先用 `mapToInt`/`IntStream`，避免装箱开销；
- 调试用 `peek`，并行流需注意线程安全和顺序需求（`forEachOrdered`）。
