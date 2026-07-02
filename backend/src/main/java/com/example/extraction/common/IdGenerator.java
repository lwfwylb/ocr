package com.example.extraction.common;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class IdGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private IdGenerator() {
    }

    public static String nextId(String prefix) {
        int suffix = RANDOM.nextInt(900000) + 100000;
        return prefix + LocalDateTime.now().format(FORMATTER) + suffix;
    }
}
