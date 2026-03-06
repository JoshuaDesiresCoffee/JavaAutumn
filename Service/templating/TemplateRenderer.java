package Service.templating;

import java.io.IOException;
import java.util.Map;

public interface TemplateRenderer {
    String renderTemplate(String templatePath, Map<String, ?> context) throws IOException;
}
