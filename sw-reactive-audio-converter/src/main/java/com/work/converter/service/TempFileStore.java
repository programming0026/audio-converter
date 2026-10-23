package com.work.converter.service;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TempFileStore {

    private final ConcurrentHashMap<String, Path> store = new ConcurrentHashMap<>();

    public String register(Path filePath) {
        String token = UUID.randomUUID().toString();
        store.put(token, filePath);
        return token;
    }

    public Optional<Path> retrieve(String token) {
        return Optional.ofNullable(store.remove(token));
    }
}
