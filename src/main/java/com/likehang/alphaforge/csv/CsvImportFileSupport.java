package com.likehang.alphaforge.csv;

import com.likehang.alphaforge.exception.CsvImportException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class CsvImportFileSupport {

    private static final String DEFAULT_FILENAME = "account-activity.csv";
    private static final int MAX_FILENAME_LENGTH = 255;

    public byte[] readFileBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CsvImportException("CSV file is required");
        }

        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new CsvImportException("Failed to read uploaded file", exception);
        }
    }

    public String cleanOriginalFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            return DEFAULT_FILENAME;
        }

        String cleaned = originalFilename.trim();
        if (cleaned.length() <= MAX_FILENAME_LENGTH) {
            return cleaned;
        }
        return cleaned.substring(cleaned.length() - MAX_FILENAME_LENGTH);
    }

    public String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
