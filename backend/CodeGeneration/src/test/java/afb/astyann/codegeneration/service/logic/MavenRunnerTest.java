package afb.astyann.codegeneration.service.logic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies MavenRunner's OS-aware candidate resolution — the fix for the Windows
 * {@code CreateProcess error=2} bug where {@code new ProcessBuilder("mvn", ...)} fails because
 * Java doesn't do PATHEXT resolution and the real command is {@code mvn.cmd}.
 */
class MavenRunnerTest {

    private final MavenRunner runner = new MavenRunner();

    @Test
    @SuppressWarnings("unchecked")
    void windows_defaults_include_mvn_cmd_and_mvn_bat() {
        ReflectionTestUtils.setField(runner, "mvnCommandOverride", "");
        List<String> candidates = invokeCandidates(null);
        // We can't be sure what OS this test runs on, so check by dispatch.
        boolean isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        if (isWindows) {
            assertThat(candidates).containsSequence("mvn.cmd", "mvn.bat", "mvn");
        } else {
            assertThat(candidates).containsExactly("mvn");
        }
    }

    @Test
    void explicit_override_takes_first_slot() {
        ReflectionTestUtils.setField(runner, "mvnCommandOverride", "C:/opt/maven/bin/mvn.cmd");
        List<String> candidates = invokeCandidates(null);
        assertThat(candidates.get(0)).isEqualTo("C:/opt/maven/bin/mvn.cmd");
        assertThat(candidates).hasSizeGreaterThan(1);
    }

    @Test
    void blank_or_default_override_falls_through_to_os_defaults() {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

        ReflectionTestUtils.setField(runner, "mvnCommandOverride", "mvn");
        List<String> candidates = invokeCandidates(null);
        // The literal "mvn" override should NOT appear at position 0 — it must fall through
        // to the OS-aware defaults where mvn.cmd (Windows) or mvn (Unix) leads.
        if (isWindows) {
            assertThat(candidates.get(0)).isEqualTo("mvn.cmd");
        } else {
            assertThat(candidates.get(0)).isEqualTo("mvn");
            assertThat(candidates).hasSize(1);
        }

        ReflectionTestUtils.setField(runner, "mvnCommandOverride", "");
        candidates = invokeCandidates(null);
        assertThat(candidates).isNotEmpty();
    }

    @Test
    void project_local_wrapper_is_probed_before_shared_defaults(@TempDir Path project) throws IOException {
        ReflectionTestUtils.setField(runner, "mvnCommandOverride", "");
        boolean isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        String wrapperName = isWindows ? "mvnw.cmd" : "mvnw";
        Path wrapper = project.resolve(wrapperName);
        Files.writeString(wrapper, "@echo off\necho fake wrapper");
        wrapper.toFile().setExecutable(true);

        List<String> candidates = invokeCandidates(project);
        assertThat(candidates.get(0)).endsWith(wrapperName);
    }

    @SuppressWarnings("unchecked")
    private List<String> invokeCandidates(Path projectDir) {
        return (List<String>) ReflectionTestUtils.invokeMethod(runner, "candidates", projectDir);
    }

    // ── parseErrors ────────────────────────────────────────────────────────
    // Regression tests using the EXACT format observed from Maven 3.9+ against a
    // generated backend project. If these ever regress, the auto-correction loop
    // silently degrades to "no parseable errors" and never asks the AI to fix.

    @Test
    void parseErrors_matches_maven_compile_plugin_row_with_windows_path() {
        // Exact format Maven emits on Windows — leading slash before the drive letter.
        // Previously Path.of("/C:/...") threw InvalidPathException and every row was
        // silently dropped, so the AI fix loop never ran.
        String output = String.join("\n",
                "[INFO] BUILD FAILURE",
                "[ERROR] /C:/Users/AAPH~1/AppData/Local/Temp/astyann-validate-abc/src/main/java/com/example/Foo.java:[43,68] <identifier> expected",
                "[ERROR] /C:/Users/AAPH~1/AppData/Local/Temp/astyann-validate-abc/src/main/java/com/example/Foo.java:[43,69] ';' expected",
                "[ERROR] -> [Help 1]");
        List<MavenRunner.CompileErrorRow> rows = runner.parseErrors(output);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).line()).isEqualTo(43);
        assertThat(rows.get(0).col()).isEqualTo(68);
        assertThat(rows.get(0).message()).isEqualTo("<identifier> expected");
        assertThat(rows.get(0).file().getFileName().toString()).isEqualTo("Foo.java");
        // Leading slash stripped so Path.of accepts it on Windows
        assertThat(rows.get(0).file().toString()).doesNotStartWith("/C:");
        assertThat(rows.get(0).file().isAbsolute()).isTrue();
    }

    @Test
    void parseErrors_folds_cannot_find_symbol_continuation() {
        String output = String.join("\n",
                "[ERROR] /C:/tmp/src/main/java/com/example/Foo.java:[41,64] cannot find symbol",
                "  symbol:   class GoodsReceivedDto",
                "  location: class com.example.Foo",
                "[ERROR] /C:/tmp/src/main/java/com/example/Foo.java:[47,63] cannot find symbol",
                "  symbol:   class GoodsDispatchedDto",
                "  location: class com.example.Foo");
        List<MavenRunner.CompileErrorRow> rows = runner.parseErrors(output);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).message()).contains("GoodsReceivedDto");
        assertThat(rows.get(1).message()).contains("GoodsDispatchedDto");
    }
}
