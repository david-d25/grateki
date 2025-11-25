package com.example;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class SkippedTest {
    @Disabled("Intentional skip")
    @Test
    void skipped() {}
}