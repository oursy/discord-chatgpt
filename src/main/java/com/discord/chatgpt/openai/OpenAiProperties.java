package com.discord.chatgpt.openai;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(value = "openai", ignoreUnknownFields = false)
@Getter
@Setter
public class OpenAiProperties {

    private String key;

    private String baseUrl;

    private final HttpTimeoutConfig timeoutConfig = new HttpTimeoutConfig();

    private final Proxy proxy = new Proxy();

    public Proxy getProxy() {
        return proxy;
    }

    public HttpTimeoutConfig getTimeoutConfig() {
        return timeoutConfig;
    }

    @Getter
    @Setter
    @ToString
    public static class HttpTimeoutConfig {
        private Duration callTimeout = Duration.ofSeconds(30);
        private Duration writeTimeout = Duration.ofSeconds(10);
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration readTimeout = Duration.ofSeconds(10);
    }

    @Getter
    @Setter
    @ToString
    public static class Proxy {

        private Boolean enable = false;

        private String host = "127.0.0.1";

        private int port = 7890;
    }
}
