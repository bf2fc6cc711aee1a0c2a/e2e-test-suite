package io.managed.services.test;

import io.managed.services.test.client.oauth.KeycloakLoginSession;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Properties;

import static io.managed.services.test.TestUtils.bwait;
import static org.testng.Assert.assertNotNull;


/**
 * Test Login to Red Hat SSO and Openshift Identity.
 * <p>
 * <b>Requires:</b>
 * <ul>
 *     <li> PRIMARY_USERNAME
 *     <li> PRIMARY_PASSWORD
 * </ul>
 */
public class SSOAuthTest extends TestBase {
    private static final Logger LOGGER = LogManager.getLogger(SSOAuthTest.class);

    @BeforeClass
    public void bootstrap() {

        LOGGER.info("RelaxTakeItEasy");
        String myPropertyValue = System.getProperty("environment.brokerscount");
        LOGGER.info("orNot");
        System.out.println("my.property value: " + myPropertyValue);

        LOGGER.info("$$$s");
        LOGGER.info("this is execution profile {}", Environment.EXECUTION_PROFILE);
        LOGGER.info("this is desired integration profile group profile {}", TestGroups.INTEGRATION);
        LOGGER.info("$$$e");

        Properties properties = System.getProperties();
        properties.list(System.out);

        LOGGER.info(System.getenv().getOrDefault("maven.activeProfiles", null));
        LOGGER.info(System.getenv().getOrDefault("maven.profile", null));
        LOGGER.info(System.getenv().getOrDefault("profile", null));
        LOGGER.info(System.getenv().getOrDefault("-P", null));
        LOGGER.info(System.getenv().getOrDefault("p", null));


        assertNotNull(Environment.PRIMARY_USERNAME, "the PRIMARY_USERNAME env is null");
        assertNotNull(Environment.PRIMARY_PASSWORD, "the PRIMARY_PASSWORD env is null");
    }

    @Test
    public void testRedHatSSOLogin() throws Throwable {
        var auth = new KeycloakLoginSession(Vertx.vertx(), Environment.PRIMARY_USERNAME, Environment.PRIMARY_PASSWORD);
        bwait(auth.loginToRedHatSSO());
        LOGGER.info("user authenticated against: {}", Environment.REDHAT_SSO_URI);
    }

    @Test
    public void testMASSSOLogin() throws Throwable {
        var auth2 = new KeycloakLoginSession(Vertx.vertx(), Environment.PRIMARY_USERNAME, Environment.PRIMARY_PASSWORD);
        bwait(auth2.loginToOpenshiftIdentity());
        LOGGER.info("user authenticated against: {}", Environment.OPENSHIFT_IDENTITY_URI);
    }

    @Test(groups = "production")
    public void testJoinedLogin() throws Throwable {
        var auth = new KeycloakLoginSession(Vertx.vertx(), Environment.PRIMARY_USERNAME, Environment.PRIMARY_PASSWORD);

        bwait(auth.loginToRedHatSSO());
        LOGGER.info("user authenticated against: {}", Environment.REDHAT_SSO_URI);

        bwait(auth.loginToOpenshiftIdentity());
        LOGGER.info("user authenticated against: {}", Environment.OPENSHIFT_IDENTITY_URI);
    }
}
