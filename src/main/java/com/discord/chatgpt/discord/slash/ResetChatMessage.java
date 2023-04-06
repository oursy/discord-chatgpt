package com.discord.chatgpt.discord.slash;


import com.discord.chatgpt.discord.SlashMessageHook;
import com.discord.chatgpt.openai.OpenAiChatMessage;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.stereotype.Component;


@Component
public class ResetChatMessage implements SlashMessageHook {

    private final OpenAiChatMessage openAiChatMessage;

    public ResetChatMessage(OpenAiChatMessage openAiChatMessage) {
        this.openAiChatMessage = openAiChatMessage;
    }


    @Override
    public String slashName() {
        return "reset-chat";
    }

    @Override
    public void onMessage(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        final String userId = event.getInteraction().getMember().getId();
        openAiChatMessage.resetMessage(userId);
        event.getHook().sendMessage(success()).queue();
    }

    @Override
    public String success() {
        return "reset success!";
    }
}
