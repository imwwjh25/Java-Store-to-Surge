Java 的 IO 流体系核心围绕 **“数据传输方式”** 和 **“处理数据类型”** 分类，字节流处理二进制数据（如文件、网络流），字符流处理文本数据（按字符编码转换），两者通过 “桥接流” 实现转换。以下是详细梳理：

## 一、Java IO 流的整体分类

IO 流的分类维度有 3 个，核心是 **“字节流 vs 字符流”**（处理数据类型），再结合 “输入 vs 输出”（数据流向）、“节点流 vs 处理流”（流的功能），形成完整体系：

### 1. 核心分类表

| 分类维度     | 具体类型                    | 核心说明                                                     |
| ------------ | --------------------------- | ------------------------------------------------------------ |
| **数据类型** | 字节流（Byte Stream）       | 以字节（`byte`）为单位读写，无编码转换，适用于所有二进制数据（文件、图片、视频） |
|              | 字符流（Character Stream）  | 以字符（`char`）为单位读写，需指定编码（如 UTF-8），仅适用于文本数据（.txt、.java） |
| **数据流向** | 输入流（Input Stream）      | 从外部设备（文件、网络）读取数据到程序（读操作）             |
|              | 输出流（Output Stream）     | 从程序写入数据到外部设备（写操作）                           |
| **流的功能** | 节点流（Node Stream）       | 直接连接数据源 / 目标（如文件、Socket），是 IO 流的 “底层流”（无依赖） |
|              | 处理流（Processing Stream） | 包装节点流 / 其他处理流，增强功能（如缓冲、编码转换、对象序列化），是 “上层流” |

### 2. 字节流核心体系（`java.io` 包）

字节流的顶层抽象类是 `InputStream`（输入）和 `OutputStream`（输出），所有字节流都继承这两个类：

| 流类型         | 核心实现类                                       | 功能描述                                                     |
| -------------- | ------------------------------------------------ | ------------------------------------------------------------ |
| **节点字节流** | `FileInputStream` / `FileOutputStream`           | 读写本地文件（二进制文件如图片、视频，或文本文件）           |
|                | `ByteArrayInputStream` / `ByteArrayOutputStream` | 读写内存中的字节数组（无需外部设备，高效）                   |
|                | `SocketInputStream` / `SocketOutputStream`       | 网络通信（TCP 连接）的字节流                                 |
|                | `PipedInputStream` / `PipedOutputStream`         | 线程间通信（管道流，需搭配线程使用）                         |
| **处理字节流** | `BufferedInputStream` / `BufferedOutputStream`   | 缓冲流：减少磁盘 / 网络 IO 次数，提升读写效率（核心优化流）  |
|                | `DataInputStream` / `DataOutputStream`           | 数据流：读写基本数据类型（`int`、`double` 等）和字符串（保持数据类型） |
|                | `ObjectInputStream` / `ObjectOutputStream`       | 对象流：序列化（对象→字节）和反序列化（字节→对象），需实现 `Serializable` |
|                | `FilterInputStream` / `FilterOutputStream`       | 过滤流：所有处理流的父类，自定义处理流时继承                 |

### 3. 字符流核心体系（`java.io` 包）

字符流的顶层抽象类是 `Reader`（输入）和 `Writer`（输出），所有字符流都继承这两个类：

| 流类型         | 核心实现类                                 | 功能描述                                                     |
| -------------- | ------------------------------------------ | ------------------------------------------------------------ |
| **节点字符流** | `FileReader` / `FileWriter`                | 读写本地文本文件（默认使用系统编码，如 Windows GBK、Linux UTF-8） |
|                | `CharArrayReader` / `CharArrayWriter`      | 读写内存中的字符数组                                         |
|                | `StringReader` / `StringWriter`            | 读写字符串（`String`）数据                                   |
|                | `PipedReader` / `PipedWriter`              | 线程间管道通信（字符版）                                     |
| **处理字符流** | `BufferedReader` / `BufferedWriter`        | 缓冲字符流：提供 `readLine()` 读整行、`newLine()` 换行，提升文本读写效率 |
|                | `InputStreamReader` / `OutputStreamWriter` | 桥接流：字节流→字符流（核心转换流，指定编码）                |
|                | `PrintWriter`                              | 打印流：提供 `print()`/`println()` 方法，自动刷新，支持字符 / 字节流包装 |
|                | `FilterReader` / `FilterWriter`            | 字符过滤流：自定义字符处理（如敏感词替换）                   |

## 二、字节流与字符流的转换（核心：桥接流）

字节流和字符流的本质区别是 **“是否处理编码”**：字节流是原始二进制数据，字符流是二进制数据按编码（如 UTF-8）转换后的字符。转换的核心是 **`InputStreamReader`（字节→字符）** 和 **`OutputStreamWriter`（字符→字节）**，这两个类是 “桥接流”，必须指定编码（如 UTF-8）避免乱码。

### 1. 转换原理

- 字节→字符（读操作）：`InputStreamReader` 包装字节输入流，将字节数据按指定编码（如 UTF-8）解码为字符，供字符流（如 `BufferedReader`）读取；
- 字符→字节（写操作）：`OutputStreamWriter` 包装字节输出流，将字符数据按指定编码编码为字节，通过字节流写入外部设备。

### 2. 转换示例（文件读写场景）

#### 示例 1：字节流 → 字符流（读文本文件）

需求：用字节流读取文本文件，通过桥接流转换为字符流，按行读取（避免手动处理编码）。






```java
import java.io.*;

public class ByteToCharDemo {
    public static void main(String[] args) {
        // 1. 节点字节流（连接文件数据源）
        try (InputStream in = new FileInputStream("test.txt");
             // 2. 桥接流（字节→字符，指定编码 UTF-8，关键！）
             InputStreamReader isr = new InputStreamReader(in, "UTF-8");
             // 3. 处理字符流（缓冲流，增强功能：readLine() 读整行）
             BufferedReader br = new BufferedReader(isr)) {

            String line;
            while ((line = br.readLine()) != null) { // 按行读取，无乱码
                System.out.println("读取内容：" + line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```

- 核心链：`FileInputStream`（字节节点流）→ `InputStreamReader`（桥接转换）→ `BufferedReader`（字符处理流）；
- 必须指定编码（如 UTF-8）：若省略，默认使用系统编码（可能导致乱码，如 Windows 系统编码为 GBK，读取 UTF-8 文件会乱码）。

#### 示例 2：字符流 → 字节流（写文本文件）

需求：用字符流写入文本，通过桥接流转换为字节流，写入文件（指定编码避免乱码）。









```java
import java.io.*;

public class CharToByteDemo {
    public static void main(String[] args) {
        // 1. 节点字节流（连接文件目标）
        try (OutputStream out = new FileOutputStream("output.txt");
             // 2. 桥接流（字符→字节，指定编码 UTF-8）
             OutputStreamWriter osw = new OutputStreamWriter(out, "UTF-8");
             // 3. 处理字符流（缓冲流，增强功能：newLine() 换行）
             BufferedWriter bw = new BufferedWriter(osw)) {

            bw.write("Hello 字节流转换！"); // 字符写入
            bw.newLine(); // 换行（跨平台兼容）
            bw.write("编码：UTF-8，无乱码");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```

- 核心链：`BufferedWriter`（字符处理流）→ `OutputStreamWriter`（桥接转换）→ `FileOutputStream`（字节节点流）；
- 编码一致性：写入和读取时必须使用相同编码（如都是 UTF-8），否则会乱码。

#### 示例 3：简化写法（`PrintWriter` 直接包装字节流）

`PrintWriter` 是字符处理流，可直接包装字节流并指定编码，简化转换流程：










```java
// 直接用 PrintWriter 包装字节输出流（内置桥接转换）
try (PrintWriter pw = new PrintWriter(new FileOutputStream("print.txt"), true, "UTF-8")) {
    pw.println("简化字符→字节转换");
    pw.print("自动刷新（第二个参数为 true）");
} catch (FileNotFoundException e) {
    e.printStackTrace();
}
```

- 第二个参数 `true`：自动刷新（调用 `println()` 后立即写入，无需手动 `flush()`）；
- 本质：`PrintWriter` 内部封装了 `OutputStreamWriter` 桥接流。

## 三、转换的关键注意事项（避坑指南）

1. **编码必须一致**：写入和读取时的编码（如 UTF-8）必须相同，否则会出现乱码（如 UTF-8 写入、GBK 读取，中文会变成 “???”）；
2. **优先指定编码**：避免依赖系统默认编码（`Charset.defaultCharset()`），不同环境（Windows/Linux）默认编码不同，会导致跨平台乱码；
3. **缓冲流的作用**：转换时搭配 `BufferedReader`/`BufferedWriter`，不仅提升效率（减少 IO 次数），还提供 `readLine()`/`newLine()` 等文本友好功能；
4. **异常处理**：编码错误（如指定不存在的编码 “UTF-9”）会抛出 `UnsupportedEncodingException`，需捕获或抛出；
5. **资源关闭**：使用 try-with-resources 语法（Java 7+），自动关闭流（从外层到内层依次关闭，无需手动调用 `close()`）。

## 四、核心总结

### 1. IO 流体系核心

- 字节流：处理所有二进制数据，顶层 `InputStream`/`OutputStream`，核心节点流 `FileInputStream`/`FileOutputStream`；
- 字符流：处理文本数据，顶层 `Reader`/`Writer`，核心节点流 `FileReader`/`FileWriter`；
- 处理流：包装节点流，增强功能（缓冲、转换、序列化），如 `BufferedXXX`、`ObjectXXX`。

### 2. 转换核心

- 字节→字符：`InputStreamReader`（桥接流），需指定编码，搭配 `BufferedReader` 提升文本读取效率；
- 字符→字节：`OutputStreamWriter`（桥接流），需指定编码，搭配 `BufferedWriter`/`PrintWriter` 提升文本写入效率；
- 核心原则：**文本数据用字符流（避免编码问题），二进制数据用字节流（无编码转换）**。

### 3. 常见使用场景

- 读图片 / 视频 / 压缩包：字节流（`FileInputStream`+`BufferedInputStream`）；
- 读文本文件 / 写日志：字符流（`InputStreamReader`+`BufferedReader` / `OutputStreamWriter`+`BufferedWriter`）；
- 网络通信（Socket）：字节流（网络传输的是二进制数据，文本需手动转换编码）；
- 对象序列化：字节流（`ObjectInputStream`/`ObjectOutputStream`）。
