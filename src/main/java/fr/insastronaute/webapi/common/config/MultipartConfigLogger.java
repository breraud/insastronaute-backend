package fr.insastronaute.webapi.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.boot.tomcat.autoconfigure.TomcatServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MultipartConfigLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultipartConfigLogger.class);

    @Bean
    ApplicationRunner logMultipartConfiguration(
            MultipartProperties multipartProperties,
            TomcatServerProperties tomcatServerProperties
    ) {
        return ignored -> LOGGER.info(
                "Multipart upload limits: max-file-size={}, max-request-size={}, max-part-count={}.",
                multipartProperties.getMaxFileSize(),
                multipartProperties.getMaxRequestSize(),
                tomcatServerProperties.getMaxPartCount()
        );
    }
}
