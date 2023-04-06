package com.discord.chatgpt.discord;

import com.neovisionaries.ws.client.WebSocketFactory;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.dv8tion.jda.internal.utils.IOUtil;
import okhttp3.OkHttpClient.Builder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.util.EnumSet;

@Configuration(proxyBeanMethods = false)
@Slf4j
@EnableConfigurationProperties(value = {DiscordProperties.class})
@ConditionalOnProperty(name = "discord.enable", havingValue = "true")
public class DiscordConfiguration {

    private final SlashBotAndExclamationPointListener slashBotAndExclamationListener;

    private final DiscordProperties discordProperties;

    public DiscordConfiguration(
            SlashBotAndExclamationPointListener slashBotAndExclamationListener,
            DiscordProperties discordProperties) {
        this.slashBotAndExclamationListener = slashBotAndExclamationListener;
        this.discordProperties = discordProperties;
    }

    @Bean
    JDA jda() {
        final JDA jda = jda(discordProperties);
        commandListUpdateAction(jda);
        return jda;
    }

    void commandListUpdateAction(JDA jda) {
        CommandListUpdateAction commands = jda.updateCommands();
        commands.addCommands(
                Commands.slash("chat", "Openai ChatGPT")
                        .addOptions(
                                new OptionData(
                                        OptionType.STRING, "message", "The messages to generate chat completions")
                                        .setRequired(true))
                        .addOptions(
                                new OptionData(
                                        OptionType.BOOLEAN,
                                        "private",
                                        "default is private chat, after selecting public, others will see the chat")));
        commands.addCommands(
                Commands.slash("reset-chat", "Clear the current chat context")
        );

        commands.queue();
        log.info("Update Command success!");
    }

    private JDA jda(DiscordProperties discordProperties) {
        final Builder newHttpClientBuilder = IOUtil.newHttpClientBuilder();
        WebSocketFactory socketFactory = new WebSocketFactory();
        if (discordProperties.getProxy().getEnable()) {
            newHttpClientBuilder.proxySelector(
                    ProxySelector.of(
                            new InetSocketAddress(
                                    discordProperties.getProxy().getHost(), discordProperties.getProxy().getPort())));
            socketFactory.getProxySettings().setHost(discordProperties.getProxy().getHost());
            socketFactory.getProxySettings().setPort(discordProperties.getProxy().getPort());
        }
        JDA jda =
                JDABuilder.createLight(
                                discordProperties.getToken(),
                                EnumSet.noneOf(GatewayIntent.class)) // slash commands don't need any intents
                        .setHttpClientBuilder(newHttpClientBuilder)
                        .setWebsocketFactory(socketFactory)
                        .addEventListeners(slashBotAndExclamationListener)
                        .build();
        log.info("Start JDA complete！");
        return jda;
    }
}
