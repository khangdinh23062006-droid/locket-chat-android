package com.fpt.locketchat.utils;

import org.junit.Test;
import java.time.*;
import static org.junit.Assert.*;

public class MessageFormatTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-16T05:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    @Test public void sameDayConvertsUtcToLocalTime() {
        assertEquals("10:30", MessageFormat.time("2026-11-16T03:30:00.000Z", clock));
    }
    @Test public void offsetAndSqlDatesAreSupported() {
        assertEquals("10:30", MessageFormat.time("2026-11-16T10:30:00+07:00", clock));
        assertEquals("10:30", MessageFormat.time("2026-11-16 10:30:00", clock));
    }
    @Test public void olderDaysAndYearsIncludeDate() {
        assertEquals("15/11 23:00", MessageFormat.time("2026-11-15T16:00:00Z", clock));
        assertEquals("31/12/2025 23:00", MessageFormat.time("2025-12-31T16:00:00Z", clock));
    }
    @Test public void invalidValuesDoNotCrash() {
        assertEquals("", MessageFormat.time(null, clock));
        assertEquals("", MessageFormat.time("", clock));
        assertEquals("", MessageFormat.time("not a date", clock));
        assertEquals("", MessageFormat.time("2026-99-99", clock));
    }
    @Test public void attachmentPreviewsDescribeTheType() {
        assertTrue(MessageFormat.preview("VIDEO", "movie.mp4").contains("Video"));
        assertTrue(MessageFormat.preview("AUDIO", null).contains("thoại"));
        assertTrue(MessageFormat.preview("FILE", "report.pdf").contains("report.pdf"));
        assertEquals("", MessageFormat.preview("TEXT", null));
    }
}
