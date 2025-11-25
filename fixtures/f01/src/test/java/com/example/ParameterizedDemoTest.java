package com.example;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ParameterizedDemoTest {
    @ParameterizedTest
    @ValueSource(ints = {2,4,6})
    void even(int v) {
        assertTrue(v % 2 == 0);
    }
}