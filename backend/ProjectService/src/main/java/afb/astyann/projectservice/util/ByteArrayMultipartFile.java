package afb.astyann.projectservice.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.*;

public class ByteArrayMultipartFile implements MultipartFile {

    private final byte[] content;
    private final String name;
    private final String originalFilename;
    private final String contentType;

    public ByteArrayMultipartFile(byte[] content,
                                  String originalFilename,
                                  String contentType) {
        this.content          = content;
        this.name             = "document";
        this.originalFilename = originalFilename;
        this.contentType      = contentType;
    }

    @Override public String  getName()             { return name; }
    @Override public String  getOriginalFilename() { return originalFilename; }
    @Override public String  getContentType()      { return contentType; }
    @Override public boolean isEmpty()             { return content.length == 0; }
    @Override public long    getSize()             { return content.length; }
    @Override public byte[]  getBytes()            { return content; }
    @Override public InputStream getInputStream()  { return new ByteArrayInputStream(content); }

    @Override
    public void transferTo(File dest) throws IOException {
        try (FileOutputStream out = new FileOutputStream(dest)) {
            out.write(content);
        }
    }
}
