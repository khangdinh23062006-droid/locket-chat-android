package com.fpt.locketchat.utils;

import org.junit.Test;
import static org.junit.Assert.*;

public class FileUtilsTest {

    @Test
    public void testFormatFileSize() {
        assertEquals("0 B", FileUtils.formatFileSize(0));
        assertEquals("500 B", FileUtils.formatFileSize(500));
        assertEquals("1 KB", FileUtils.formatFileSize(1024));
        assertEquals("1.5 KB", FileUtils.formatFileSize(1536));
        assertEquals("2 MB", FileUtils.formatFileSize(2 * 1024 * 1024));
    }
}
