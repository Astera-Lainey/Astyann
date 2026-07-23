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
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs {@code mvn compile} against a generated backend project directory and parses the
 * compiler output into structured {@link afb.astyann.codegeneration.domain.logic.CompileError}
 * records. Uses {@code ProcessBuilder} so it works everywhere the JVM does; requires
 * {@code mvn} on the PATH (see {@code codegen.validate.compile.mvn-command}).
 */
@Service
@Slf4j
public class MavenRunner {

    /**
     * Matches the Maven compile-error format:
     * <pre>[ERROR] /abs/path/File.java:[lineNo,colNo] message</pre>
     * The Maven compiler-plugin emits every javac error in this shape.
     */
    private static final Pattern ERROR_LINE = Pattern.compile(
            "\\[ERROR]\\s+(.+?\\.java):\\[(\\d+),(\\d+)]\\s*(.*)");

    @Value("${codegen.validate.compile.mvn-command:mvn}")
    private String mvnCommand;

    public boolean isAvailable() {
        try {
            Process p = new ProcessBuilder(mvnCommand, "-v").redirectErrorStream(true).start();
            boolean finished = p.waitFor(15, TimeUnit.SECONDS);
            if (!finished) { p.destroyForcibly(); return false; }
            return p.exitValue() == 0;
        } catch (Exception ex) {
            log.debug("mvn availability check failed for command '{}': {}", mvnCommand, ex.getMessage());
            return false;
        }
    }

    public CompileResult compile(Path projectDir) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        // On Windows the mvn wrapper is typically mvn.cmd — allow either.
        cmd.add(mvnCommand);
        cmd.add("-q");
        cmd.add("--batch-mode");
        cmd.add("compile");

        ProcessBuilder pb = new ProcessBuilder(cmd)
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
        return new CompileResult(process.exitValue(), output);
    }

    /** Parses the output of {@link #compile} into a list of structured errors. */
    public List<CompileErrorRow> parseErrors(String output) {
        List<CompileErrorRow> out = new ArrayList<>();
        if (output == null || output.isBlank()) return out;
        for (String line : output.split("\\r?\\n")) {
            Matcher m = ERROR_LINE.matcher(line.trim());
            if (m.matches()) {
                try {
                    Path file = Path.of(m.group(1));
                    int lineNo = Integer.parseInt(m.group(2));
                    int col = Integer.parseInt(m.group(3));
                    out.add(new CompileErrorRow(file, lineNo, col, m.group(4).trim()));
                } catch (Exception ignored) {
                }
            }
        }
        return out;
    }

    /** Reads {@code file} and returns the {@code radius} lines above and below {@code line}. */
    public String readContextAround(Path file, int line, int radius) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        int start = Math.max(0, line - radius - 1);
        int end = Math.min(lines.size(), line + radius);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            sb.append(i + 1).append(": ").append(lines.get(i)).append('\n');
        }
        return sb.toString();
    }

    public record CompileResult(int exitCode, String output) {
        public boolean success() { return exitCode == 0; }
    }

    /** File-level info parsed out of one compiler error row. */
    public record CompileErrorRow(Path file, int line, int col, String message) {
        public String formatted() { return file.getFileName() + ":" + line + ":" + col + " " + message; }
    }
}
