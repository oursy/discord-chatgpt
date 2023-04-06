package com.discord.chatgpt.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.OkHttpClient.Builder;
import okhttp3.logging.HttpLoggingInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(value = {OpenAiProperties.class})
public class OpenAiConfiguration {

    public static final ObjectMapper OPENAI_OBJECT_MAPPER = defaultObjectMapper();

    @Bean
    OkHttpClient okHttpClient(OpenAiProperties openAiProperties) {
        return buildOkHttpClient(
                openAiProperties);
    }

    private static ObjectMapper defaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        return mapper;
    }

    private static OkHttpClient buildOkHttpClient(OpenAiProperties openAiProperties) {
        final OpenAiProperties.HttpTimeoutConfig timeoutConfig = openAiProperties.getTimeoutConfig();
        final OpenAiProperties.Proxy proxy = openAiProperties.getProxy();
        final Builder builder =
                new Builder()
                        .addInterceptor(new AuthenticationInterceptor(openAiProperties.getKey()))
                        .addInterceptor(new HttpLoggingInterceptor())
                        .connectionPool(new ConnectionPool(5, 1, TimeUnit.SECONDS))
                        .callTimeout(timeoutConfig.getCallTimeout())
                        .writeTimeout(timeoutConfig.getWriteTimeout())
                        .connectTimeout(timeoutConfig.getConnectTimeout())
                        .readTimeout(timeoutConfig.getReadTimeout());
        if (proxy.getEnable()) {
            builder.proxySelector(
                    ProxySelector.of(new InetSocketAddress(proxy.getHost(), proxy.getPort())));
            log.info("ProxySelector: {}:{}", proxy.getHost(), proxy.getPort());
        }
        return builder.build();
    }
}
