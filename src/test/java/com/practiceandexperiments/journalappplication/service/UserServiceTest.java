package com.practiceandexperiments.journalappplication.service;

import com.practiceandexperiments.journalappplication.entity.User;
import com.practiceandexperiments.journalappplication.repository.UserRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class UserServiceTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    // we can use this method to clean up the database or to initialize or run anything before each test
//    @BeforeEach
//    public void setUp() {
//    }

    // we can use this method to initialize the database or run anything before all tests
//    @BeforeAll
//    public static void init() {}

    // Similarly, we can use @AfterEach and @AfterAll to clean up the database or run anything after each test

    @Disabled
    @Test
    public void testFindByUsername() {
        User user = userRepository.findByUserName("ram");
        assertNotNull(user);
        assertTrue(!user.getJournalEntries().isEmpty());
    }

    @Disabled
    @ParameterizedTest
    @ValueSource(strings = {
            "ram",
            "vipu",
            "nikhil"
    })
    public void testFindByUsername(String name) {
        User user = userRepository.findByUserName(name);
        assertNotNull(user);
//        assertTrue(!user.getJournalEntries().isEmpty());
    }

    @Disabled
    @ParameterizedTest
    @ArgumentsSource(UserArgumentsProvider.class)
    public void testSaveUser(User user) {
        assertTrue(userService.saveNewUser(user));
    }

    @Disabled
    @ParameterizedTest
    @CsvSource({
            "1,2,3",
            "2,3,5"
    })
    public void test(int a, int b, int expected){
        assertEquals(expected, a + b);
    }
}
