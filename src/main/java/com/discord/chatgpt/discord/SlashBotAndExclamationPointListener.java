package com.discord.chatgpt.discord;


import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class SlashBotAndExclamationPointListener extends ListenerAdapter {

  private final List<SlashMessageHook> slashMessageHolders;

  private final List<MessageReceivedEventHook> messageReceivedEventHooks;

  public SlashBotAndExclamationPointListener(
      List<SlashMessageHook> slashMessageHolders,
      List<MessageReceivedEventHook> messageReceivedEventHooks) {
    this.slashMessageHolders = slashMessageHolders;
    this.messageReceivedEventHooks = messageReceivedEventHooks;
  }

  @Override
  public void onMessageReceived(@NotNull MessageReceivedEvent event) {
    if (event.getAuthor().isBot()) return;
    final Message message = event.getMessage();
    final String contentRaw = message.getContentRaw();
    final Optional<MessageReceivedEventHook> messageReceivedEventHook =
        messageReceivedEventHooks.stream()
            .filter(
                messageReceivedEventHook1 ->
                    contentRaw.contains(messageReceivedEventHook1.pointName()))
            .findFirst();
    if (messageReceivedEventHook.isEmpty()) {
      return;
    }
    final MessageReceivedEventHook receivedEventHook = messageReceivedEventHook.get();
    try {
      receivedEventHook.onMessageReceived(event);
    } catch (Exception e) {
      e.printStackTrace();
      log.error("onMessageReceived onMessage Error:", e);
      event.getChannel().sendMessage("Processing failed, please try again later！").queue();
    }
  }

  @Override
  public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
    if (event.getGuild() == null) {
      log.error("Only accept commands from guilds");
      return;
    }
    SlashMessageHook slashMessageHolder =
        filter(event.getName()).orElseGet(this::defaultSlashMessageHolder);
    try {
      slashMessageHolder.onMessage(event);
    } catch (Exception e) {
      e.printStackTrace();
      log.error("SlashMessage onMessage Error:", e);
      processingFailed(event);
    }
  }

  private Optional<SlashMessageHook> filter(String eventName) {
    return slashMessageHolders.stream()
        .filter(slashMessageHolder -> eventName.equals(slashMessageHolder.slashName()))
        .findFirst();
  }

  private void processingFailed(SlashCommandInteractionEvent event) {
    event.getHook().sendMessage("Processing failed, please try again later！").queue();
  }

  private SlashMessageHook defaultSlashMessageHolder() {
    return new SlashMessageHook() {
      @Override
      public String slashName() {
        return "";
      }

      @Override
      public void onMessage(SlashCommandInteractionEvent event) {
        event.getHook().sendMessage("command not supported").queue();
      }
    };
  }
}
