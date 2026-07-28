package afb.astyann.codegeneration.service.logic;

import afb.astyann.codegeneration.domain.logic.StubMethod;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * JavaParser-based helper for reading, inspecting and rewriting generated Java files. Kept
 * intentionally small — the AI pass and the compile-fix loop both go through this so we have a
 * single place to add safety checks.
 *
 * <p>Language level is {@link ParserConfiguration.LanguageLevel#JAVA_17} to match the generated
 * projects ({@code release 17}). The default JavaParser level rejects records / text blocks /
 * pattern matching, which caused valid AI fixes to be refused with "Record Declarations are not
 * supported".
 */
@Service
@Slf4j
public class FilePatcher {

    // JavaParser is NOT thread-safe — a single instance shared across the parallel logic-injection
    // threads corrupts its internal token manager ("tok is null" / IndexOutOfBounds). Give each
    // thread its own parser. The pool threads are long-lived, so these are created once per thread
    // and reused.
    private final ThreadLocal<JavaParser> parser = ThreadLocal.withInitial(() -> new JavaParser(
            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17)));

    public String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    public void write(Path file, String source) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, source, StandardCharsets.UTF_8);
    }

    /** Parses {@code file} and returns every method whose body throws
     *  {@link UnsupportedOperationException} — the target set for AI logic injection. Parsing is
     *  quiet: an unparseable file yields an empty list without logging (the tree walk in
     *  {@link #scanTree(Path)} reports unparseable files as a group instead). */
    public List<StubMethod> findStubMethods(Path file) {
        CompilationUnit cu = parseQuiet(file);
        return cu == null ? new ArrayList<>() : stubsIn(cu, file);
    }

    private List<StubMethod> stubsIn(CompilationUnit cu, Path file) {
        List<StubMethod> out = new ArrayList<>();
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz ->
                clazz.getMethods().forEach(method -> {
                    if (isUnsupportedStub(method)) {
                        out.add(new StubMethod(file, clazz.getNameAsString(),
                                method.getNameAsString(), method.getDeclarationAsString()));
                    }
                }));
        return out;
    }

    private boolean isUnsupportedStub(MethodDeclaration method) {
        return method.getBody()
                .map(b -> b.toString().contains("UnsupportedOperationException"))
                .orElse(false);
    }

    /**
     * Result of walking a generated source tree: the remaining stub methods AND the files that
     * could not be parsed at all. Both matter for completeness — a stub body compiles, and an
     * unparseable file is worse than a stub (it won't compile), so neither may be silently
     * treated as "done".
     */
    public record TreeScan(List<StubMethod> stubs, List<Path> unparseable) {
        /** Total blocking issues: stubs + unparseable files. {@code 0} == verifiably complete. */
        public int blockingCount() { return stubs.size() + unparseable.size(); }
    }

    /**
     * Walks every {@code *.java} file under {@code root}, collecting remaining stub methods and
     * the files that fail to parse. A missing / unreadable {@code root} yields an empty scan.
     * Parse failures are collected quietly here rather than logged per-file with a stacktrace.
     */
    public TreeScan scanTree(Path root) {
        List<StubMethod> stubs = new ArrayList<>();
        List<Path> unparseable = new ArrayList<>();
        if (root == null || !Files.exists(root)) return new TreeScan(stubs, unparseable);
        try (var paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .forEach(p -> {
                        CompilationUnit cu = parseQuiet(p);
                        if (cu == null) unparseable.add(p);
                        else stubs.addAll(stubsIn(cu, p));
                    });
        } catch (IOException ex) {
            log.warn("Could not scan {} for stubs: {}", root, ex.getMessage());
        }
        return new TreeScan(stubs, unparseable);
    }

    /** Convenience: stub methods only, across the whole tree. */
    public List<StubMethod> findAllStubMethods(Path root) {
        return scanTree(root).stubs();
    }

    /** Convenience count of remaining stub methods under {@code root}. */
    public int countStubMethods(Path root) {
        return findAllStubMethods(root).size();
    }

    /** Returns the declaration string of every method on the class in {@code repositoryFile}
     *  — used to tell the AI which repo calls are allowed. */
    public List<String> methodSignatures(Path javaFile) {
        List<String> out = new ArrayList<>();
        CompilationUnit cu = parseOrNull(javaFile);
        if (cu == null) return out;
        cu.findAll(MethodDeclaration.class)
                .forEach(m -> out.add(m.getDeclarationAsString(true, false, false)));
        return out;
    }

    /**
     * Replaces the body of one method in {@code file}. Falls back to a no-op with a warning if
     * the file no longer parses or the method cannot be found.
     */
    public boolean replaceMethodBody(Path file, String methodName, String rawBodyWithBraces) throws IOException {
        CompilationUnit cu = parseOrNull(file);
        if (cu == null) return false;
        var target = cu.findFirst(MethodDeclaration.class,
                m -> m.getNameAsString().equals(methodName));
        if (target.isEmpty()) {
            log.warn("Method {} not found in {} — cannot replace body.", methodName, file);
            return false;
        }
        String body = rawBodyWithBraces.trim();
        if (!body.startsWith("{")) body = "{" + body + "}";
        var parsed = parser.get().parseBlock(body);
        if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) {
            log.warn("Failed to parse replacement body for {}: {}", methodName, parsed.getProblems());
            return false;
        }
        target.get().setBody(parsed.getResult().get());
        write(file, cu.toString());
        return true;
    }

    /**
     * Full-file replacement — used when the AI returns the entire updated class. Verifies the
     * replacement parses before writing to avoid corrupting the working tree.
     */
    public boolean replaceEntireFile(Path file, String newSource) throws IOException {
        ParseResult<CompilationUnit> parsed = parser.get().parse(newSource);
        if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) {
            log.warn("Refusing to write {} — replacement does not parse: {}",
                    file, parsed.getProblems());
            return false;
        }
        write(file, parsed.getResult().get().toString());
        return true;
    }

    /** Parses without logging — used by the tree scan, which reports failures as a group. */
    private CompilationUnit parseQuiet(Path file) {
        try {
            ParseResult<CompilationUnit> res = parser.get().parse(file);
            if (res.isSuccessful() && res.getResult().isPresent()) return res.getResult().get();
        } catch (Exception ignored) {
            // reported by the caller as an unparseable file (includes JavaParser internal errors)
        }
        return null;
    }

    private CompilationUnit parseOrNull(Path file) {
        try {
            ParseResult<CompilationUnit> res = parser.get().parse(file);
            if (res.isSuccessful() && res.getResult().isPresent()) return res.getResult().get();
            String firstProblem = res.getProblems().isEmpty() ? "unknown"
                    : res.getProblems().get(0).getVerboseMessage().lines().findFirst().orElse("parse error");
            log.warn("Failed to parse {}: {}", file.getFileName(), firstProblem);
        } catch (IOException ex) {
            log.warn("I/O error reading {}: {}", file.getFileName(), ex.getMessage());
        } catch (Exception ex) {
            // JavaParser can throw internal RuntimeExceptions on malformed input — treat as unparseable.
            log.warn("Parser error on {}: {}", file.getFileName(), ex.getMessage());
        }
        return null;
    }
}
