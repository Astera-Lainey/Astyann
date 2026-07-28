package afb.astyann.codegeneration.service.logic;

import afb.astyann.codegeneration.service.logic.NodeRunner.TsErrorRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NodeRunnerTest {

    private final NodeRunner runner = new NodeRunner();

    @Test
    void parsesTscErrorRows() {
        String output = String.join("\n",
                "src/app/foo.component.ts(12,5): error TS2322: Type 'string' is not assignable to type 'number'.",
                "src/app/bar.service.ts(3,10): error TS2304: Cannot find name 'Xyz'.");

        List<TsErrorRow> rows = runner.parseErrors(output);

        assertThat(rows).hasSize(2);
        TsErrorRow first = rows.get(0);
        assertThat(first.file()).isEqualTo("src/app/foo.component.ts");
        assertThat(first.line()).isEqualTo(12);
        assertThat(first.col()).isEqualTo(5);
        assertThat(first.code()).isEqualTo("TS2322");
        assertThat(first.message()).contains("not assignable");
        assertThat(rows.get(1).code()).isEqualTo("TS2304");
    }

    @Test
    void normalisesBackslashPaths() {
        String output = "src\\app\\foo.ts(1,1): error TS1005: ';' expected.";
        List<TsErrorRow> rows = runner.parseErrors(output);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).file()).isEqualTo("src/app/foo.ts");
    }

    @Test
    void ignoresNonErrorLines() {
        String output = String.join("\n",
                "> tsc --noEmit",
                "Found 0 errors.",
                "");
        assertThat(runner.parseErrors(output)).isEmpty();
    }

    @Test
    void formattedRowIsHumanReadable() {
        String output = "src/x.ts(2,3): error TS1: boom";
        assertThat(runner.parseErrors(output).get(0).formatted())
                .isEqualTo("src/x.ts:2:3 TS1 boom");
    }
}
