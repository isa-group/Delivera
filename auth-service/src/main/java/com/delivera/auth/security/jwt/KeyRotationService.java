package com.delivera.auth.security.jwt;

import java.time.LocalDate;
import java.time.temporal.WeekFields;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.delivera.auth.config.JwtKeyProperties;

@Component
public class KeyRotationService {

    private final JwtKeyProperties properties;

    @Autowired
    public KeyRotationService(JwtKeyProperties properties) {
        this.properties = properties;
    }

    public String resolveActiveKeyIdSafe() {

        if (!properties.getRotation().isEnabled()) {
            return properties.getActiveKeyId();
        }

        String generated = generateKey();

        if (!properties.getKeys().containsKey(generated)) {
            return properties.getActiveKeyId();
        }

        return generated;
    }

    private String generateKey() {

        String period = properties.getRotation().getPeriod();
        LocalDate now = LocalDate.now();

        return switch (period) {

            case "MONTHLY" -> String.format("key-%d-%02d",
                    now.getYear(), now.getMonthValue());

            case "WEEKLY" -> {
                WeekFields wf = WeekFields.ISO;
                int week = now.get(wf.weekOfWeekBasedYear());
                int weekYear = now.get(wf.weekBasedYear());

                yield String.format("key-%d-W%02d",
                    weekYear, week);
            }

            default -> throw new IllegalArgumentException("Invalid rotation period");
        };
    }
}
