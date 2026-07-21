package afb.astyann.codegeneration.service;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.util.Map;

/**
 * Renders backend and infrastructure source files from FreeMarker templates on the classpath.
 * Templates live under {@code src/main/resources/templates/} and are referenced by a path
 * relative to that root, e.g. {@code "backend/Entity.java.ftl"}.
 */
@Service
public class FreeMarkerEngine {

    private final Configuration configuration;

    public FreeMarkerEngine() {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
        cfg.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "templates");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        cfg.setFallbackOnNullLoopVariable(false);
        cfg.setNumberFormat("computer");
        this.configuration = cfg;
    }

    public String render(String templateName, Map<String, Object> model) {
        try {
            Template template = configuration.getTemplate(templateName);
            StringWriter writer = new StringWriter();
            template.process(model, writer);
            return writer.toString();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to render FreeMarker template '" + templateName + "': "
                    + ex.getMessage(), ex);
        }
    }
}
