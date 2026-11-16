package com.fpt.locketchat.utils;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public final class MessageFormat {
    private MessageFormat() {}
    public static String time(String value) {
        return time(value, Clock.systemDefaultZone());
    }
    public static String time(String value, Clock clock) {
        if (value == null || value.trim().isEmpty()) return "";
        try {
            String normalized = value.trim().replace(' ', 'T');
            ZonedDateTime date;
            try {
                date = Instant.parse(normalized).atZone(clock.getZone());
            } catch (DateTimeParseException e) {
                try {
                    date = OffsetDateTime.parse(normalized).atZoneSameInstant(clock.getZone());
                } catch (DateTimeParseException ignored) {
                    date = LocalDateTime.parse(normalized).atZone(clock.getZone());
                }
            }
            LocalDate today = LocalDate.now(clock);
            String pattern = date.toLocalDate().equals(today) ? "HH:mm"
                    : date.getYear() == today.getYear() ? "dd/MM HH:mm" : "dd/MM/yyyy HH:mm";
            return date.format(DateTimeFormatter.ofPattern(pattern, Locale.ROOT));
        } catch (DateTimeException e) { return ""; }
    }
    public static String preview(String type, String content) {
        if (type == null || "TEXT".equals(type)) return content == null ? "" : content;
        switch (type) {
            case "IMAGE": return "📷 Hình ảnh";
            case "VIDEO": return "🎬 Video";
            case "AUDIO": return "🎤 Tin nhắn thoại";
            case "FILE": return "📎 " + (content == null ? "Tệp đính kèm" : content);
            case "MOMENT": return "📷 Phản hồi khoảnh khắc";
            default: return content == null ? "" : content;
        }
    }
}
