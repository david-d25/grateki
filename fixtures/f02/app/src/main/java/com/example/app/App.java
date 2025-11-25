package com.example.app;

import com.example.core.CoreUtil;

public class App {
    public static int twice(int x) {
        return CoreUtil.inc(CoreUtil.inc(x));
    }
}