# 简化版 Spring Boot 登录注册项目

## 环境
- JDK 21
- Maven
- Spring Boot 3.5.0

## 启动
在项目根目录执行：

```bash
mvn spring-boot:run
```

也可以在 IntelliJ IDEA 中运行 `LoginDemoApplication`。

## 注册
POST `http://localhost:8080/register`

JSON 请求体：

```json
{
  "username": "user_01",
  "password": "abc123"
}
```

注册成功返回 HTTP 201：

```json
{
  "message": "注册成功"
}
```

## 登录
POST `http://localhost:8080/login`

```json
{
  "username": "user_01",
  "password": "abc123"
}
```

成功返回 HTTP 200：

```json
{
  "message": "Success"
}
```

用户名或密码错误返回 HTTP 401；参数为空或格式不符合规则返回 HTTP 400；
重复注册返回 HTTP 409。

## 配置
修改 `src/main/resources/application.yml` 后重启应用，配置才会重新加载：
- `app.user-file`：用户 JSON 文件路径
- `app.password-min-length`：注册密码最小长度
- `app.username-regex`：用户名正则规则

注意：相对路径相对于启动应用时的工作目录，不一定相对于 YAML 文件所在目录。

## 数据安全说明
用户 JSON 文件中保存的是 BCrypt 散列值，而不是明文密码。此项目是教学练习示例，不适用于生产环境：JSON 文件不适合多进程并发写入，也没有完整的账户安全、限流、会话或令牌机制。
