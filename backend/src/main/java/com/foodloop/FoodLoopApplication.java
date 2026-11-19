package com.foodloop;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// UserDetailsServiceAutoConfiguration is excluded because logins use our own JWT filter; without
// the exclusion Spring Boot would create an in-memory user and log a generated password.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
@EnableAsync
public class FoodLoopApplication {

    public static void main(String[] args) {
        // The app runs in UTC whatever the machine's zone, so every DATETIME column holds UTC and nothing is
        // shifted by the developer machine's zone (IST). Pick-up times are converted to the listing's own zone
        // only at the edges: emails and the board's "today". (Lesson from DeskHive, where Hibernate shifted
        // local times by the JVM zone.)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(FoodLoopApplication.class, args);
    }
}
