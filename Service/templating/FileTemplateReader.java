package Service.templating;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileTemplateReader implements TemplateReader {

    private final Path templateRoot;

    public FileTemplateReader() {
        this(Path.of("templates"));
    }

    public FileTemplateReader(Path templateRoot) {
        this.templateRoot = templateRoot;
    }

    @Override
    public String readTemplate(String templatePath) throws IOException {
        Path resolvedPath = templateRoot.resolve(templatePath).normalize();
        if (!resolvedPath.startsWith(templateRoot.normalize())) {
            throw new IllegalArgumentException("Template path escapes template root: " + templatePath);
        }

        return Files.readString(resolvedPath);
    }
}
