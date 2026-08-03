package afb.astyann.codegeneration.service.logic;

import afb.astyann.codegeneration.service.logic.NodeRunner.TsErrorRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NodeRunnerTest {

    private final NodeRunner runner = new NodeRunner();

    /** Verbatim from a real run: memfs@4.66.0 pinned a sibling that was never published. */
    @Test
    void classifiesAnUnpublishedTransitiveVersionAsADependencyProblem() {
        String output = String.join("\n",
                "npm error code ETARGET",
                "npm error notarget No matching version found for @jsonjoy.com/fs-node@4.66.0.",
                "npm error notarget In most cases you or one of your dependencies are requesting",
                "npm error A complete log of this run can be found in: C:\\Users\\x\\_logs\\debug-0.log");

        var failure = runner.classifyInstallFailure(output);

        assertThat(failure).isPresent();
        assertThat(failure.get().npmCode()).isEqualTo("ETARGET");
        assertThat(failure.get().packageName()).isEqualTo("@jsonjoy.com/fs-node");
        assertThat(failure.get().dependencyProblem()).isTrue();
    }

    @Test
    void classifiesAPackageMissingFromTheRegistry() {
        // The shape of the earlier @iconify/angular failure — a name the generator itself emitted.
        String output = String.join("\n",
                "npm error code E404",
                "npm error 404 Not Found - GET https://registry.npmjs.org/@iconify%2fangular",
                "npm error 404  '@iconify/angular@^2.0.0' is not in this registry.");

        var failure = runner.classifyInstallFailure(output);

        assertThat(failure).isPresent();
        assertThat(failure.get().npmCode()).isEqualTo("E404");
        assertThat(failure.get().packageName()).isEqualTo("@iconify/angular");
        assertThat(failure.get().dependencyProblem()).isTrue();
    }

    @Test
    void treatsNonResolutionFailuresAsSomethingElse() {
        // A lifecycle-script failure is the project's own problem and must not be explained away
        // as an upstream registry hiccup.
        var failure = runner.classifyInstallFailure(String.join("\n",
                "npm error code ELIFECYCLE",
                "npm error errno 1",
                "npm error postinstall script failed"));

        assertThat(failure).isPresent();
        assertThat(failure.get().npmCode()).isEqualTo("ELIFECYCLE");
        assertThat(failure.get().dependencyProblem()).isFalse();
    }

    @Test
    void returnsEmptyWhenThereIsNoRecognisableNpmErrorCode() {
        assertThat(runner.classifyInstallFailure("something exploded")).isEmpty();
        assertThat(runner.classifyInstallFailure("")).isEmpty();
        assertThat(runner.classifyInstallFailure(null)).isEmpty();
    }

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
