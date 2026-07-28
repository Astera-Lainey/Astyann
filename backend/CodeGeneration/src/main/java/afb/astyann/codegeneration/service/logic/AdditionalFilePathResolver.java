package afb.astyann.codegeneration.service.logic;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Single place that turns an AI-supplied {@code additionalFiles} key into a concrete, safe path
 * under {@code src/main/java}. Both the logic-injection pass and the compile-fix loop used to
 * do this inline with two divergent (and partly dead) strategies — this unifies them.
 *
 * <p>Accepted key shapes, all relative to the {@code src/main/java} root passed in as
 * {@code javaRoot}:
 * <ul>
 *   <li>a full package path — {@code "com/example/app/dto/Foo.java"} — used verbatim;</li>
 *   <li>a sub-package-relative path — {@code "dto/Foo.java"}, {@code "enums/Bar.java"} — the
 *       project's base package (from {@code packageHint}) is prepended;</li>
 *   <li>a bare filename — {@code "Foo.java"} / {@code "Foo"} — placed under
 *       {@code <package>/dto/}.</li>
 * </ul>
 *
 * <p>The result is always {@code normalize()}d and guarded with {@code startsWith(javaRoot)} so a
 * malicious {@code ../} key can never escape the working tree ({@link Optional#empty()} on
 * rejection).
 */
@Component
@Slf4j
public class AdditionalFilePathResolver {

    /** Common top-level package roots that mark a key as an already-rooted full package path. */
    private static final List<String> PACKAGE_ROOTS =
            List.of("com/", "org/", "io/", "net/", "afb/", "edu/", "gov/", "app/");

    /**
     * @param javaRoot    the {@code src/main/java} directory the file must land under
     * @param key         the AI-supplied relative path (any of the shapes above)
     * @param packageHint the project base package in dot form (e.g. {@code com.example.app}),
     *                    or {@code null} if unknown
     * @return the resolved absolute path, or empty if the key is blank or escapes {@code javaRoot}
     */
    public Optional<Path> resolve(Path javaRoot, String key, String packageHint) {
        if (javaRoot == null || key == null || key.isBlank()) return Optional.empty();

        String normalized = key.replace('\\', '/').replaceFirst("^/+", "").trim();
        if (normalized.isEmpty()) return Optional.empty();
        if (!normalized.endsWith(".java")) normalized = normalized + ".java";

        String pkgPath = (packageHint == null || packageHint.isBlank())
                ? null : packageHint.replace('.', '/');

        String relative;
        if (pkgPath != null && normalized.startsWith(pkgPath + "/")) {
            // already rooted at the base package
            relative = normalized;
        } else if (!normalized.contains("/")) {
            // bare filename -> default to the dto sub-package
            relative = (pkgPath != null ? pkgPath + "/dto/" : "") + normalized;
        } else if (looksLikeFullPackagePath(normalized)) {
            // full package path under some other root (com/…, org/…, …)
            relative = normalized;
        } else {
            // sub-package-relative (dto/…, enums/…, exception/…, service/impl/…)
            relative = (pkgPath != null ? pkgPath + "/" : "") + normalized;
        }

        Path target = javaRoot.resolve(relative).normalize();
        if (!target.startsWith(javaRoot)) {
            log.warn("Refusing suspicious additional-file path from AI: {}", key);
            return Optional.empty();
        }
        return Optional.of(target);
    }

    private boolean looksLikeFullPackagePath(String normalized) {
        String lower = normalized.toLowerCase(java.util.Locale.ROOT);
        return PACKAGE_ROOTS.stream().anyMatch(lower::startsWith);
    }
}
