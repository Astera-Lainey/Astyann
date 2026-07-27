package afb.astyann.codegeneration.service.logic;

import afb.astyann.codegeneration.domain.logic.StubMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FilePatcherTest {

    private final FilePatcher patcher = new FilePatcher();

    @Test
    void detects_and_replaces_unsupported_operation_stub(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("ProductServiceImpl.java");
        Files.writeString(file, """
                package com.example.product.service.impl;

                import com.example.product.service.ProductService;

                public class ProductServiceImpl implements ProductService {

                    public String archive(String id) {
                        // TODO: implement archive logic
                        throw new UnsupportedOperationException("archive not yet implemented");
                    }

                    public String getById(String id) {
                        return id;
                    }
                }
                """);

        List<StubMethod> stubs = patcher.findStubMethods(file);
        assertThat(stubs).hasSize(1);
        assertThat(stubs.get(0).methodName()).isEqualTo("archive");
        assertThat(stubs.get(0).className()).isEqualTo("ProductServiceImpl");

        boolean patched = patcher.replaceMethodBody(file, "archive",
                "return \"archived-\" + id;");
        assertThat(patched).isTrue();

        String updated = Files.readString(file);
        assertThat(updated).contains("return \"archived-\" + id;")
                .doesNotContain("UnsupportedOperationException");

        assertThat(patcher.findStubMethods(file)).isEmpty();
    }

    @Test
    void refuses_to_write_invalid_java(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("Broken.java");
        String original = "package p; public class Broken { public int one() { return 1; } }";
        Files.writeString(file, original);

        boolean written = patcher.replaceEntireFile(file, "this is not java {{{{");

        assertThat(written).isFalse();
        assertThat(Files.readString(file)).isEqualTo(original);
    }

    @Test
    void accepts_java17_record_declarations(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("GoodsReceivedDto.java");
        String source = """
                package com.example.dto;

                public record GoodsReceivedDto(String productId, int quantity) {}
                """;
        boolean written = patcher.replaceEntireFile(file, source);
        assertThat(written).isTrue();
        assertThat(Files.readString(file)).contains("record GoodsReceivedDto");
    }
}
