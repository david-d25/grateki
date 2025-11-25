package com.example;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NestedAndDynamicTest {

    @Nested
    class Inner {
        @Test
        void innerPass() {
            assertTrue(true);
        }
    }

    @TestFactory
    Stream<DynamicTest> dynamic() {
        return List.of(1,2,3).stream().map(i ->
            DynamicTest.dynamicTest("dyn-"+i, () -> assertTrue(i > 0))
        );
    }
}