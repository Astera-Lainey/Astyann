package afb.astyann.codegeneration.service.logic;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs Maven against a generated backend project directory and parses the compiler output into
 * structured records. {@link #compile(Path)} runs {@code test-compile} so that both main and test
 * sources are checked; {@link #test(Path)} then runs the suite itself.
 *
 * <p>Command resolution is OS-aware: Java's {@code ProcessBuilder} on Windows does not do
 * {@code PATHEXT} resolution, so {@code new ProcessBuilder("mvn", ...)} fails with
 * {@code CreateProcess error=2} even when Maven is properly installed as {@code mvn.cmd}. To
 * fix that this runner probes a list of candidates in order:
 * <ol>
 *   <li>{@code codegen.validate.compile.mvn-command} if the user set an explicit override,</li>
 *   <li>The project-local Maven wrapper ({@code mvnw.cmd} on Windows, {@code mvnw} elsewhere)
 *       if it exists in the working dir passed to {@link #compile(Path)},</li>
 *   <li>OS-aware defaults — {@code mvn.cmd}, {@code mvn.bat}, {@code mvn} on Windows;
 *       {@code mvn} on Linux/macOS.</li>
 * </ol>
 * The first candidate that responds to {@code -v} within 5 seconds wins and is cached for the
 * lifetime of the process.
 */
@Service
@Slf4j
public class MavenRunner {

    /** Matches the Maven compile-error format: {@code [ERROR] /abs/path/File.java:[l,c] msg}. */
    private static final Pattern ERROR_LINE = Pattern.compile(
            "\\[ERROR]\\s+(.+?\\.java):\\[(\\d+),(\\d+)]\\s*(.*)");

    /** Fallback javac output when Maven prints without the {@code [ERROR]} prefix. */
    private static final Pattern JAVAC_LINE = Pattern.compile(
            "^\\s*(.+?\\.java):(\\d+):\\s*error:\\s*(.*)");

    /** Blank / "mvn" both mean "use OS defaults" — otherwise treat as explicit override. */
    @Value("${codegen.validate.compile.mvn-command:}")
    private String mvnCommandOverride;

    private volatile String cachedCommand;

    // ── Public API ─────────────────────────────────────────────────────────

    public boolean isAvailable() {
        return isAvailable(null);
    }

    /**
     * Whether at least one candidate resolves to a working Maven install, considering the
     * project-local wrapper in {@code projectDir} (if any).
     */
    public boolean isAvailable(Path projectDir) {
        return findWorkingCommand(projectDir).isPresent();
    }

    public CompileResult compile(Path projectDir) throws IOException, InterruptedException {
        String cmd = findWorkingCommand(projectDir).orElseThrow(() -> new IOException(
                "No usable Maven command found. Tried: " + describeCandidates(projectDir) +
                ". Configure codegen.validate.compile.mvn-command to an absolute path if needed."));

        List<String> argv = new ArrayList<>();
        argv.add(cmd);
        // Deliberately NOT passing -q — Maven's quiet mode suppresses the compile-plugin
        // error rows that our parser depends on, which is what caused the
        // "no error rows were parseable" result in the wild.
        argv.add("--batch-mode");
        argv.add("-Dstyle.color=never");
        // `test-compile`, not `compile`: the generated project ships tests, and a test source that
        // does not compile is an unambiguous compile error the AI fix loop can repair. Compiling
        // only main sources let those errors escape to `mvn test`, where they surfaced as
        // "test phase failed before any test ran" — reported but never fixed.
        argv.add("test-compile");

        ProcessBuilder pb = new ProcessBuilder(argv)
                .directory(projectDir.toFile())
                .redirectErrorStream(true);
        Process process = pb.start();
        String output;
        try (var in = process.getInputStream()) {
            output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        boolean finished = process.waitFor(10, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("mvn compile timed out after 10 minutes");
        }
        int exit = process.exitValue();
        // Always log the full output at DEBUG. On failure, log a tail at WARN so it's
        // visible to operators without having to bump the log level.
        if (log.isDebugEnabled()) log.debug("mvn compile output ({} bytes):\n{}", output.length(), output);
        if (exit != 0) log.warn("mvn compile failed (exit={}). Last {} lines:\n{}",
                exit, MAX_TAIL_LINES, tail(output, MAX_TAIL_LINES));
        return new CompileResult(exit, output);
    }

    /**
     * Runs {@code mvn test} against the generated project. Uses the same command resolution as
     * {@link #compile(Path)}; {@code -DfailIfNoTests=false} keeps a project without tests from
     * failing the run outright.
     */
    public CompileResult test(Path projectDir) throws IOException, InterruptedException {
        String cmd = findWorkingCommand(projectDir).orElseThrow(() -> new IOException(
                "No usable Maven command found. Tried: " + describeCandidates(projectDir)));

        List<String> argv = new ArrayList<>();
        argv.add(cmd);
        argv.add("--batch-mode");
        argv.add("-Dstyle.color=never");
        argv.add("-DfailIfNoTests=false");
        argv.add("test");

        ProcessBuilder pb = new ProcessBuilder(argv)
                .directory(projectDir.toFile())
                .redirectErrorStream(true);
        Process process = pb.start();
        String output;
        try (var in = process.getInputStream()) {
            output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        boolean finished = process.waitFor(15, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("mvn test timed out after 15 minutes");
        }
        int exit = process.exitValue();
        if (log.isDebugEnabled()) log.debug("mvn test output ({} bytes):\n{}", output.length(), output);
        if (exit != 0) log.warn("mvn test failed (exit={}). Last {} lines:\n{}",
                exit, MAX_TAIL_LINES, tail(output, MAX_TAIL_LINES));
        return new CompileResult(exit, output);
    }

    /** Surefire's roll-up line, e.g. {@code Tests run: 7, Failures: 1, Errors: 0, Skipped: 0}. */
    private static final Pattern TEST_SUMMARY = Pattern.compile(
            "Tests run:\\s*(\\d+),\\s*Failures:\\s*(\\d+),\\s*Errors:\\s*(\\d+),\\s*Skipped:\\s*(\\d+)");

    /** Individual failure rows Surefire prints under {@code [ERROR] Failures:} / {@code Errors:}. */
    private static final Pattern TEST_FAILURE_ROW = Pattern.compile(
            "^\\[ERROR]\\s{2,}(\\S+?\\.\\S+?)\\s*[:»](.*)");

    /**
     * Extracts the totals from a {@code mvn test} run. Surefire prints one summary line per module
     * plus a final roll-up; the LAST match is the roll-up, so that is the one returned.
     */
    public TestSummary parseTestSummary(String output) {
        if (output == null || output.isBlank()) return new TestSummary(0, 0, 0, 0, List.of());
        Matcher m = TEST_SUMMARY.matcher(output);
        int run = 0, failures = 0, errors = 0, skipped = 0;
        boolean found = false;
        while (m.find()) {
            run = Integer.parseInt(m.group(1));
            failures = Integer.parseInt(m.group(2));
            errors = Integer.parseInt(m.group(3));
            skipped = Integer.parseInt(m.group(4));
            found = true;
        }
        if (!found) return new TestSummary(0, 0, 0, 0, List.of());

        List<String> failed = new ArrayList<>();
        for (String line : output.split("\\r?\\n")) {
            Matcher f = TEST_FAILURE_ROW.matcher(line.trim());
            if (f.matches()) {
                String name = f.group(1);
                String detail = f.group(2).trim();
                String row = detail.isEmpty() ? name : name + " — " + detail;
                if (!failed.contains(row)) failed.add(row);
            }
        }
        return new TestSummary(run, failures, errors, skipped, failed);
    }

    public List<CompileErrorRow> parseErrors(String output) {
        List<CompileErrorRow> out = new ArrayList<>();
        if (output == null || output.isBlank()) return out;
        String[] lines = output.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            Matcher m = ERROR_LINE.matcher(trimmed);
            if (m.matches()) {
                Path file = toPath(m.group(1));
                if (file != null) {
                    String message = m.group(4).trim();
                    // javac often puts "symbol: class Foo" on the next indented line —
                    // fold it into the message so the AI sees WHICH symbol is missing.
                    message = appendContinuation(message, lines, i + 1);
                    out.add(new CompileErrorRow(
                            file,
                            Integer.parseInt(m.group(2)),
                            Integer.parseInt(m.group(3)),
                            message));
                }
                continue;
            }
            Matcher j = JAVAC_LINE.matcher(trimmed);
            if (j.matches()) {
                Path file = toPath(j.group(1));
                if (file != null) {
                    String message = j.group(3).trim();
                    message = appendContinuation(message, lines, i + 1);
                    out.add(new CompileErrorRow(
                            file,
                            Integer.parseInt(j.group(2)),
                            1,
                            message));
                }
            }
        }
        return out;
    }

    /** Folds javac's indented "symbol:" / "location:" continuation lines into the message. */
    private static String appendContinuation(String message, String[] lines, int start) {
        StringBuilder sb = new StringBuilder(message);
        for (int i = start; i < lines.length && i < start + 3; i++) {
            String next = lines[i].trim();
            if (next.startsWith("symbol:") || next.startsWith("location:")
                    || next.startsWith("required:") || next.startsWith("found:")) {
                sb.append(" | ").append(next);
            } else {
                break;
            }
        }
        return sb.toString();
    }

    /**
     * Maven on Windows emits paths like {@code /C:/Users/.../Foo.java} (leading slash before
     * the drive letter). {@link Path#of(String, String...)} rejects that with
     * {@code InvalidPathException: Illegal char <:>}, which previously made every parsed
     * error row vanish into a silent catch — so the AI fix loop never ran.
     */
    private Path toPath(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String normalized = raw.trim();
        // Strip Maven's /C:/… form down to C:/…
        if (normalized.length() >= 3
                && normalized.charAt(0) == '/'
                && Character.isLetter(normalized.charAt(1))
                && normalized.charAt(2) == ':') {
            normalized = normalized.substring(1);
        }
        try {
            return Path.of(normalized);
        } catch (Exception ex) {
            log.warn("Could not resolve compile-error path '{}': {}", raw, ex.getMessage());
            return null;
        }
    }

    /**
     * Best-effort short summary of a compile failure when {@link #parseErrors(String)} yields
     * nothing — typically because Maven itself failed before the compiler ran (bad POM,
     * missing plugin, dependency resolution error). Returns the "Failed to execute goal…"
     * chain plus BUILD FAILURE context, or the last {@value #MAX_TAIL_LINES} lines as a
     * fallback.
     */
    public String extractFailureSummary(String output) {
        if (output == null || output.isBlank()) return "(empty maven output)";
        List<String> summary = new ArrayList<>();
        for (String line : output.split("\\r?\\n")) {
            String t = line.trim();
            if (t.contains("BUILD FAILURE")
                    || t.startsWith("[ERROR] Failed to execute goal")
                    || t.startsWith("[ERROR] Non-resolvable")
                    || t.startsWith("[ERROR] Malformed POM")
                    || t.startsWith("[FATAL]")
                    || t.startsWith("[ERROR] -> [Help")
                    || t.startsWith("[ERROR] Re-run Maven")
                    // Include the actual compiler rows so remainingIssues stays useful
                    // even if Path.of somehow rejects a path shape we haven't seen yet.
                    || (t.startsWith("[ERROR]") && t.contains(".java:["))) {
                summary.add(t);
            }
        }
        return summary.isEmpty() ? tail(output, MAX_TAIL_LINES) : String.join("\n", summary);
    }

    /** Returns the last {@code n} non-empty lines of {@code output}, joined by \n. */
    public String tail(String output, int n) {
        if (output == null) return "";
        String[] lines = output.split("\\r?\\n");
        int start = Math.max(0, lines.length - n);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < lines.length; i++) sb.append(lines[i]).append('\n');
        return sb.toString();
    }

    private static final int MAX_TAIL_LINES = 40;

    public String readContextAround(Path file, int line, int radius) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        int start = Math.max(0, line - radius - 1);
        int end = Math.min(lines.size(), line + radius);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) sb.append(i + 1).append(": ").append(lines.get(i)).append('\n');
        return sb.toString();
    }

    // ── Candidate resolution ───────────────────────────────────────────────

    private Optional<String> findWorkingCommand(Path projectDir) {
        // 1. Cached hit from an earlier probe — refuse if it looks like a wrapper for a
        //    different project (wrappers are absolute paths, so bind them per-call).
        String cached = cachedCommand;
        if (cached != null && !looksLikeWrapper(cached) && tryCommand(cached)) return Optional.of(cached);

        for (String candidate : candidates(projectDir)) {
            if (tryCommand(candidate)) {
                cacheIfShared(candidate);
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private List<String> candidates(Path projectDir) {
        List<String> out = new ArrayList<>();
        // (1) explicit override
        if (mvnCommandOverride != null && !mvnCommandOverride.isBlank()
                && !"mvn".equals(mvnCommandOverride.trim())) {
            out.add(mvnCommandOverride.trim());
        }
        // (2) project-local wrapper
        if (projectDir != null) {
            Path wrapper = projectDir.resolve(isWindows() ? "mvnw.cmd" : "mvnw");
            if (Files.exists(wrapper)) out.add(wrapper.toAbsolutePath().toString());
        }
        // (3) OS-aware defaults
        if (isWindows()) {
            out.add("mvn.cmd");
            out.add("mvn.bat");
            out.add("mvn");
        } else {
            out.add("mvn");
        }
        return out;
    }

    private String describeCandidates(Path projectDir) {
        return String.join(", ", candidates(projectDir));
    }

    private boolean tryCommand(String cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd, "-v").redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return false;
            }
            if (p.exitValue() != 0) return false;
            // Optional sanity check on the version banner — should contain "Apache Maven"
            try (var in = p.getInputStream()) {
                String banner = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                return banner.contains("Apache Maven") || banner.contains("Maven ");
            } catch (IOException ignored) {
                return true; // exit code 0 already suggests success
            }
        } catch (Exception ex) {
            log.debug("mvn candidate '{}' unavailable: {}", cmd, ex.getMessage());
            return false;
        }
    }

    private void cacheIfShared(String candidate) {
        // Only cache the shared-PATH candidates, not per-project wrappers.
        if (!looksLikeWrapper(candidate)) cachedCommand = candidate;
    }

    private boolean looksLikeWrapper(String cmd) {
        String lower = cmd.toLowerCase(Locale.ROOT);
        return lower.endsWith("mvnw") || lower.endsWith("mvnw.cmd");
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    // ── Records ────────────────────────────────────────────────────────────

    public record CompileResult(int exitCode, String output) {
        public boolean success() { return exitCode == 0; }
    }

    /** Totals from a {@code mvn test} run, plus the names of the tests that did not pass. */
    public record TestSummary(int run, int failures, int errors, int skipped, List<String> failedTests) {
        public boolean allPassed() { return failures == 0 && errors == 0; }
        public int notPassed() { return failures + errors; }
    }

    public record CompileErrorRow(Path file, int line, int col, String message) {
        public String formatted() { return file.getFileName() + ":" + line + ":" + col + " " + message; }
    }
}
