package net.petrabarus.java.record_dir_and_upload.snapshot;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.io.IOUtils;

public class DirectorySnapshot implements AutoCloseable {

    private final Path path;

    private final OutputStream out;

    private final ZipOutputStream zip;

    public DirectorySnapshot(Path path, OutputStream out) {
        this.path = path;
        this.out = out;
        this.zip = new ZipOutputStream(out);
    }

    public void compress() throws IOException {
        compressRecursive(path);
        IOUtils.closeQuietly(zip);
    }

    private void compressRecursive(Path subPath) throws IOException {
        for (File file : subPath.toFile().listFiles()) {
            Path filePath = file.toPath();
            if (file.isDirectory()) {
                compressRecursive(filePath);
            } else {
                String name = filePath.toString().replace(path.toString(), "");
                ZipEntry entry = new ZipEntry(name);
                zip.putNextEntry(entry);
                FileInputStream in = new FileInputStream(file);
                IOUtils.copy(in, zip);
                IOUtils.closeQuietly(in);
            }
        }
    }

    @Override
    public void close() {
        IOUtils.closeQuietly(zip);
        IOUtils.closeQuietly(out);
    }
}
