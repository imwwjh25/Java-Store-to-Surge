
## 使用系统类加载器
Java的系统类加载器（ClassLoader.getSystemClassLoader()）是默认的类加载器，可以用来加载类

```plain
public static void main(String[] args) {
  try {
    Class<?> clazz = ClassLoader.getSystemClassLoader().loadClass("com.example.MyClass");
    System.out.println("Class loaded: " + clazz.getName());
  } catch (ClassNotFoundException e) {
    e.printStackTrace();
  }
}
```

## 使用自定义类加载器
```plain
public class CustomClassLoader extends ClassLoader {
  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    byte[] classData = loadClassData(name);
    if (classData == null) {
      throw new ClassNotFoundException();
    }
    return defineClass(name, classData, 0, classData.length);
  }

  private byte[] loadClassData(String name) {
    // 实现加载类数据的逻辑
    return null; // 示例中返回null，实际应返回类的字节码数据
  }

  public static void main(String[] args) {
    try {
      CustomClassLoader customClassLoader = new CustomClassLoader();
      Class<?> clazz = customClassLoader.loadClass("com.example.MyClass");
      System.out.println("Class loaded: " + clazz.getName());
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }
  }
}
```

## 使用URLClassLoader
URLClassLoader可以从指定的URL加载类，适用于从JAR文件或远程位置加载类

```plain
import java.net.URL;
import java.net.URLClassLoader;

public class URLClassLoaderExample {

  public static void main(String[] args) {
    try {
      URL[] urls = {new URL("file:///path/to/your/classes/")};
      URLClassLoader urlClassLoader = new URLClassLoader(urls);
      Class<?> clazz = urlClassLoader.loadClass("com.example.MyClass");
      System.out.println("Class loaded: " + clazz.getName());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
```

## 使用反射
使用反射机制的Class.forName()方法加载类：

```plain
public static void main(String[] args) {
  try {
    Class<?> clazz = Class.forName("com.example.MyClass");
    System.out.println("Class loaded: " + clazz.getName());
  } catch (ClassNotFoundException e) {
    e.printStackTrace();
  }
}
```

## 使用Thread.currentThread().getContextClassLoader()
获取当前线程的上下文类加载器来加载类：

```plain
public static void main(String[] args) {
  try {
    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    Class<?> clazz = contextClassLoader.loadClass("com.example.MyClass");
    System.out.println("Class loaded: " + clazz.getName());
  } catch (ClassNotFoundException e) {
    e.printStackTrace();
  }
}
```






