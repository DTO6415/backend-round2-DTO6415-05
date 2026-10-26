package com.example.logindemo;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(
            @Valid @RequestBody UserRequest request) {
        String message = userService.register(
                request.getUsername(), request.getPassword());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", message));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(
            @Valid @RequestBody UserRequest request) {
        String message = userService.login(
                request.getUsername(), request.getPassword());

        return ResponseEntity.ok(Map.of("message", message));
    }
}
