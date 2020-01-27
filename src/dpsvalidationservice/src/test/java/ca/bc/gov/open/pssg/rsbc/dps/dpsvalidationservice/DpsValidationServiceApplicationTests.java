package ca.bc.gov.open.pssg.rsbc.dps.dpsvalidationservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author nancymz
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class DpsValidationServiceApplicationTests {

    @Autowired
    private TestRestTemplate restTemplate;
    @LocalServerPort
    private int port;

    @Test
    void getValidOpenDFCMCase() throws Exception {
        String request = "/getValidOpenDFCMCase/?driversLicense=1234567&surcode=345";
        assertThat(this.restTemplate.getForObject("http://localhost:" + port + "/dpsvalidationservice" + request,
                String.class).contains("getValidOpenDFCMCase"));
    }

}
