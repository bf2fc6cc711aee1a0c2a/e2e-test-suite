package io.managed.services.test.quickstarts.steps;

import io.cucumber.java.en.Given;
import io.managed.services.test.Environment;
import io.managed.services.test.client.kafkamgmt.KafkaMgmtApiUtils;
import io.managed.services.test.client.oauth.KeycloakLoginSession;
import io.managed.services.test.client.securitymgmt.SecurityMgmtAPIUtils;
import io.managed.services.test.quickstarts.contexts.OpenShiftAPIContext;
import io.managed.services.test.quickstarts.contexts.UserContext;
import lombok.extern.log4j.Log4j2;

import static io.managed.services.test.TestUtils.bwait;
import static org.testng.Assert.assertNotNull;

@Log4j2
public class LoginSteps {

    private final UserContext user;
    private final OpenShiftAPIContext openShiftAPI;

    public LoginSteps(UserContext user, OpenShiftAPIContext openShiftAPI) {
        this.user = user;
        this.openShiftAPI = openShiftAPI;
    }

    @Given("you have a Red Hat account")
    public void you_have_a_red_hat_account() {
        assertNotNull(Environment.PRIMARY_USERNAME, "the PRIMARY_USERNAME env is null");
        assertNotNull(Environment.PRIMARY_PASSWORD, "the PRIMARY_PASSWORD env is null");
    }

    @Given("you’re logged in to the OpenShift Streams for Apache Kafka web console")
    public void you_re_logged_in_to_the_open_shift_streams_for_apache_kafka_web_console() throws Throwable {

        var keycloakLoginSession = new KeycloakLoginSession(Environment.PRIMARY_USERNAME, Environment.SECONDARY_PASSWORD);

        // login to Red Hat SSO
        log.info("login to Red Hat SSO");
        var redHatUser = bwait(keycloakLoginSession.loginToRedHatSSO());

        // login to MAS SSO
        log.info("login to MAS SSO");
        var masUser = bwait(keycloakLoginSession.loginToOpenshiftIdentity());

        // initialize APIs
        log.info("initialize KafkaMgmtAPI with base path '{}'", Environment.OPENSHIFT_API_URI);
        var kafkaMgmtApi = KafkaMgmtApiUtils.kafkaMgmtApi(Environment.OPENSHIFT_API_URI, redHatUser);

        log.info("initialize SecurityMgmtAPI with base path '{}'", Environment.OPENSHIFT_API_URI);
        var securityMgmtApi = SecurityMgmtAPIUtils.securityMgmtApi(Environment.OPENSHIFT_API_URI, redHatUser);

        // update contexts
        user.setKeycloakLoginSession(keycloakLoginSession);
        user.setRedHatUser(redHatUser);
        user.setMasUser(masUser);
        openShiftAPI.setKafkaMgmtApi(kafkaMgmtApi);
        openShiftAPI.setSecurityMgmtApi(securityMgmtApi);
    }
}
