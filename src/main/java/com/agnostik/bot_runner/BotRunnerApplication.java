package com.agnostik.bot_runner;

import com.agnostik.bot_runner.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AppProperties.class)
public class BotRunnerApplication {

	public static void main(String[] args) {
		SpringApplication.run(BotRunnerApplication.class, args);
	}

}
