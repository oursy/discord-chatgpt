package com.discord.chatgpt.openai;

import com.google.common.collect.EvictingQueue;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OpenAiChatMessage {

    private static final int LAST_MESSAGE_LENGTH = 10;

    @SuppressWarnings("UnstableApiUsage")
    private static final Map<String, EvictingQueue<ChatMessage>> evictingMessageMapQueue = new ConcurrentHashMap<>();

    public List<ChatMessage> defaultSystemMessage() {
        return List.of(new ChatMessage(ChatMessageRole.SYSTEM.value(),
                "Please do not add newline at the beginning of your reply content"));
    }

    /**
     * 获取当前要发送消息
     *
     * @return List<Message>
     */
    @SuppressWarnings("UnstableApiUsage")
    public List<ChatMessage> getBotMessages(String discordUserId) {
        List<ChatMessage> messages = new LinkedList<>(defaultSystemMessage());
        EvictingQueue<ChatMessage> chatMessages = evictingMessageMapQueue.get(discordUserId);
        if (chatMessages == null) {
            chatMessages = EvictingQueue.create(LAST_MESSAGE_LENGTH);
            evictingMessageMapQueue.put(discordUserId, chatMessages);
        }
        final List<ChatMessage> botMessages = chatMessages.stream().toList();
        messages.addAll(botMessages);
        return messages;
    }

    /***
     * 用户提问稍后或者将提问内容追加到 message Queue
     * @param message 消息
     */
    @SuppressWarnings("UnstableApiUsage")
    public void addMessage(String discordUserId, ChatMessage message) {
        EvictingQueue<ChatMessage> chatMessages = evictingMessageMapQueue.get(discordUserId);
        if (chatMessages == null) {
            chatMessages = EvictingQueue.create(LAST_MESSAGE_LENGTH);
            chatMessages.add(message);
            evictingMessageMapQueue.put(discordUserId, chatMessages);
        } else {
            chatMessages.add(message);
        }
    }

    public void resetMessage(String discordUserId) {
        evictingMessageMapQueue.remove(discordUserId);
    }
}
