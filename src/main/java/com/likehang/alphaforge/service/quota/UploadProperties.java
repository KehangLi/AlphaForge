package com.likehang.alphaforge.service.quota;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("alphaforge.uploads")
public record UploadProperties(@NotNull DataSize maxFileSize, @Min(1) @Max(100_000) int maxRows) {

    @AssertTrue(message = "upload size must be positive and at most 10MB")
    public boolean isFileSizeValid() {
        return maxFileSize != null && maxFileSize.toBytes() > 0
                && maxFileSize.toBytes() <= DataSize.ofMegabytes(10).toBytes();
    }
}
