import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import wex.ui.core.WexStore;
import wex.ui.core.feature.LocationPrivacy;
import wex.ui.core.feature.LogPreview;

public final class RegressionChecks {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "--arguments".equals(args[0])) {
            for (int i = 1; i < args.length; i++) {
                System.out.println(Base64.getEncoder().encodeToString(args[i].getBytes(StandardCharsets.UTF_8)));
            }
            return;
        }
        File dir = new File(args[0]);
        File log = new File(dir, "preview.log");
        check("暂无日志".equals(LogPreview.read(log)), "missing log");
        Files.write(log.toPath(), new byte[0]);
        check("暂无日志".equals(LogPreview.read(log)), "empty log");
        Files.writeString(log.toPath(), "中文日志\r\n下一行", StandardCharsets.UTF_8);
        check("中文日志\r\n下一行".equals(LogPreview.read(log)), "UTF-8 and CRLF");
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 250; i++) text.append("line-").append(i).append('\n');
        Files.writeString(log.toPath(), text, StandardCharsets.UTF_8);
        String result = LogPreview.read(log);
        check(result.startsWith("line-50\n"), "last 200 lines start");
        check(result.endsWith("line-249"), "last 200 lines end");
        check(result.split("\n", -1).length == 200, "line bound");
        Files.writeString(log.toPath(), text.substring(0, text.length() - 1), StandardCharsets.UTF_8);
        check(result.equals(LogPreview.read(log)), "no final newline");
        String big = "旧日志".repeat(30000) + "\n末尾中文\n";
        Files.writeString(log.toPath(), big, StandardCharsets.UTF_8);
        check("末尾中文".equals(LogPreview.read(log)), "discard partial UTF-8 line");
        Files.writeString(log.toPath(), "x".repeat(100000), StandardCharsets.UTF_8);
        check(LogPreview.read(log).contains("单行过长"), "bounded long line");
        boolean failed = false;
        try { LogPreview.read(dir); } catch (java.io.IOException expected) { failed = true; }
        check(failed, "read error propagated");
        check(!LocationPrivacy.isEnabled(), "default network consent is false");
        WexStore.INSTANCE.enabled = true;
        check(LocationPrivacy.isEnabled(), "explicit consent");
        WexStore.INSTANCE.enabled = false;
        check(!LocationPrivacy.isEnabled(), "disabled consent");
        WexStore.INSTANCE.fail = true;
        check(!LocationPrivacy.isEnabled(), "store failure denies network");
        System.out.println("PASS: bounded log preview and network consent");
    }
}
