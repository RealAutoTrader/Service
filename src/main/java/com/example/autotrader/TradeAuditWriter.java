package com.example.autotrader;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Component
public class TradeAuditWriter {

    private final ObjectMapper objectMapper;
    private final Path auditPath = Path.of("logs", "trade-audit.jsonl");

    public TradeAuditWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public synchronized void write(Dtos.TradeRunAudit audit) {
        try {
            Files.createDirectories(auditPath.getParent());
            String line = objectMapper.writeValueAsString(audit) + System.lineSeparator();

            Files.writeString(
                    auditPath,
                    line,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
            );
        } catch (Exception e) {
            throw new RuntimeException("trade-audit.jsonl 저장 실패", e);
        }
    }
}