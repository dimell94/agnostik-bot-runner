package com.agnostik.bot_runner;

import com.agnostik.bot_runner.config.AppProperties;
import com.agnostik.bot_runner.config.LlmProperties;
import com.agnostik.bot_runner.bot.BotManager;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({AppProperties.class, LlmProperties.class})
public class BotRunnerApplication {

	public static void main(String[] args) {
		SpringApplication.run(BotRunnerApplication.class, args);
	}

	@Bean
	CommandLineRunner run(BotManager manager) {
		return args -> {

			System.out.println("Bot runner started");
		};
	}
}
