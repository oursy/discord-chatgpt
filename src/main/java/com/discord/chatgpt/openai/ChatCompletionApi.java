package com.discord.chatgpt.openai;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.RequestBody;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class ChatCompletionApi {

    private final OpenAiProperties openAiProperties;

    private static final String CREATE_CHAT_COMPLETION = "v1/chat/completions";

    private final OkHttpClient okHttpClient;

    public ChatCompletionApi(OpenAiProperties openAiProperties, OkHttpClient okHttpClient) {
        this.openAiProperties = openAiProperties;
        this.okHttpClient = okHttpClient;
    }

    public void sseChatCompletion(String message, EventSourceListener eventSourceListener) {
        final Request request = new Builder().url(openAiProperties.getBaseUrl() + CREATE_CHAT_COMPLETION).post(RequestBody.create(message, okhttp3.MediaType.parse(MediaType.APPLICATION_JSON_VALUE))).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).addHeader("Accept", "text/event-stream").build();
        EventSources.createFactory(okHttpClient).newEventSource(request, eventSourceListener);
    }
}
