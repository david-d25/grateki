package com.example.f01;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class BetaTest {

    @Test
    void betaFirst() {
        assertFalse(false);
    }

    @Test
    void betaSecond() throws InterruptedException {
        Thread.sleep(5L);
        assertFalse(false);
    }
}