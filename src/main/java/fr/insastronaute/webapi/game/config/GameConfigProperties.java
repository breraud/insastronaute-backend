package fr.insastronaute.webapi.game.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.game-config")
public class GameConfigProperties {

    private String objectPrefix = "GameData/current/dev/";

    private String releasesObjectPrefix = "GameData/versions/";

    public String getObjectPrefix() {
        return objectPrefix;
    }

    public void setObjectPrefix(String objectPrefix) {
        this.objectPrefix = objectPrefix;
    }

    public String getReleasesObjectPrefix() {
        return releasesObjectPrefix;
    }

    public void setReleasesObjectPrefix(String releasesObjectPrefix) {
        this.releasesObjectPrefix = releasesObjectPrefix;
    }
}
