package com.example;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FailingTest {
    @Test
    void fails() {
        fail("Intentional failure");
    }
}