package com.gitinsight.gitinsight_backend.config;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import java.util.List;
@Configuration
public class AiConfig {

    @Value("${openrouter.api.keys}")
    private List<String> openRouterKeys;

    @Value("${openrouter.base-url:https://openrouter.ai/api/v1}")
    private String openRouterBaseUrl;

    @Value("${openrouter.model:openai/gpt-4o-mini}")
    private String openRouterModel;

    @Bean
    @Primary
    public ChatClient chatClient() {
        // Use the first OpenRouter key for the ChatClient
        String firstKey = openRouterKeys.get(0).trim();

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(openRouterBaseUrl)
                .apiKey(firstKey)
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(openRouterModel)
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();

        return ChatClient.builder(chatModel).build();
    }
}


//package com.gitinsight.gitinsight_backend.config;
//
//import org.springframework.ai.chat.client.ChatClient;
//import org.springframework.ai.chat.model.ChatModel;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.context.annotation.Primary;
//
//@Configuration
//public class AiConfig {
//
//    // We explicitly tell Spring: "Use openAiChatModel when building the ChatClient"
//    @Bean
//    @Primary
//    public ChatClient chatClient(@Qualifier("openAiChatModel") ChatModel chatModel) {
//        return ChatClient.builder(chatModel).build();
//    }
//}