package cn.lunadeer.deertitle.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Guards the native Paper input-name contract that only the real server provider enforces. */
class DialogInputContractTest {
    @Test void nativeInputNamesAreValidAndResponseReadersMatchDeclarations() throws Exception {
        String source = Files.readString(Path.of("src/main/java/cn/lunadeer/deertitle/ui/DialogUi.java"));
        Set<String> declared = matches(source,
            "(?:\\binput|DialogInput\\.(?:text|bool|singleOption|numberRange))\\(\"([^\"]+)\"");
        Set<String> read = matches(source, "response\\.get(?:Text|Boolean|Float)\\(\"([^\"]+)\"");
        assertFalse(declared.isEmpty(), "No native dialog inputs were inspected");
        for (String key : declared) {
            assertTrue(key.matches("[a-zA-Z_][a-zA-Z0-9_]*"), "Paper rejects native input name: " + key);
        }
        assertTrue(declared.containsAll(read), "A dialog response reader has no matching input declaration");
    }

    private static Set<String> matches(String source, String expression) {
        Set<String> result = new HashSet<>();
        var matcher = Pattern.compile(expression).matcher(source);
        while (matcher.find()) result.add(matcher.group(1));
        return result;
    }
}
