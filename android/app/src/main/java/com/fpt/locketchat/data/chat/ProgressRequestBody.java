package com.fpt.locketchat.data.chat;

import java.io.File;
import java.io.IOException;
import java.util.function.IntConsumer;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;

public final class ProgressRequestBody extends RequestBody {
    private final File file;
    private final MediaType mime;
    private final IntConsumer progress;
    public ProgressRequestBody(File file, String mime, IntConsumer progress) {
        this.file = file;
        this.mime = MediaType.parse(mime == null ? "application/octet-stream" : mime);
        this.progress = progress;
    }
    @Override public MediaType contentType() { return mime; }
    @Override public long contentLength() { return file.length(); }
    @Override public void writeTo(BufferedSink sink) throws IOException {
        long total = file.length(), sent = 0;
        int previous = -1;
        try (Source source = Okio.source(file)) {
            long read;
            while ((read = source.read(sink.buffer(), 8192)) != -1) {
                sent += read;
                sink.emitCompleteSegments();
                int percent = total == 0 ? 100 : (int) (sent * 100 / total);
                if (percent != previous) { progress.accept(percent); previous = percent; }
            }
        }
    }
}
