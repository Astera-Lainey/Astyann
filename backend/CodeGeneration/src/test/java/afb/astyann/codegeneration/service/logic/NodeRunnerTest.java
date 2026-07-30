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
    void parsesAngularEsbuildTemplateError() {
        // The exact shape `ng build` emits — and precisely the error that a bare `tsc --noEmit`
        // cannot see, because it lives in a component template rather than TypeScript.
        String output = String.join("\n",
                "✘ [ERROR] NG8001: 'iconify-icon' is not a known element:",
                "1. If 'iconify-icon' is an Angular component, then verify that it is part of this module.",
                " [plugin angular-compiler]",
                "",
                "    src/app/layout/sidebar/sidebar.component.html:6:8:",
                "      6 │         <iconify-icon [icon]=\"item.icon\"></iconify-icon>",
                "        ╵         ~~~~~~~~~~~~~");

        List<TsErrorRow> rows = runner.parseErrors(output);

        assertThat(rows).hasSize(1);
        TsErrorRow row = rows.get(0);
        assertThat(row.file()).isEqualTo("src/app/layout/sidebar/sidebar.component.html");
        assertThat(row.line()).isEqualTo(6);
        assertThat(row.col()).isEqualTo(8);
        assertThat(row.code()).isEqualTo("NG8001");
        assertThat(row.message()).contains("is not a known element");
    }

    @Test
    void parsesAngularEsbuildTypeErrorAndStripsPluginSuffix() {
        String output = String.join("\n",
                "✘ [ERROR] TS2322: Type 'string' is not assignable to type 'number'. [plugin angular-compiler]",
                "",
                "    src/app/features/product/form/product-form.component.ts:31:6:");

        List<TsErrorRow> rows = runner.parseErrors(output);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).code()).isEqualTo("TS2322");
        assertThat(rows.get(0).message()).doesNotContain("plugin angular-compiler");
        assertThat(rows.get(0).file()).endsWith("product-form.component.ts");
    }

    @Test
    void ignoresAngularErrorHeaderWithNoLocation() {
        String output = "✘ [ERROR] Something went wrong with no file reference";
        assertThat(runner.parseErrors(output)).isEmpty();
    }

    @Test
    void formattedRowIsHumanReadable() {
        String output = "src/x.ts(2,3): error TS1: boom";
        assertThat(runner.parseErrors(output).get(0).formatted())
                .isEqualTo("src/x.ts:2:3 TS1 boom");
    }
}
