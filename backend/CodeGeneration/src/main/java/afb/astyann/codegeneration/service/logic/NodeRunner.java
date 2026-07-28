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
 * Runs {@code npm install} + {@code npx tsc --noEmit} against a generated Angular project and
 * parses the TypeScript compiler output into structured rows — the frontend counterpart of
 * {@link MavenRunner}.
 *
 * <p>Command resolution is OS-aware for the same reason Maven's is: {@code ProcessBuilder("npm")}
 * fails on Windows because {@code npm} ships as {@code npm.cmd} and Java does not do
 * {@code PATHEXT} resolution. Candidates are probed with {@code -v}; the first that works is
 * cached for the process lifetime.
 */
@Service
@Slf4j
public class NodeRunner {

    /** {@code src/app/foo.component.ts(12,5): error TS2322: Type 'x' is not assignable...} */
    private static final Pattern TS_ERROR_LINE = Pattern.compile(
            "^(.+?\\.ts)\\((\\d+),(\\d+)\\):\\s*error\\s+(TS\\d+):\\s*(.*)");

    @Value("${codegen.validate.frontend.npm-command:}")
    private String npmCommandOverride;

    private volatile String cachedNpm;
    private volatile String cachedNpx;

    private static final int MAX_TAIL_LINES = 40;

    // ── Public API ─────────────────────────────────────────────────────────

    public boolean isAvailable(Path projectDir) {
        return resolveNpm(projectDir).isPresent() && resolveNpx(projectDir).isPresent();
    }

    /** {@code npm install} — installs devDependencies (TypeScript, Angular) so tsc can run. */
    public NodeResult install(Path projectDir) throws IOException, InterruptedException {
        String npm = resolveNpm(projectDir).orElseThrow(() ->
                new IOException("No usable npm command found. Tried: " + describeNpm(projectDir)));
        return run(projectDir, List.of(npm, "install", "--no-audit", "--no-fund"), 15);
    }

    /**
     * Type-checks with {@code npx tsc --noEmit}, preferring the Angular {@code tsconfig.app.json}
     * and falling back to {@code tsconfig.json}.
     */
    public NodeResult typeCheck(Path projectDir) throws IOException, InterruptedException {
        String npx = resolveNpx(projectDir).orElseThrow(() ->
                new IOException("No usable npx command found. Tried: " + describeNpx(projectDir)));
        String tsconfig = Files.exists(projectDir.resolve("tsconfig.app.json"))
                ? "tsconfig.app.json" : "tsconfig.json";
        return run(projectDir, List.of(npx, "tsc", "--noEmit", "-p", tsconfig), 10);
    }

    public List<TsErrorRow> parseErrors(String output) {
        List<TsErrorRow> out = new ArrayList<>();
        if (output == null || output.isBlank()) return out;
        for (String raw : output.split("\\r?\\n")) {
            Matcher m = TS_ERROR_LINE.matcher(raw.trim());
            if (m.matches()) {
                out.add(new TsErrorRow(
                        m.group(1).replace('\\', '/'),
                        Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3)),
                        m.group(4),
                        m.group(5).trim()));
            }
        }
        return out;
    }

    public String tail(String output, int n) {
        if (output == null) return "";
        String[] lines = output.split("\\r?\\n");
        int start = Math.max(0, lines.length - n);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < lines.length; i++) sb.append(lines[i]).append('\n');
        return sb.toString();
    }

    public String extractFailureSummary(String output) {
        return tail(output, MAX_TAIL_LINES);
    }

    // ── Process execution ──────────────────────────────────────────────────

    private NodeResult run(Path projectDir, List<String> argv, int timeoutMinutes)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(argv)
                .directory(projectDir.toFile())
                .redirectErrorStream(true);
        Process process = pb.start();
        String output;
        try (var in = process.getInputStream()) {
            output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException(argv.get(0) + " timed out after " + timeoutMinutes + " minutes");
        }
        int exit = process.exitValue();
        if (log.isDebugEnabled()) log.debug("{} output ({} bytes):\n{}", argv, output.length(), output);
        if (exit != 0) log.warn("{} failed (exit={}). Last {} lines:\n{}",
                argv, exit, MAX_TAIL_LINES, tail(output, MAX_TAIL_LINES));
        return new NodeResult(exit, output);
    }

    // ── Candidate resolution ───────────────────────────────────────────────

    private Optional<String> resolveNpm(Path projectDir) {
        if (cachedNpm != null && tryVersion(cachedNpm)) return Optional.of(cachedNpm);
        for (String c : npmCandidates()) {
            if (tryVersion(c)) { cachedNpm = c; return Optional.of(c); }
        }
        return Optional.empty();
    }

    private Optional<String> resolveNpx(Path projectDir) {
        if (cachedNpx != null && tryVersion(cachedNpx)) return Optional.of(cachedNpx);
        for (String c : npxCandidates()) {
            if (tryVersion(c)) { cachedNpx = c; return Optional.of(c); }
        }
        return Optional.empty();
    }

    private List<String> npmCandidates() {
        List<String> out = new ArrayList<>();
        if (npmCommandOverride != null && !npmCommandOverride.isBlank()) out.add(npmCommandOverride.trim());
        if (isWindows()) { out.add("npm.cmd"); out.add("npm.bat"); out.add("npm"); }
        else out.add("npm");
        return out;
    }

    private List<String> npxCandidates() {
        List<String> out = new ArrayList<>();
        if (isWindows()) { out.add("npx.cmd"); out.add("npx.bat"); out.add("npx"); }
        else out.add("npx");
        return out;
    }

    private String describeNpm(Path projectDir) { return String.join(", ", npmCandidates()); }
    private String describeNpx(Path projectDir) { return String.join(", ", npxCandidates()); }

    private boolean tryVersion(String cmd) {
        try {
            Process p = new ProcessBuilder(cmd, "-v").redirectErrorStream(true).start();
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            if (!finished) { p.destroyForcibly(); return false; }
            return p.exitValue() == 0;
        } catch (Exception ex) {
            log.debug("node candidate '{}' unavailable: {}", cmd, ex.getMessage());
            return false;
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    // ── Records ────────────────────────────────────────────────────────────

    public record NodeResult(int exitCode, String output) {
        public boolean success() { return exitCode == 0; }
    }

    public record TsErrorRow(String file, int line, int col, String code, String message) {
        public String formatted() { return file + ":" + line + ":" + col + " " + code + " " + message; }
    }
}
