package com.practiceandexperiments.journalappplication.controller;

import com.practiceandexperiments.journalappplication.api.response.WeatherResponse;
import com.practiceandexperiments.journalappplication.entity.User;
import com.practiceandexperiments.journalappplication.repository.UserRepository;
import com.practiceandexperiments.journalappplication.service.UserService;
import com.practiceandexperiments.journalappplication.service.WeatherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WeatherService weatherService;

    @PutMapping
    public ResponseEntity<?> updateUser(@RequestBody User user) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        User userInDb = userService.findByUserName(username);
        userInDb.setUserName(user.getUserName());
        userInDb.setPassword(user.getPassword());
        userService.saveNewUser(userInDb);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @DeleteMapping
    public ResponseEntity<?> deleteUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        userRepository.deleteByUserName(authentication.getName());
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @GetMapping
    public ResponseEntity<?> greetings() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        WeatherResponse weatherResponse = weatherService.getWeather("Jaipur");
        String greeting = "";
        if(weatherResponse != null)  greeting = ", Weather feels like " + weatherResponse.getCurrent().getFeelslike();
        return new ResponseEntity<>("Hi "+authentication.getName() + greeting, HttpStatus.OK);
    }
}
