package com.practiceandexperiments.journalappplication.controller;

import com.practiceandexperiments.journalappplication.entity.User;
import com.practiceandexperiments.journalappplication.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/public")
public class PublicController {

    @Autowired
    private UserService userService;

    @GetMapping("/health-check")
    public String healthCheck() {
        return "OK";
    }

    @PostMapping("/create-user")
    public User createUser(@RequestBody User user) {
        userService.saveNewUser(user);
        return user;
    }
}
