package com.likehang.alphaforge.csv;

import com.likehang.alphaforge.exception.UploadTooLargeException;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CsvImportFileSupportTest {

    @Test
    void neverReadsAnUnboundedStreamEvenIfTheReportedSizeIsWrong() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        var stream = new ByteArrayInputStream(new byte[1000]);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1L);
        when(file.getInputStream()).thenReturn(stream);
        assertThatThrownBy(() -> new CsvImportFileSupport().readFileBytes(file, 100))
                .isInstanceOf(UploadTooLargeException.class);
        assertThat(stream.available()).isEqualTo(899);
    }
}
