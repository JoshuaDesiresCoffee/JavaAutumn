package Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class Templater {

    public static String render(String templatePath, Map<String, Object> ctx) throws IOException {

        String template = readTemplate(templatePath);
        // actually do parsing
        String rendered = template;
        return rendered;
    }

    public static String readTemplate(String templatePath) throws IOException {
        return Files.readString(Path.of("./templates/" + templatePath));
    }
}
