package afb.astyann.codegeneration.service;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.github.mustachejava.MustacheResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/**
 * Renders Angular / TypeScript source files from Mustache templates on the classpath. Templates
 * live under {@code src/main/resources/templates/} and are referenced by a path relative to
 * that root, e.g. {@code "frontend/model.ts.mustache"}.
 */
@Service
public class MustacheEngine {

    private final MustacheFactory factory;

    public MustacheEngine() {
        MustacheResolver resolver = name -> {
            try {
                InputStream in = new ClassPathResource("templates/" + name).getInputStream();
                return new InputStreamReader(in, StandardCharsets.UTF_8);
            } catch (Exception ex) {
                return null;
            }
        };
        this.factory = new DefaultMustacheFactory(resolver);
    }

    public String render(String templateName, Object model) {
        try {
            Mustache mustache = factory.compile(templateName);
            StringWriter writer = new StringWriter();
            mustache.execute(writer, model).flush();
            return writer.toString();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to render Mustache template '" + templateName + "': "
                    + ex.getMessage(), ex);
        }
    }
}
