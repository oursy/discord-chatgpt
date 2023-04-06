package com.discord.chatgpt.discord;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public interface MessageReceivedEventHook {
  String pointName();

  void onMessageReceived(MessageReceivedEvent messageReceivedEvent);
}
