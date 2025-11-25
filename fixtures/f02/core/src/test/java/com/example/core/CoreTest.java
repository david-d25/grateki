package com.example.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoreTest {
    @Test
    void pass() {
        assertEquals(2, CoreUtil.inc(1));
    }
}