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

    /** Angular/esbuild header line: {@code ✘ [ERROR] NG8001: 'x' is not a known element}. */
    private static final Pattern NG_ERROR_HEADER = Pattern.compile(
            "^(?:✘\\s*)?\\[ERROR]\\s*(NG\\d+|TS\\d+)?:?\\s*(.*)");

    /** The indented location line esbuild prints below a header: {@code src/app/x.html:6:8:}. */
    private static final Pattern NG_ERROR_LOCATION = Pattern.compile(
            "^(\\S.*?\\.(?:ts|html)):(\\d+):(\\d+):?$");

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

    /**
     * Runs the real Angular build ({@code ng build --configuration development}).
     *
     * <p>Preferred over {@link #typeCheck(Path)} whenever {@code angular.json} exists, because
     * {@code tsc} only checks TypeScript — it never compiles component templates, so an invalid
     * binding or an unknown element passes silently. The development configuration skips
     * optimization and budget checks, which are irrelevant to correctness.
     */
    public NodeResult build(Path projectDir) throws IOException, InterruptedException {
        String npx = resolveNpx(projectDir).orElseThrow(() ->
                new IOException("No usable npx command found. Tried: " + describeNpx(projectDir)));
        return run(projectDir, List.of(npx, "ng", "build", "--configuration", "development"), 15);
    }

    /** Whether the project carries an Angular CLI workspace file. */
    public boolean hasAngularWorkspace(Path projectDir) {
        return projectDir != null && Files.exists(projectDir.resolve("angular.json"));
    }

    /**
     * Parses both error formats the toolchain produces: plain {@code tsc}
     * ({@code file(line,col): error TSxxxx: msg}) and the Angular/esbuild build
     * ({@code ✘ [ERROR] NGxxxx: msg} followed by an indented {@code file:line:col:} line).
     */
    public List<TsErrorRow> parseErrors(String output) {
        List<TsErrorRow> out = new ArrayList<>();
        if (output == null || output.isBlank()) return out;
        String[] lines = output.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();

            Matcher tsc = TS_ERROR_LINE.matcher(trimmed);
            if (tsc.matches()) {
                out.add(new TsErrorRow(
                        tsc.group(1).replace('\\', '/'),
                        Integer.parseInt(tsc.group(2)),
                        Integer.parseInt(tsc.group(3)),
                        tsc.group(4),
                        tsc.group(5).trim()));
                continue;
            }

            Matcher header = NG_ERROR_HEADER.matcher(trimmed);
            if (header.matches()) {
                String code = header.group(1) != null ? header.group(1) : "NG";
                String message = header.group(2).replace("[plugin angular-compiler]", "").trim();
                // The location follows within the next few lines.
                for (int j = i + 1; j < Math.min(lines.length, i + 8); j++) {
                    Matcher loc = NG_ERROR_LOCATION.matcher(lines[j].trim());
                    if (loc.matches()) {
                        out.add(new TsErrorRow(
                                loc.group(1).replace('\\', '/'),
                                Integer.parseInt(loc.group(2)),
                                Integer.parseInt(loc.group(3)),
                                code, message));
                        i = j;
                        break;
                    }
                }
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

    /** {@code npm error code ETARGET} */
    private static final Pattern NPM_ERROR_CODE = Pattern.compile(
            "^npm (?:ERR!|error)\\s+code\\s+([A-Z0-9_]+)\\s*$");

    /** {@code No matching version found for @jsonjoy.com/fs-node@4.66.0.} */
    private static final Pattern NPM_NO_MATCHING_VERSION = Pattern.compile(
            "No matching version found for\\s+(\\S+?)@([^\\s.]+(?:\\.[^\\s.]+)*?)\\.?\\s*$");

    /** {@code 404  '@iconify/angular@^2.0.0' is not in this registry.} */
    private static final Pattern NPM_NOT_IN_REGISTRY = Pattern.compile(
            "'([^']+?)@([^']+)'\\s+is not in this registry");

    /**
     * npm codes that mean dependency resolution or the registry failed — the project's own sources
     * were never looked at. Distinguishing these matters: a package the generator itself declares
     * is a defect in the templates, whereas a transitive one is an upstream or network problem, and
     * reporting both as "frontend compile FAILED" sends you hunting through generated code for a
     * fault that is not there.
     */
    private static final List<String> RESOLUTION_ERROR_CODES = List.of(
            "ETARGET", "E404", "ENOTFOUND", "EAI_AGAIN", "ECONNREFUSED", "ECONNRESET",
            "ETIMEDOUT", "ERR_SOCKET_TIMEOUT", "EAGAIN", "ENETUNREACH", "E429", "EINTEGRITY");

    /**
     * Extracts the npm failure code and, where npm names one, the package whose version could not
     * be resolved. Returns empty when the output carries no recognisable npm error code — in which
     * case the failure is something else (a lifecycle script, a permissions problem) and should be
     * reported verbatim rather than explained away.
     */
    public Optional<InstallFailure> classifyInstallFailure(String output) {
        if (output == null || output.isBlank()) return Optional.empty();
        String code = null;
        String pkg = null;
        for (String raw : output.split("\\r?\\n")) {
            String line = raw.trim();
            if (code == null) {
                Matcher m = NPM_ERROR_CODE.matcher(line);
                if (m.matches()) { code = m.group(1); continue; }
            }
            if (pkg == null) {
                Matcher m = NPM_NO_MATCHING_VERSION.matcher(line);
                if (m.find()) { pkg = m.group(1); continue; }
                m = NPM_NOT_IN_REGISTRY.matcher(line);
                if (m.find()) pkg = m.group(1);
            }
        }
        if (code == null) return Optional.empty();
        return Optional.of(new InstallFailure(code, pkg, RESOLUTION_ERROR_CODES.contains(code)));
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

    /**
     * @param npmCode           npm's own error code, e.g. {@code ETARGET}
     * @param packageName       the package npm named, or {@code null} if it named none
     * @param dependencyProblem whether this is dependency resolution / registry rather than a
     *                          failure of the project's own code
     */
    public record InstallFailure(String npmCode, String packageName, boolean dependencyProblem) {}
}
