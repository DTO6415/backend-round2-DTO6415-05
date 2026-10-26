package com.example.logindemo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

    private void validateUsername(String username) {
        if (!username.matches(usernameRegex)) {
            throw new IllegalArgumentException("用户名格式不符合要求");
        }
    }

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
}
