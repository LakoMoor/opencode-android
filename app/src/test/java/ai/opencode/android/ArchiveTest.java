package ai.opencode.android;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.apache.commons.compress.archivers.tar.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.GZIPOutputStream;
import static org.junit.Assert.*;

public class ArchiveTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    @Test public void extractsRuntimeFilesWithLongPaths() throws Exception {
        File root=temporary.newFolder("linux");
        String name="usr/share/doc/"+"long-runtime-directory-".repeat(8)+"/copyright";
        LocalRuntime.extract(new ByteArrayInputStream(archive(name,"license")),root);
        assertEquals("license",new String(Files.readAllBytes(new File(root,name).toPath()),StandardCharsets.UTF_8));
    }
    @Test public void rejectsArchiveTraversal() throws Exception {
        File root=temporary.newFolder("root");
        try { LocalRuntime.extract(new ByteArrayInputStream(archive("../escape","unsafe")),root);fail(); }
        catch(IOException expected){ assertTrue(expected.getMessage().contains("Unsafe")); }
        assertFalse(new File(root.getParentFile(),"escape").exists());
    }
    private byte[] archive(String name,String value) throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(TarArchiveOutputStream tar=new TarArchiveOutputStream(new GZIPOutputStream(bytes))){
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            byte[] content=value.getBytes(StandardCharsets.UTF_8);
            TarArchiveEntry entry=new TarArchiveEntry(name);entry.setSize(content.length);tar.putArchiveEntry(entry);tar.write(content);tar.closeArchiveEntry();
        }
        return bytes.toByteArray();
    }
}
