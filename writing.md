# T5

## 整体思路

我们需要实现的接口是登录和注册。为了实现登录和注册的业务逻辑，我们需要：

- 进行配置
- 接收前端发送的信息
- 判断该登录或注册请求是否合法（字符串长度/内容是否合法）
- 调取数据库（本题中为外部JSON文件）中的信息
- （注册）若用户不存在，向数据库中写入加密后的信息
- 对各环节出现的错误进行处理

为了实现这些需求，我们需要以下类：

- 启动类
- 用户信息类，保存用户名和密码
- 用户服务类，用于与数据库交互、进行逻辑判断
- `Controller`类，用于与前端交换信息
- 异常拦截类，处理报错

## 代码实现

接下来按层次看这些类：

### `User`类

```Java
public class User {
    private String username;
    private String passwordHash;

    public User() {
    }

    public User(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}

```

`User`类用于存放用户信息。注意密码部分，存储的是加密过后的密码，也就是其哈希值。

### `UserRequest`类：

```java

import jakarta.validation.constraints.NotBlank;

public class UserRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    public UserRequest() {
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
```

这个类指的是用户登录或注册的请求。看上去与`User`类十分像，不过二者实际指代的东西不一样，因此这个类多了一层判空，密码也不是加密过后的。
没有含参构造，是因为这个类的对象主要由Spring框架创建。

### `UserController`类：

```Java

@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }
    ...
}
```
该类需要一个`UserService`对象来调用其中的方法。显然这个对象也是由框架创建的，体现了DI思想。

```java

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(
            @Valid @RequestBody UserRequest request) {
        String message = userService.register(
                request.getUsername(), request.getPassword());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", message));
    }
```

注册接口的部分。
输出值为`ResponseEntity`类型，代表了整个响应，包括状态码、响应头、响应体。
传入值是请求体，判断值是否合法后自动被转换为`UserRequest`对象。
`Controller`中的方法只用于接收和输出响应信息。接下来调用`UserService`中的方法进行业务逻辑的实现。
如果方法成功被执行，则返回状态码"CREATED"以及成功信息。

```java

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(
            @Valid @RequestBody UserRequest request) {
        String message = userService.login(
                request.getUsername(), request.getPassword());

        return ResponseEntity.ok(Map.of("message", message));
    }
```

登录接口的部分。
与注册接口差不多，返回的状态码是"OK"。

由此可见，实现业务逻辑的核心代码全在这个`UserService`类中，下面我们就来会一会它。

### `UserService`类

```Java

@Service
public class UserService {
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;
    
    @Value("${app.user-file}")
    private Path userFile;

    @Value("${app.password-min-length}")
    private int passwordMinLength;

    @Value("${app.username-regex}")
    private String usernameRegex;

    public UserService(
            ObjectMapper objectMapper,
            PasswordEncoder passwordEncoder) {
        this.objectMapper = objectMapper;
        this.passwordEncoder = passwordEncoder;
    }

    ...
}
```

`ObjectMapper`是来自外部库的一个类，用于对象和JSON文件之间的转换；
`PasswordEncoder`是来自外部库的一个类，用于加密文件相关；
`Path`指的是文件路径。不同于前二者主要被用于调用方法，这个对象用于读取配置文件，保存数据库文件的路径。
下面二者用于读取配置文件，分别保存密码的最小长度、用户名的格式。
构造方法里，只用初始化前二者即可，因为下面三个对象均可被自动创建，且被赋予@value()中的值。值得注意的是`userfile`接收到的值虽然是`String`类型，但通常可以被自动转换。

```java

    public static class UserAlreadyExistsException extends RuntimeException {
        public UserAlreadyExistsException(String message) {
            super(message);
        }
    }

    public static class InvalidLoginException extends RuntimeException {
        public InvalidLoginException(String message) {
            super(message);
        }
    }
```

这是两个自定义的异常类。自定义异常类可以提高代码的可读性。

```Java

    private void validateUsername(String username) {
        if (!username.matches(usernameRegex)) {
            throw new IllegalArgumentException("用户名格式不符合要求");
        }
    }

```

这个方法用于检测用户名是否合法。之所以把它单拎出来，是因为我们配置文件中用户名的格式是一个正则表达式，需要用`matches`方法专门比较，单列出来能减少代码重复度；实际上也应该把判断密码格式的方法也单列出来的，但我忘了（（（

```java

    private List<User> readUsers() {
        try {
            if (userFile.getParent() != null) {
                Files.createDirectories(userFile.getParent());
            }

            if (Files.notExists(userFile)) {
                Files.writeString(userFile, "[]");
            }

            return objectMapper.readValue(
                    userFile.toFile(),
                    new TypeReference<List<User>>() {
                    });
        } catch (IOException e) {
            throw new IllegalStateException("读取用户数据失败：" + e.getMessage(), e);
        }
    }

```

这个方法用于读取数据库。
返回值是一个`list`，代表用户信息列表。
这里用到一个`try-catch`结构，用于报错。
前两个`if`语句都是为了防止文件路径不存在，构建文件路径用的。`GetParent`可以用于得到文件路径父目录的值。
`return`语句用到了`objectMapper`的转换JSON为其他格式方法，用`TypeReference`来指定返回`List<User>`类型。
报错可能主要是因为JSON文件被破坏导致的。

```java

    private void writeUsers(List<User> users) {
        try {
            if (userFile.getParent() != null) {
                Files.createDirectories(userFile.getParent());
            }

            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(userFile.toFile(), users);
        } catch (IOException e) {
            throw new IllegalStateException("保存用户数据失败：" + e.getMessage(), e);
        }
    }

```

这个方法用于将新信息写入数据库。
相比上一个方法更简单一些。同样判断文件路径是否存在。
这里调用了`writeValue`方法，实现了向数据库中插入修改过的新list(也就是users)；`.writerWithDefaultPrettyPrinter()`这个东西是一个美化器，加上能让JSON文件可读性提升。
值得注意的是上述方法也能在文件不存在时自动创建文件，因此无需判断文件本身是否存在。
这里同样用到一个`try-catch`结构，用于报错。

```java

    public String register(String username, String password) {
        validateUsername(username);

        if (password.length() < passwordMinLength) {
            throw new IllegalArgumentException(
                    "密码长度不能少于 " + passwordMinLength + " 个字符");
        }

        List<User> users = readUsers();

        for (User user : users) {
            if (user.getUsername().equals(username)) {
                throw new UserAlreadyExistsException("用户名已存在");
            }
        }

        String passwordHash = passwordEncoder.encode(password);
        users.add(new User(username, passwordHash));
        writeUsers(users);

        return "注册成功";
    }

```

重头戏来了。这是注册部分的业务逻辑。

首先判断用户名和密码是否合法。接下来，读取数据库信息。
然后用`for-each`循环遍历数据库，若检测到已注册过，则抛出异常信息即可。
如果没问题，就对信息进行处理：调用`encode`来对密码进行加密，得到密码的哈希值。接着向list中加入新信息，写入文件即可。

```java

    public String login(String username, String password) {
        List<User> users = readUsers();

        for (User user : users) {
            if (user.getUsername().equals(username)
                    && passwordEncoder.matches(password, user.getPasswordHash())) {
                return "Success";
            }
        }

        throw new InvalidLoginException("用户名或密码错误");
    }

```

这是登录部分的业务逻辑，比上面那个简单一些。

登录就不用判断传入的用户名和密码是否合法了，反正不合法也通过不了。直接读取数据库文件，遍历数据库，查看有没有对应的用户信息即可。

值得注意的是判断密码是否一致需要用到`.matches`这个方法，因为我们采用的`Bcrypt`加密方法是一种单向加密，同一个字符串产生的哈希值每次都不一样，不能直接`equal`，而是要调用库里的方法进行比较。

上面就是这个类中的所有内容。

### `GlobalExceptionHandler`类

这就是传说中的全局异常拦截器。`@RestControllerAdvice`表明了其身份。
我们一个一个看其中的方法：

```Java
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
            MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "请求参数不合法"));
    }

```

这个方法用于处理`MethodArgumentNotValidException`这种异常。这种异常是当`@valid`注解的部分被检测到不合法时，也就是当前端传入的请求参数错误时出现的。


```java

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleBadJson(
            HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "请求 JSON 格式不正确"));
    }

```

这个方法用于处理`HttpMessageNotReadableException`这种异常。它是当请求的JSON格式不正确时出现的。
返回的是`BAD_REQUEST`状态码，以及对应语句。

```java

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(
            IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
    }
```

这个方法用于处理`IllegalArgumentException`这种异常。它是当用户名或密码格式错误时出现的。
返回的是`BAD_REQUEST`状态码，以及对应语句。

```java

    @ExceptionHandler(UserService.UserAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateUser(
            UserService.UserAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
    }
```

这个方法用于处理`UserAlreadyExistsException`这种自定义异常。它是当注册时用户已存在时出现的。
返回的是`CONFLICT`状态码，以及对应语句。

```Java

    @ExceptionHandler(UserService.InvalidLoginException.class)
    public ResponseEntity<Map<String, String>> handleInvalidLogin(
            UserService.InvalidLoginException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", e.getMessage()));
    }

```

这个方法用于处理`InvalidLoginException`这种自定义异常。它是当登录失败时出现的。
返回的是`UNAUTHORIZED`状态码，以及对应语句。

```java

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleDataError(
            IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "用户数据处理失败"));
    }

```

这个方法用于处理`IllegalStateException`这种自定义异常。它是当文件处理失败(例如读取数据失败)时出现的。
返回的是`INTERNAL_SERVER_ERROR(500)`状态码，以及对应语句。

## 思考

1. 关于密码：

   - 为什么密码不能用明文存？
        这涉及到安全上的问题。一旦数据库泄露，盗用账号数据便轻而易举。
   - 散列和加密（可逆）有什么区别？
        上文中我似乎为了易于理解，把散列称作了加密，实际上这二者不同。散列常指用某些哈希算法等将一段字符串通过计算转换成另一段字符串或数字，加密常指用某种特定的变换手段将一个字符串变为另一个。
        这二者最关键的差别就是前者是不可逆的，就像碎纸机，打碎了无法得到原状；后者是可逆的，用密钥就可以还原。
        前者的有点便是安全性高，假使后者的密钥遭到窃取，那么精心设计的加密方式便形同虚设，而前者在短时间内没法大规模通过散列得到的序列还原成原文。
   - 同一个数据散列得到的序列应不应该一样？
        显然答案是否定的。如果一样，则基本上每个数据与密文是一一对应的，此时散列的必要性也体现不出来。

2. 全局异常拦截器统一处理的优点？
   显然，如果我们每写一个方法，都要根据对应的报错写一个响应，那么就会滋生出一堆`if-else`、`try-catch`等结构，处理报错的细枝末节就会与业务逻辑的实现混杂在一起，降低了代码的可读性；
   同时，多种实际情况对应的是同种异常的情况也不少见，如我们代码中`IllegalArgumentException`这种异常，用户名或密码格式不正确两种情况均可触发。此时，全局的拦截器也能降低代码重复度。

3. 我的项目还有什么可以改进的地方？
   这个项目是一个用于练手的十分初级的项目，还有很多可以改进的地方。例如，可以加一个用户注销的接口；可以将JSON文件换成我们接下来要学的数据库；异常处理返回的文本可以更加具体；响应头和响应体的组成可以更加丰富；等等。相信随着我接下来的学习，可以让我的项目一步一步地越做越好，越做越有实践价值。

## 测试

这次测试用一个视频归纳。

1. 注册三个常规用户，其中两个密码一致，查看`users.json`，可知二者散列后得到的值不同。
2. 尝试错误的注册方法：用户名已存在、用户名格式不合法、密码格式不合法。
3. 尝试登录：登录成功、登录失败。
4. 发送错误的JSON格式。