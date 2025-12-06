package com.example.f01;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlphaTest {

    @Test
    void alphaFirst() {
        assertEquals(4, 2 * 2);
    }

    @Test
    void alphaSecond() {
        assertTrue("grateki".startsWith("g"));
    }

    @Disabled("integration skip example")
    @Test
    void alphaSkipped() {
        throw new IllegalStateException("should be skipped");
    }
}