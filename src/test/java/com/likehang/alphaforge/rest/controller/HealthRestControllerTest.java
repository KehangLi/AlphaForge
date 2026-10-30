package com.likehang.alphaforge.rest.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthRestControllerTest {
    @Test
    void identifiesTheServingReleaseWithoutCachingOrChangingTheEmptyBody() {
        var response = new HealthRestController("v0.2.0", "a".repeat(40)).health();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNull();
        assertThat(response.getHeaders().getFirst("X-Alphaforge-Version")).isEqualTo("v0.2.0");
        assertThat(response.getHeaders().getFirst("X-Alphaforge-Commit")).isEqualTo("a".repeat(40));
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
    }
}
