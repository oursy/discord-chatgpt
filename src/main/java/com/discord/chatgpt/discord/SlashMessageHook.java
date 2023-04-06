package com.discord.chatgpt.discord;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public interface SlashMessageHook {

  String slashName();

  void onMessage(SlashCommandInteractionEvent event);

  default String success() {
    return slashName() + " success!";
  }

  default String fail() {
    return slashName() + " fail!";
  }
}
