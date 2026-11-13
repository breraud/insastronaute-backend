package fr.insastronaute.webapi.challenge.auto.configuration;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

class AutoChallengePropertiesTest {
    @Test void rejectsInvalidBatchSizeAndLockDuration() {
        AutoChallengeProperties properties = new AutoChallengeProperties();
        properties.setBatchSize(0);
        properties.setLockDuration(Duration.ZERO);
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertThat(validatorFactory.getValidator().validate(properties))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactlyInAnyOrder("batchSize", "lockDuration");
        }
    }
}
