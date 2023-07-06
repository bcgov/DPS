package ca.bc.gov.open.pssg.rsbc.dps.dpsemailworker.registration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configure the registration service, if register is false return null otherwhise return the otssoaServiceImpl
 */
@Configuration
@EnableConfigurationProperties(RegistrationProperties.class)
public class RegistrationConfig {

    private final RegistrationProperties registrationProperties;

    public RegistrationConfig(RegistrationProperties registrationProperties) {
        this.registrationProperties = registrationProperties;
    }

    @Bean
    public RegistrationService registrationService() {
        return new NoActionRegistrationService();
    }

}
