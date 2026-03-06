package Service.templating;

import java.io.IOException;

public interface TemplateReader {
    String readTemplate(String templatePath) throws IOException;
}
