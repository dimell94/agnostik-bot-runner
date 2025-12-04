package com.agnostik.bot_runner.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String baseUrl;
    private String wsEndpoint;
    private List<BotCredential> bots;


    @Data
    public static class BotCredential {

        private String username;
        private String password;
        private Boolean useLlm = true;
        private String fixedText;
    }
}
