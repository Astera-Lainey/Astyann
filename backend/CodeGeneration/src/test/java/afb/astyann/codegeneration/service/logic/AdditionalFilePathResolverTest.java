package afb.astyann.codegeneration.service.logic;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AdditionalFilePathResolverTest {

    private final AdditionalFilePathResolver resolver = new AdditionalFilePathResolver();
    private final Path javaRoot = Path.of(System.getProperty("java.io.tmpdir"))
            .resolve("codegen-smj").toAbsolutePath().normalize();

    private String rel(Optional<Path> p) {
        return p.map(t -> javaRoot.relativize(t).toString().replace('\\', '/')).orElse(null);
    }

    @Test
    void subPackageRelativeKeyGetsPackagePrefixed() {
        var r = resolver.resolve(javaRoot, "dto/FooDto.java", "com.example.app");
        assertThat(rel(r)).isEqualTo("com/example/app/dto/FooDto.java");
    }

    @Test
    void bareFilenameLandsUnderDtoWithJavaSuffixAdded() {
        var r = resolver.resolve(javaRoot, "BarDto", "com.example.app");
        assertThat(rel(r)).isEqualTo("com/example/app/dto/BarDto.java");
    }

    @Test
    void alreadyRootedFullPackagePathIsNotDoubled() {
        var r = resolver.resolve(javaRoot, "com/example/app/enums/Status.java", "com.example.app");
        assertThat(rel(r)).isEqualTo("com/example/app/enums/Status.java");
    }

    @Test
    void fullPackagePathUnderAnotherRootIsKeptVerbatim() {
        var r = resolver.resolve(javaRoot, "org/util/Helper.java", "com.example.app");
        assertThat(rel(r)).isEqualTo("org/util/Helper.java");
    }

    @Test
    void backslashesAreNormalised() {
        var r = resolver.resolve(javaRoot, "service\\impl\\Helper.java", "com.example.app");
        assertThat(rel(r)).isEqualTo("com/example/app/service/impl/Helper.java");
    }

    @Test
    void pathThatEscapesJavaRootIsRejected() {
        var r = resolver.resolve(javaRoot, "../../../../../../evil/Pwn.java", "com.example.app");
        assertThat(r).isEmpty();
    }

    @Test
    void blankAndNullKeysAreRejected() {
        assertThat(resolver.resolve(javaRoot, "   ", "com.example.app")).isEmpty();
        assertThat(resolver.resolve(javaRoot, null, "com.example.app")).isEmpty();
    }

    @Test
    void nullPackageHintLeavesKeyRelativeToRoot() {
        var r = resolver.resolve(javaRoot, "enums/Status.java", null);
        assertThat(rel(r)).isEqualTo("enums/Status.java");
    }
}
