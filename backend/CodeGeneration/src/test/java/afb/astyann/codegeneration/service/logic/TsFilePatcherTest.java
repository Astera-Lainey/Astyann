package afb.astyann.codegeneration.service.logic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The TypeScript/HTML write path has no parser behind it — unlike the Java path, which validates
 * with JavaParser — so these heuristics are the only protection against a mangled model response
 * overwriting a good source file.
 */
class TsFilePatcherTest {

    private final TsFilePatcher patcher = new TsFilePatcher();

    private Path write(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private static final String COMPONENT = """
            import { Component } from '@angular/core';

            @Component({ selector: 'app-x', template: '' })
            export class XComponent {
              readonly value = 1;
              doThing(): void { console.log('thing'); }
            }
            """;

    private static final String TEMPLATE = """
            <div class="page">
              @if (loading()) { <span>Loading</span> }
              @for (row of rows(); track row.id) { <p>{{ row.name }}</p> }
            </div>
            """;

    @Test
    void writesAValidReplacement(@TempDir Path dir) throws IOException {
        Path file = write(dir, "x.component.ts", COMPONENT);
        String updated = COMPONENT.replace("readonly value = 1;", "readonly value = 2;");

        assertThat(patcher.writeIfSane(file, updated, COMPONENT)).isTrue();
        assertThat(Files.readString(file)).contains("readonly value = 2;");
    }

    @Test
    void rejectsEmptyOrFenceOnlyResponses(@TempDir Path dir) throws IOException {
        Path file = write(dir, "x.component.ts", COMPONENT);

        assertThat(patcher.writeIfSane(file, "", COMPONENT)).isFalse();
        assertThat(patcher.writeIfSane(file, null, COMPONENT)).isFalse();
        assertThat(patcher.writeIfSane(file, "```ts\n```", COMPONENT)).isFalse();
        assertThat(Files.readString(file)).isEqualTo(COMPONENT);
    }

    @Test
    void rejectsTruncatedResponses(@TempDir Path dir) throws IOException {
        Path file = write(dir, "x.component.ts", COMPONENT);

        // A response that collapsed to a fraction of the original is almost always truncation.
        assertThat(patcher.writeIfSane(file, "import { Component } from '@angular/core';", COMPONENT)).isFalse();
        assertThat(Files.readString(file)).isEqualTo(COMPONENT);
    }

    @Test
    void rejectsAComponentClassReturnedForATemplate(@TempDir Path dir) throws IOException {
        // The exact failure the old code invited: template errors report a .html location, but the
        // TypeScript prompt asked for a component class.
        Path file = write(dir, "x.component.html", TEMPLATE);

        assertThat(patcher.writeIfSane(file, COMPONENT, TEMPLATE)).isFalse();
        assertThat(Files.readString(file)).isEqualTo(TEMPLATE);
    }

    @Test
    void rejectsMarkupReturnedForATypeScriptFile(@TempDir Path dir) throws IOException {
        Path file = write(dir, "x.component.ts", COMPONENT);

        assertThat(patcher.writeIfSane(file, TEMPLATE, COMPONENT)).isFalse();
        assertThat(Files.readString(file)).isEqualTo(COMPONENT);
    }

    @Test
    void acceptsAValidTemplateReplacement(@TempDir Path dir) throws IOException {
        Path file = write(dir, "x.component.html", TEMPLATE);
        String fixed = TEMPLATE.replace("<span>Loading</span>", "<span>Please wait</span>");

        assertThat(patcher.writeIfSane(file, fixed, TEMPLATE)).isTrue();
        assertThat(Files.readString(file)).contains("Please wait");
    }

    @Test
    void stripsCodeFencesFromOtherwiseValidOutput(@TempDir Path dir) throws IOException {
        Path file = write(dir, "x.component.html", TEMPLATE);

        assertThat(patcher.writeIfSane(file, "```html\n" + TEMPLATE + "\n```", TEMPLATE)).isTrue();
        assertThat(Files.readString(file)).doesNotContain("```").contains("<div class=\"page\">");
    }
}
