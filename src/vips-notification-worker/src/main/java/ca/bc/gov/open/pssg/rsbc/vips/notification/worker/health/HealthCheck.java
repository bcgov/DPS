package ca.bc.gov.open.pssg.rsbc.vips.notification.worker.health;


import ca.bc.gov.open.jag.ordsvipsclient.api.handler.ApiException;
import ca.bc.gov.open.jagvipsclient.health.HealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class HealthCheck implements HealthIndicator {

    private static final int HTTP_STATUS_OK = 200;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final HealthService healthService;

    public HealthCheck(HealthService healthService) { this.healthService = healthService; }

    @Override
    public Health health() {

        int httpStatusCode = check(); // perform a health check for the ORDS health endpoint
        logger.debug("Health Check returns HTTP Status Code: {}", httpStatusCode);

        if (httpStatusCode != HTTP_STATUS_OK) {
            return Health.down().withDetail("HTTP Status Code", httpStatusCode).build();
        }

        return Health.up().build();
    }

    private int check() {

        try {
            healthService.health();
            return HTTP_STATUS_OK;

        } catch (ApiException ex) {
            logger.error("Health Service did throw exception: ", ex);
            return ex.getCode();
        }
    }
}
