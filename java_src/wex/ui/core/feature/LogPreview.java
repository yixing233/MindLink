package wex.ui.core.feature;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/** 日志预览只读取末尾 64 KiB，最多展示 200 行。 */
public final class LogPreview {

    private static final int MAX_BYTES = 64 * 1024;
    private static final int MAX_LINES = 200;

    private LogPreview() {
    }

    public static String read(File file) throws IOException {
        if (!file.exists()) {
            return "暂无日志";
        }
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            long length = input.length();
            if (length == 0) {
                return "暂无日志";
            }
            int count = (int) Math.min(length, MAX_BYTES);
            byte[] bytes = new byte[count];
            long offset = length - count;
            input.seek(offset);
            input.readFully(bytes);
            int start = 0;
            if (offset > 0) {
                while (start < count && bytes[start] != '\n') {
                    start++;
                }
                if (start == count) {
                    return "日志尾部单行过长，无法预览";
                }
                start++;
            }
            String text = new String(bytes, start, count - start, StandardCharsets.UTF_8);
            int end = text.length();
            if (end > 0 && text.charAt(end - 1) == '\n') {
                end--;
            }
            int lines = 1;
            int from = 0;
            for (int i = end - 1; i >= 0; i--) {
                if (text.charAt(i) == '\n' && ++lines > MAX_LINES) {
                    from = i + 1;
                    break;
                }
            }
            String tail = text.substring(from, end);
            return tail.length() == 0 ? "暂无日志" : tail;
        }
    }
}
