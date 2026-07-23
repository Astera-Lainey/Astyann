package afb.astyann.codegeneration.service.logic;

import afb.astyann.codegeneration.domain.logic.StubMethod;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
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
 */
@Service
@Slf4j
public class FilePatcher {

    private final JavaParser parser = new JavaParser();

    public String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    public void write(Path file, String source) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, source, StandardCharsets.UTF_8);
    }

    /** Parses {@code file} and returns every method whose body throws
     *  {@link UnsupportedOperationException} — the target set for AI logic injection. */
    public List<StubMethod> findStubMethods(Path file) {
        List<StubMethod> out = new ArrayList<>();
        CompilationUnit cu = parseOrNull(file);
        if (cu == null) return out;
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
        var parsed = parser.parseBlock(body);
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
        ParseResult<CompilationUnit> parsed = parser.parse(newSource);
        if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) {
            log.warn("Refusing to write {} — replacement does not parse: {}",
                    file, parsed.getProblems());
            return false;
        }
        write(file, parsed.getResult().get().toString());
        return true;
    }

    private CompilationUnit parseOrNull(Path file) {
        try {
            ParseResult<CompilationUnit> res = parser.parse(file);
            if (res.isSuccessful() && res.getResult().isPresent()) return res.getResult().get();
            log.warn("Failed to parse {}: {}", file, res.getProblems());
        } catch (IOException ex) {
            log.warn("I/O error reading {}: {}", file, ex.getMessage());
        }
        return null;
    }
}
