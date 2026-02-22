### 9. Spring 拦截器（Interceptor）和过滤器（Filter）的区别

你想区分 Spring 拦截器和过滤器，核心从**所属层级、触发时机、功能范围**等维度对比：

| 维度         | 拦截器（Interceptor）                       | 过滤器（Filter）                           |
| ------------ | ------------------------------------------- | ------------------------------------------ |
| **所属规范** | Spring 框架自定义（非 Servlet 规范）        | Servlet 规范（JavaEE 标准）                |
| **触发时机** | DispatcherServlet 之后，Controller 之前     | 请求进入 Tomcat 后，DispatcherServlet 之前 |
| **拦截范围** | 仅拦截 Spring MVC 的请求（@RequestMapping） | 拦截所有请求（包括静态资源、HTML 等）      |
| **依赖环境** | 依赖 Spring 容器，可注入 Bean               | 不依赖 Spring，无法直接注入 Bean           |
| **拦截粒度** | 方法级别（可拦截具体 Controller 方法）      | 请求级别（仅拦截 URL）                     |
| **异常处理** | 可捕获 Controller 中的异常                  | 无法捕获 Controller 中的异常               |
| **实现方式** | 实现 HandlerInterceptor 接口                | 实现 Filter 接口                           |

#### 示例对比

##### （1）过滤器实现（拦截所有请求）


```java
public class LogFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        // 前置处理
        System.out.println("过滤器：请求开始");
        chain.doFilter(request, response); // 放行
        // 后置处理
        System.out.println("过滤器：请求结束");
    }
}
```

##### （2）拦截器实现（仅拦截 Controller 请求）

```java
@Component
public class LogInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Controller执行前
        System.out.println("拦截器：Controller执行前");
        return true; // 放行
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        // Controller执行后，视图渲染前
        System.out.println("拦截器：Controller执行后");
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 视图渲染后（无论是否异常）
        System.out.println("拦截器：请求完成");
    }
}
```

#### 适用场景

- 过滤器：编码转换、跨域处理、静态资源拦截、全局日志；
- 拦截器：登录验证、权限控制、Controller 层日志、接口耗时统计。
