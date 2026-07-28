package afb.astyann.codegeneration.service.logic;

import afb.astyann.codegeneration.service.logic.MavenRunner.CompileErrorRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MavenRunnerTest {

    private final MavenRunner runner = new MavenRunner();

    @Test
    void parsesMavenErrorRowsAndFoldsSymbolContinuation() {
        String output = String.join("\n",
                "[INFO] Building app 0.0.1",
                "[ERROR] /C:/proj/src/main/java/com/x/Foo.java:[12,20] cannot find symbol",
                "  symbol:   class Bar",
                "  location: class com.x.Foo",
                "[INFO] BUILD FAILURE");

        List<CompileErrorRow> rows = runner.parseErrors(output);

        assertThat(rows).hasSize(1);
        CompileErrorRow row = rows.get(0);
        assertThat(row.file().getFileName().toString()).isEqualTo("Foo.java");
        assertThat(row.line()).isEqualTo(12);
        assertThat(row.col()).isEqualTo(20);
        assertThat(row.message()).contains("cannot find symbol").contains("symbol:").contains("Bar");
    }

    @Test
    void stripsWindowsLeadingSlashFromDrivePath() {
        String output = "[ERROR] /C:/proj/src/main/java/com/x/Baz.java:[3,5] bad";
        List<CompileErrorRow> rows = runner.parseErrors(output);
        assertThat(rows).hasSize(1);
        // The /C:/ form must not blow up Path.of and must still yield the file name.
        assertThat(rows.get(0).file().getFileName().toString()).isEqualTo("Baz.java");
    }

    @Test
    void parsesBareJavacErrorRows() {
        String output = "/home/u/proj/src/main/java/com/x/Qux.java:8: error: incompatible types: String cannot be converted to int";
        List<CompileErrorRow> rows = runner.parseErrors(output);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).file().getFileName().toString()).isEqualTo("Qux.java");
        assertThat(rows.get(0).line()).isEqualTo(8);
        assertThat(rows.get(0).message()).contains("incompatible types");
    }

    @Test
    void emptyOrBlankOutputYieldsNoRows() {
        assertThat(runner.parseErrors(null)).isEmpty();
        assertThat(runner.parseErrors("   ")).isEmpty();
        assertThat(runner.parseErrors("[INFO] all good")).isEmpty();
    }

    @Test
    void extractFailureSummaryKeepsBuildFailureLines() {
        String output = String.join("\n",
                "[INFO] compiling",
                "[ERROR] Failed to execute goal on project app: Could not resolve dependencies",
                "[INFO] BUILD FAILURE",
                "[ERROR] -> [Help 1]");
        String summary = runner.extractFailureSummary(output);
        assertThat(summary).contains("BUILD FAILURE").contains("Failed to execute goal");
    }

    @Test
    void formattedRowIsHumanReadable() {
        String output = "[ERROR] /p/src/main/java/A.java:[2,3] oops";
        CompileErrorRow row = runner.parseErrors(output).get(0);
        assertThat(row.formatted()).isEqualTo("A.java:2:3 oops");
    }
}
