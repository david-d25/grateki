package com.example.api;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApiTest {
    @Test
    void pass() {
        assertTrue(Api.greet("a").startsWith("Hi"));
    }

    @Test
    void slow() throws Exception {
        Thread.sleep(500);
        assertTrue(true);
    }
}