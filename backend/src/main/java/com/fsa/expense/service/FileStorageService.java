package com.fsa.expense.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    @Value("${app.storage.upload-dir:uploads/invoices}")
    private String uploadDir;

    private Path storageLocation;

    @PostConstruct
    public void init() {
        this.storageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.storageLocation);
            log.info("File storage directory initialized at: {}", this.storageLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage directory", e);
        }
    }

    public String storeFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.contains("..")) {
            throw new IllegalArgumentException("Invalid filename");
        }

        String extension = "";
        int i = originalFilename.lastIndexOf('.');
        if (i > 0) {
            extension = originalFilename.substring(i);
        }

        String storedFilename = UUID.randomUUID().toString() + extension;
        Path targetPath = this.storageLocation.resolve(storedFilename);

        try {
            try (var input = file.getInputStream()) {
                Files.copy(input, targetPath);
            }
            log.info("Saved uploaded file {} as {}", originalFilename, storedFilename);
            return storedFilename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file " + originalFilename, e);
        }
    }

    public Resource loadFileAsResource(String filename) {
        try {
            Path filePath = this.storageLocation.resolve(filename).normalize();
            if (!filePath.startsWith(this.storageLocation)) throw new IllegalArgumentException("Invalid storage path");
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("File not found: " + filename);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Error loading file: " + filename, e);
        }
    }

    public Path getFilePath(String filename) {
        Path path = this.storageLocation.resolve(filename).normalize();
        if (!path.startsWith(this.storageLocation)) throw new IllegalArgumentException("Invalid storage path");
        return path;
    }

    public String storeBytes(byte[] bytes) {
        String name = UUID.randomUUID() + ".bin";
        try (var channel=java.nio.channels.FileChannel.open(getFilePath(name), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            var buffer=java.nio.ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
            return name;
        }
        catch (IOException e) { throw new IllegalStateException("Không lưu được tài liệu gốc", e); }
    }
}
