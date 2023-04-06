package com.discord.chatgpt.discord;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(value = "discord", ignoreUnknownFields = false)
@Setter
@Getter
public class DiscordProperties {
  private Boolean enable = false;

  private String token;

  private Proxy proxy = new Proxy();

  @Getter
  @Setter
  public static class Proxy {

    private Boolean enable = true;

    private String host = "127.0.0.1";

    private int port = 7890;
  }
}
