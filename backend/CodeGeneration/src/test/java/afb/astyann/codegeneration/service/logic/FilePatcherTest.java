package afb.astyann.codegeneration.service.logic;

import afb.astyann.codegeneration.domain.logic.StubMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FilePatcherTest {

    private final FilePatcher patcher = new FilePatcher();

    private Path write(Path dir, String name, String src) throws IOException {
        Path f = dir.resolve(name);
        Files.createDirectories(f.getParent() == null ? dir : f.getParent());
        Files.writeString(f, src, StandardCharsets.UTF_8);
        return f;
    }

    @Test
    void findStubMethodsDetectsUnsupportedOperationBodies(@TempDir Path dir) throws IOException {
        Path f = write(dir, "Foo.java", """
                package a;
                class Foo {
                    public void doThing() { throw new UnsupportedOperationException("todo"); }
                    public int ok() { return 1; }
                }
                """);
        List<StubMethod> stubs = patcher.findStubMethods(f);
        assertThat(stubs).hasSize(1);
        assertThat(stubs.get(0).methodName()).isEqualTo("doThing");
        assertThat(stubs.get(0).className()).isEqualTo("Foo");
    }

    @Test
    void replaceEntireFileRejectsNonParsingSource(@TempDir Path dir) throws IOException {
        Path f = write(dir, "Bar.java", "package a; class Bar {}");
        boolean ok = patcher.replaceEntireFile(f, "this is not valid java {{{");
        assertThat(ok).isFalse();
        assertThat(Files.readString(f)).contains("class Bar");
    }

    @Test
    void replaceEntireFileWritesValidSource(@TempDir Path dir) throws IOException {
        Path f = write(dir, "Baz.java", "package a; class Baz {}");
        boolean ok = patcher.replaceEntireFile(f, "package a; class Baz { int x() { return 2; } }");
        assertThat(ok).isTrue();
        assertThat(Files.readString(f)).contains("return 2");
    }

    @Test
    void findAllStubMethodsAggregatesAcrossTree(@TempDir Path dir) throws IOException {
        write(dir, "service/impl/AImpl.java", """
                package a; class AImpl {
                    void a() { throw new UnsupportedOperationException(); }
                    void b() { throw new UnsupportedOperationException(); }
                }
                """);
        write(dir, "service/impl/BImpl.java", """
                package a; class BImpl {
                    void c() { throw new UnsupportedOperationException(); }
                    void d() { /* implemented */ }
                }
                """);
        assertThat(patcher.countStubMethods(dir)).isEqualTo(3);
    }

    @Test
    void findAllStubMethodsOnMissingRootIsEmpty(@TempDir Path dir) {
        assertThat(patcher.findAllStubMethods(dir.resolve("nope"))).isEmpty();
    }

    @Test
    void scanTreeReportsStubsAndUnparseableFilesSeparately(@TempDir Path dir) throws IOException {
        write(dir, "Good.java", """
                package a; class Good { void x() { throw new UnsupportedOperationException(); } }
                """);
        // Invalid Java (the exact shape that broke generation: a param with a type but no name)
        write(dir, "Bad.java", "package a; interface Bad { X foo(UUID id,  request); }");

        FilePatcher.TreeScan scan = patcher.scanTree(dir);

        assertThat(scan.stubs()).hasSize(1);
        assertThat(scan.unparseable()).hasSize(1);
        assertThat(scan.unparseable().get(0).getFileName().toString()).isEqualTo("Bad.java");
        assertThat(scan.blockingCount()).isEqualTo(2);
    }

    @Test
    void isThreadSafeUnderParallelParsing(@TempDir Path dir) throws Exception {
        // Regression: a single shared JavaParser is not thread-safe; parallel logic injection
        // corrupted its token manager ("tok is null" / IndexOutOfBounds). Each thread must parse
        // its own file without interference.
        int n = 64;
        for (int i = 0; i < n; i++) {
            write(dir, "C" + i + ".java", "package a; class C" + i
                    + " { void s() { throw new UnsupportedOperationException(); } int ok() { return " + i + "; } }");
        }
        var pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            List<java.util.concurrent.Future<Integer>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) {
                Path f = dir.resolve("C" + i + ".java");
                futures.add(pool.submit(() -> patcher.findStubMethods(f).size()));
            }
            int totalStubs = 0;
            for (var fut : futures) totalStubs += fut.get();
            assertThat(totalStubs).isEqualTo(n); // exactly one stub per file, none lost to corruption
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void methodSignaturesListsDeclarations(@TempDir Path dir) throws IOException {
        Path f = write(dir, "Repo.java", """
                package a;
                interface Repo {
                    java.util.List<String> findByName(String name);
                    long countActive();
                }
                """);
        List<String> sigs = patcher.methodSignatures(f);
        assertThat(sigs).anyMatch(s -> s.contains("findByName"))
                .anyMatch(s -> s.contains("countActive"));
    }
}
