package afb.astyann.codegeneration.service.logic;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiJsonExtractorTest {

    private final AiJsonExtractor extractor = new AiJsonExtractor();

    @Test
    void readsWellFormedStringField() {
        String raw = "{\"serviceImpl\": \"package a;\\nclass B {}\", \"notes\": \"done\"}";
        assertThat(extractor.stringField(raw, "serviceImpl")).contains("package a;\nclass B {}");
        assertThat(extractor.stringField(raw, "notes")).contains("done");
    }

    @Test
    void recoversFieldEvenWhenAnotherFieldIsMalformed() {
        // The "controller" value contains a raw unescaped quote, which makes the document as a
        // whole invalid — "serviceImpl" must still be readable.
        String raw = """
                {
                  "serviceImpl": "package a;\\nclass Good {}",
                  "controller": "broken " quote here",
                  "notes": "x"
                }""";
        assertThat(extractor.stringField(raw, "serviceImpl")).contains("package a;\nclass Good {}");
    }

    @Test
    void salvagesUnterminatedFinalString() {
        String raw = "{\"serviceImpl\": \"package a;\\nclass Cut {";
        assertThat(extractor.stringField(raw, "serviceImpl")).contains("package a;\nclass Cut {");
    }

    @Test
    void keepsUnknownEscapesVerbatim() {
        // \d is not a JSON escape but appears in regexes inside generated code.
        String raw = "{\"serviceImpl\": \"m.matches(\\\"\\d+\\\")\"}";
        assertThat(extractor.stringField(raw, "serviceImpl")).contains("m.matches(\"d+\")");
    }

    @Test
    void decodesUnicodeEscapes() {
        String raw = "{\"notes\": \"self\\u2011deactivation\"}";
        assertThat(extractor.stringField(raw, "notes")).contains("self‑deactivation");
    }

    @Test
    void readsAdditionalFilesMap() {
        String raw = """
                {
                  "serviceImpl": "x",
                  "additionalFiles": {
                    "enums/UserRole.java": "package a;\\npublic enum UserRole { ADMIN }",
                    "enums/UserStatus.java": "package a;\\npublic enum UserStatus { ACTIVE }"
                  },
                  "notes": "n"
                }""";
        Map<String, String> files = extractor.stringMapField(raw, "additionalFiles");
        assertThat(files).hasSize(2)
                .containsKeys("enums/UserRole.java", "enums/UserStatus.java");
        assertThat(files.get("enums/UserRole.java")).contains("public enum UserRole");
    }

    @Test
    void missingFieldYieldsEmpty() {
        String raw = "{\"serviceImpl\": \"x\"}";
        assertThat(extractor.stringField(raw, "repository")).isEmpty();
        assertThat(extractor.stringMapField(raw, "additionalFiles")).isEmpty();
    }

    @Test
    void ignoresKeyThatIsNotFollowedByColon() {
        // The word "notes" appearing inside a code string must not be mistaken for the key.
        String raw = "{\"serviceImpl\": \"log(\\\"notes\\\")\", \"notes\": \"real\"}";
        assertThat(extractor.stringField(raw, "notes")).contains("real");
    }

    @Test
    void handlesNullInputSafely() {
        assertThat(extractor.stringField(null, "x")).isEmpty();
        assertThat(extractor.stringMapField("{}", null)).isEmpty();
    }
}
