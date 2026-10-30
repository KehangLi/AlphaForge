package com.likehang.alphaforge.rest.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthRestController {
    private final String version;
    private final String commit;

    public HealthRestController(
            @Value("${alphaforge.release.version:development}") String version,
            @Value("${alphaforge.release.commit:unknown}") String commit
    ) {
        this.version = version;
        this.commit = commit;
    }

    @GetMapping("/health")
    public ResponseEntity<Void> health() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Alphaforge-Version", version)
                .header("X-Alphaforge-Commit", commit)
                .build();
    }
}
