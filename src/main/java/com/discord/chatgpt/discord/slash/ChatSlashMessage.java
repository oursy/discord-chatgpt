package com.discord.chatgpt.discord.slash;


import com.discord.chatgpt.discord.SlashMessageHook;
import com.discord.chatgpt.openai.ChatCompletionApi;
import com.discord.chatgpt.openai.OpenAiChatMessage;
import com.discord.chatgpt.openai.OpenAiConfiguration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Slf4j
public class ChatSlashMessage implements SlashMessageHook {

    private final ChatCompletionApi chatCompletionApi;

    private final OpenAiChatMessage openAiChatMessage;

    public ChatSlashMessage(ChatCompletionApi chatCompletionApi, OpenAiChatMessage openAiChatMessage) {
        this.chatCompletionApi = chatCompletionApi;
        this.openAiChatMessage = openAiChatMessage;
    }

    @Override
    public String slashName() {
        return "chat";
    }

    @Override
    public void onMessage(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        final String message = event.getOption("message").getAsString();
        final OptionMapping aPrivate = event.getOption("private");
        boolean pMessage = false;
        if (aPrivate != null) {
            pMessage = aPrivate.getAsBoolean();
        }
        String oneMessage = String.format("> **%s** - <@%s>  \n\n", message, event.getInteraction().getMember().getId());
        if (pMessage) {
            event.getHook().sendMessage(oneMessage).queue();
        } else {
            event.getHook().deleteOriginal().queue();
            event.getMessageChannel().sendMessage(oneMessage).queue();
        }
        sendChatMessage(event.getInteraction().getMember().getId(), message, event, pMessage);
    }

    private void sendChatMessage(String userId, String message, SlashCommandInteractionEvent hook, Boolean privateMessage) {
        openAiChatMessage.addMessage(userId, new ChatMessage(ChatMessageRole.USER.value(), message));
        final ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                .model("gpt-3.5-turbo")
                .user("discord-bot")
                .messages(openAiChatMessage.getBotMessages(userId))
                .stream(true)
                .logitBias(Map.of()).build();
        try {
            final String chatCompletionRequestJson = OpenAiConfiguration.OPENAI_OBJECT_MAPPER.writeValueAsString(chatCompletionRequest);
            AtomicStringBuilder discordMaxMessageContent = new AtomicStringBuilder(Message.MAX_CONTENT_LENGTH - 100);
            AtomicLong singleMessageSize = new AtomicLong();
            // discord message
            AtomicReference<String> messageId = new AtomicReference<>();
            ThreadPauseResume threadPauseResume = new ThreadPauseResume();
            chatCompletionApi.sseChatCompletion(chatCompletionRequestJson, new EventSourceListener() {
                @Override
                public void onClosed(@NotNull EventSource eventSource) {
                    log.warn("onClosed :{}", eventSource);
                }

                @Override
                public void onEvent(@NotNull EventSource eventSource, @Nullable String id, @Nullable String type, @NotNull String data) {
                    log.info("OnEvent:{}", data);
                    final Semaphore pauseSemaphore = threadPauseResume.getPauseSemaphore();
                    try {
                        pauseSemaphore.acquire();
                    } catch (InterruptedException ignored) {
                    }
                    if ("[DONE]".equals(data)) {
                        final String historyMessage = discordMaxMessageContent.history.get();
                        log.info("Reply All Message:{}", historyMessage);
                        openAiChatMessage.addMessage(userId, new ChatMessage(ChatMessageRole.ASSISTANT.value(), historyMessage));
                        pauseSemaphore.release();
                        return;
                    }
                    JsonNode jsonNode;
                    try {
                        jsonNode = OpenAiConfiguration.OPENAI_OBJECT_MAPPER.readTree(data);
                    } catch (JsonProcessingException e) {
                        pauseSemaphore.release();
                        throw new RuntimeException(e);
                    }
                    if (discordMaxMessageContent.sbRef.get().codePoints().count() == 0 && discordMaxMessageContent.counter.get() == 0) {
                        // first role
                        final JsonNode delta = jsonNode.get("choices").get(0).get("delta");
                        if (delta.hasNonNull("role")) {
                            final String accumulateFirstMessage = discordMaxMessageContent.accumulate("Reply in progress. Please wait.");
                            singleMessageSize.set(0L);
                            if (privateMessage) {
                                final InteractionHook eventHook = hook.getHook();
                                eventHook.sendMessage(accumulateFirstMessage).queue(message12 -> {
                                    log.info("Add MessageID:{}", message12.getId());
                                    messageId.set(message12.getId());
                                    discordMaxMessageContent.sbRef.getAndSet("");
                                    pauseSemaphore.release();
                                }, throwable -> pauseSemaphore.release());
                            } else {
                                hook.getMessageChannel().sendMessage(accumulateFirstMessage).queue(message1 -> {
                                    log.info("add MessageID:{}", message1.getId());
                                    messageId.set(message1.getId());
                                    discordMaxMessageContent.sbRef.getAndSet("");
                                    pauseSemaphore.release();
                                }, throwable -> pauseSemaphore.release());
                            }
                        }
                    } else {
                        final JsonNode finish_reason = jsonNode.get("choices").get(0).get("finish_reason");
                        if (!finish_reason.isNull() && "stop".equals(finish_reason.asText())) {
                            sendAppendMessage(hook, privateMessage, discordMaxMessageContent, messageId, singleMessageSize, pauseSemaphore);
                            pauseSemaphore.release();
                            return;
                        }
                        final JsonNode deltaNode = jsonNode.get("choices").get(0).get("delta");
                        if (!deltaNode.hasNonNull("content")) {
                            pauseSemaphore.release();
                            return;
                        }
                        final String content = jsonNode.get("choices").get(0).get("delta").get("content").asText();
                        discordMaxMessageContent.accumulate(content);
                        final long batchMessageSizeCount = singleMessageSize.accumulateAndGet(content.codePoints().count(), Long::sum);
                        if (batchMessageSizeCount < 320 && !discordMaxMessageContent.newMessageBool.getAcquire()) {
                            pauseSemaphore.release();
                            return;
                        }
                        // 发送段落文本
                        if (!discordMaxMessageContent.newMessageBool.getAcquire()) {
                            sendAppendMessage(hook, privateMessage, discordMaxMessageContent, messageId, singleMessageSize, pauseSemaphore);
                        } else {
                            // reset content
                            log.info("New Message:{}", discordMaxMessageContent.sbRef.getAcquire());
                            // send new Message Content
                            if (privateMessage) {
                                final InteractionHook eventHook = hook.getHook();
                                eventHook.sendMessage(discordMaxMessageContent.sbRef.getAcquire()).queue(message12 -> {
                                    log.info("add MessageID:{}", message12.getId());
                                    messageId.set(message12.getId());
                                    pauseSemaphore.release();
                                }, throwable -> pauseSemaphore.release());
                            } else {
                                hook.getHook().sendMessage(discordMaxMessageContent.sbRef.getAcquire()).queue(message1 -> {
                                    log.info("add MessageID:{}", message1.getId());
                                    messageId.set(message1.getId());
                                    pauseSemaphore.release();
                                }, throwable -> pauseSemaphore.release());
                            }
                        }
                    }
                }

                @Override
                public void onFailure(@NotNull EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
                    log.error("onFailure:{},response:{}", t, response);
                    if (privateMessage) {
                        final InteractionHook eventHook = hook.getHook();
                        eventHook.sendMessage("> **Generate Failure!**").queue(message12 -> {
                            log.info("add MessageID:{}", message12.getId());
                        }, Throwable::printStackTrace);
                    } else {
                        hook.getMessageChannel().sendMessage("> **Generate Failure!**").queue(message1 -> {
                            log.info("add MessageID:{}", message1.getId());
                        }, Throwable::printStackTrace);
                    }
                }

                @Override
                public void onOpen(@NotNull EventSource eventSource, @NotNull Response response) {
                    log.info("onOpen:{}", response);
                }
            });
        } catch (JsonProcessingException ignored) {
        }
    }

    private void sendAppendMessage(SlashCommandInteractionEvent hook, Boolean privateMessage, AtomicStringBuilder atomicStringBuilder, AtomicReference<String> messageId, AtomicLong batchMessageSize, final Semaphore pauseSemaphore) {
        final String messageContent = atomicStringBuilder.sbRef.getAcquire();
        atomicStringBuilder.setLastMessageIndex(messageContent.length());
        if (privateMessage) {
            final InteractionHook eventHook = hook.getHook();
            eventHook.editMessageById(messageId.get(), messageContent).queue(message12 -> {
                log.info("Update MessageID:{}", message12.getId());
                messageId.set(message12.getId());
                batchMessageSize.getAndSet(0);
                pauseSemaphore.release();
            }, throwable -> {
                batchMessageSize.getAndSet(0);
                pauseSemaphore.release();
            });
        } else {
            log.info("Content:{},SizeCount:{},", messageContent, batchMessageSize);
            hook.getMessageChannel().editMessageById(messageId.get(), messageContent).queue(message1 -> {
                log.info("Update MessageID:{}", message1.getId());
                messageId.set(message1.getId());
                batchMessageSize.getAndSet(0);
                pauseSemaphore.release();
            }, throwable -> {
                batchMessageSize.getAndSet(0);
                pauseSemaphore.release();
            });
        }
    }

    static class AtomicStringBuilder {

        private final int maxCountSize;

        public final AtomicReference<String> history = new AtomicReference<>("");

        public final AtomicLong lastMessageIndex = new AtomicLong(0);

        public final AtomicInteger counter = new AtomicInteger(0);

        private final AtomicBoolean newMessageBool = new AtomicBoolean(false);

        public final AtomicReference<String> sbRef = new AtomicReference<>("");

        AtomicStringBuilder(int maxCountSize) {
            this.maxCountSize = maxCountSize;
        }

        public void setLastMessageIndex(int length) {
            lastMessageIndex.getAndSet(length);
        }

        public String accumulate(String message) {
            if (counter.getPlain() != 0) {
                history.getAndAccumulate(message, (s, s2) -> s + s2);
            }
            return this.sbRef.accumulateAndGet(message, (oldContent, newContent) -> {
                counter.getAndIncrement();
                if (oldContent.length() < maxCountSize) {
                    newMessageBool.getAndSet(false);
                    return oldContent + newContent;
                } else {
                    log.info("New Message!!!!!!!!!!!! ,lastMessageIndex:{},oldContent:{},newContent:{}", lastMessageIndex, oldContent, newContent);
                    newMessageBool.getAndSet(true);
                    String s = oldContent + newContent;
                    return s.substring(Math.toIntExact(lastMessageIndex.get()));
                }
            });
        }
    }
}
