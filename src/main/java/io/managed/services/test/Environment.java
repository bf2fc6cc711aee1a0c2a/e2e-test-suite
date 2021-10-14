package io.managed.services.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.managed.services.test.cli.Platform;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;

/**
 * Class which holds environment variables for system tests.
 */
@Log4j2
public class Environment {

    private static final Map<String, String> VALUES = new HashMap<>();
    private static final JsonNode JSON_DATA = loadConfigurationFile();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm");
    private static final String[] REGIONS = {"us-east-1", "eu-west-1"};

    /*
     * Definition of env vars
     */
    private static final String CONFIG_FILE_ENV = "CONFIG_FILE";

    private static final String LOG_DIR_ENV = "LOG_DIR";

    private static final String PRIMARY_USERNAME_ENV = "PRIMARY_USERNAME";
    private static final String PRIMARY_PASSWORD_ENV = "PRIMARY_PASSWORD";
    private static final String SECONDARY_USERNAME_ENV = "SECONDARY_USERNAME";
    private static final String SECONDARY_PASSWORD_ENV = "SECONDARY_PASSWORD";
    private static final String ALIEN_USERNAME_ENV = "ALIEN_USERNAME";
    private static final String ALIEN_PASSWORD_ENV = "ALIEN_PASSWORD";

    private static final String OPENSHIFT_API_URI_ENV = "OPENSHIFT_API_URI";

    private static final String REDHAT_SSO_URI_ENV = "REDHAT_SSO_URI";
    private static final String REDHAT_SSO_REALM_ENV = "REDHAT_SSO_REALM";
    private static final String REDHAT_SSO_CLIENT_ID_ENV = "REDHAT_SSO_CLIENT_ID";
    private static final String REDHAT_SSO_REDIRECT_URI_ENV = "REDHAT_SSO_REDIRECT_URI";

    private static final String OPENSHIFT_IDENTITY_URI_ENV = "OPENSHIFT_IDENTITY_URI";
    private static final String OPENSHIFT_IDENTITY_REALM_ENV = "OPENSHIFT_IDENTITY_REALM_ENV";
    private static final String OPENSHIFT_IDENTITY_CLIENT_ID_ENV = "OPENSHIFT_IDENTITY_CLIENT_ID_ENV";
    private static final String OPENSHIFT_IDENTITY_REDIRECT_URI_ENV = "OPENSHIFT_IDENTITY_REDIRECT_URI_ENV";

    private static final String DEV_CLUSTER_SERVER_ENV = "DEV_CLUSTER_SERVER";
    private static final String DEV_CLUSTER_NAMESPACE_ENV = "DEV_CLUSTER_NAMESPACE";
    private static final String DEV_CLUSTER_TOKEN_ENV = "DEV_CLUSTER_TOKEN";

    private static final String RHOAS_OPERATOR_NAMESPACE_ENV = "RHOAS_OPERATOR_NAMESPACE";

    private static final String CLI_DOWNLOAD_ORG_ENV = "CLI_DOWNLOAD_ORG";
    private static final String CLI_DOWNLOAD_REPO_ENV = "CLI_DOWNLOAD_REPO";
    private static final String CLI_VERSION_ENV = "CLI_VERSION";
    private static final String CLI_PLATFORM_ENV = "CLI_PLATFORM";
    private static final String CLI_ARCH_ENV = "CLI_ARCH";
    private static final String GITHUB_TOKEN_ENV = "GITHUB_TOKEN";

    private static final String LAUNCH_KEY_ENV = "LAUNCH_KEY";

    private static final String SKIP_TEARDOWN_ENV = "SKIP_TEARDOWN";

    private static final String SKIP_KAFKA_TEARDOWN_ENV = "SKIP_KAFKA_TEARDOWN";

    private static final String PROMETHEUS_PUSH_GATEWAY_ENV = "PROMETHEUS_PUSH_GATEWAY";

    private static final String DEFAULT_KAFKA_REGION_ENV = "DEFAULT_KAFKA_REGION";

    private static final String JENKINS_JOB_BUILD_NUMBER_ENV = "BUILD_NUMBER";

    /*
     * Setup constants from env variables or set default
     */
    public static final String SUITE_ROOT = System.getProperty("user.dir");
    public static final Path LOG_DIR = getOrDefault(LOG_DIR_ENV, Paths::get, Paths.get(SUITE_ROOT, "target", "logs")).resolve("test-run-" + DATE_FORMAT.format(LocalDateTime.now()));

    // sso.redhat.com primary user (See README.md)
    public static final String PRIMARY_USERNAME = getOrDefault(PRIMARY_USERNAME_ENV, null);
    public static final String PRIMARY_PASSWORD = getOrDefault(PRIMARY_PASSWORD_ENV, null);

    // sso.redhat.com secondary user (See README.md)
    public static final String SECONDARY_USERNAME = getOrDefault(SECONDARY_USERNAME_ENV, null);
    public static final String SECONDARY_PASSWORD = getOrDefault(SECONDARY_PASSWORD_ENV, null);

    // sso.redhat.com alien user (See README.md)
    public static final String ALIEN_USERNAME = getOrDefault(ALIEN_USERNAME_ENV, null);
    public static final String ALIEN_PASSWORD = getOrDefault(ALIEN_PASSWORD_ENV, null);

    // app-services APIs base URI
    public static final String OPENSHIFT_API_URI = getOrDefault(OPENSHIFT_API_URI_ENV, "https://api.stage.openshift.com");

    // sso.redhat.com OAuth ENVs
    public static final String REDHAT_SSO_URI = getOrDefault(REDHAT_SSO_URI_ENV, "https://sso.redhat.com");
    public static final String REDHAT_SSO_REALM = getOrDefault(REDHAT_SSO_REALM_ENV, "redhat-external");
    public static final String REDHAT_SSO_CLIENT_ID = getOrDefault(REDHAT_SSO_CLIENT_ID_ENV, "cloud-services");
    public static final String REDHAT_SSO_REDIRECT_URI = getOrDefault(REDHAT_SSO_REDIRECT_URI_ENV, "https://cloud.redhat.com");

    // identity.api.openshift.com OAuth ENVs
    public static final String OPENSHIFT_IDENTITY_URI = getOrDefault(OPENSHIFT_IDENTITY_URI_ENV, "https://identity.api.stage.openshift.com");
    public static final String OPENSHIFT_IDENTITY_REALM = getOrDefault(OPENSHIFT_IDENTITY_REALM_ENV, "rhoas");
    public static final String OPENSHIFT_IDENTITY_CLIENT_ID = getOrDefault(OPENSHIFT_IDENTITY_CLIENT_ID_ENV, "strimzi-ui");
    public static final String OPENSHIFT_IDENTITY_REDIRECT_URI = getOrDefault(OPENSHIFT_IDENTITY_REDIRECT_URI_ENV, "https://cloud.redhat.com/beta/application-services");

    public static final String DEV_CLUSTER_SERVER = getOrDefault(DEV_CLUSTER_SERVER_ENV, null);
    public static final String DEV_CLUSTER_NAMESPACE = getOrDefault(DEV_CLUSTER_NAMESPACE_ENV, "mk-e2e-tests");
    public static final String DEV_CLUSTER_TOKEN = getOrDefault(DEV_CLUSTER_TOKEN_ENV, null);

    // used to retrieve the RHOAS operator logs, and it needs to be changed in case the operator is installed in a
    // different namespace
    public static final String RHOAS_OPERATOR_NAMESPACE = getOrDefault(RHOAS_OPERATOR_NAMESPACE_ENV, "openshift-operators");

    public static final String CLI_DOWNLOAD_ORG = getOrDefault(CLI_DOWNLOAD_ORG_ENV, "redhat-developer");
    public static final String CLI_DOWNLOAD_REPO = getOrDefault(CLI_DOWNLOAD_REPO_ENV, "app-services-cli");
    public static final String CLI_VERSION = getOrDefault(CLI_VERSION_ENV, "latest");
    public static final String CLI_PLATFORM = getOrDefault(CLI_PLATFORM_ENV, Platform.getArch().toString());
    public static final String CLI_ARCH = getOrDefault(CLI_ARCH_ENV, "amd64");
    public static final String GITHUB_TOKEN = getOrDefault(GITHUB_TOKEN_ENV, null);

    public static final String LAUNCH_KEY = getOrDefault(LAUNCH_KEY_ENV, "change-me");

    // Skip the whole teardown in some tests, although some of them will need top re-enable it to succeed
    public static final boolean SKIP_TEARDOWN = getOrDefault(SKIP_TEARDOWN_ENV, Boolean::parseBoolean, false);

    // Skip only the Kafka instance delete teardown to speed the local development
    public static final boolean SKIP_KAFKA_TEARDOWN = getOrDefault(SKIP_KAFKA_TEARDOWN_ENV, Boolean::parseBoolean, false);

    // Checks if the tests are being executed from Jenkins
    public static final String BUILD_NUMBER = getOrDefault(JENKINS_JOB_BUILD_NUMBER_ENV, null);

    // Change the default region where kafka instances will be provisioned if the test suite doesn't decide otherwise
    public static final String DEFAULT_KAFKA_REGION = getOrDefault(DEFAULT_KAFKA_REGION_ENV, getKafkaAwsRegion());

    public static final String PROMETHEUS_PUSH_GATEWAY = getOrDefault(PROMETHEUS_PUSH_GATEWAY_ENV, null);

    private Environment() {
    }

    public static Map<String, String> getValues() {
        return VALUES;
    }

    /**
     * Get value from env or from config or default and parse it to String data type
     *
     * @param varName      variable name
     * @param defaultValue default string value
     * @return value of variable
     */
    private static String getOrDefault(String varName, String defaultValue) {
        return getOrDefault(varName, String::toString, defaultValue);
    }

    /**
     * Get value from env or from config or default and parse it to defined type
     *
     * @param var          env variable name
     * @param converter    converter from string to defined type
     * @param defaultValue default value if variable is not set in env or config
     * @return value of variable fin defined data type
     */
    private static <T> T getOrDefault(String var, Function<String, T> converter, T defaultValue) {
        var value = System.getenv(var) != null ?
                System.getenv(var) :
                (Objects.requireNonNull(JSON_DATA).get(var) != null ?
                        JSON_DATA.get(var).asText() :
                        null);
        T returnValue = defaultValue;
        if (value != null && !value.isEmpty()) {
            returnValue = converter.apply(value);
        }
        VALUES.put(var, String.valueOf(returnValue));
        return returnValue;
    }

    /**
     * Load configuration from config file
     *
     * @return json object with loaded variables
     */
    private static JsonNode loadConfigurationFile() {
        var config = System.getenv().getOrDefault(CONFIG_FILE_ENV,
                Paths.get(System.getProperty("user.dir"), "config.json").toAbsolutePath().toString());

        VALUES.put(CONFIG_FILE_ENV, config);

        var mapper = new ObjectMapper();
        try {
            var jsonFile = new File(config).getAbsoluteFile();
            return mapper.readTree(jsonFile);
        } catch (IOException ex) {
            log.info("the json config file didn't exists or wasn't provided");
            return mapper.createObjectNode();
        }
    }

    /**
     * Get the aws region in which to deploy a kafka instance
     *
     * @return an aws region
     */
    public static String getKafkaAwsRegion() {
        return (BUILD_NUMBER == null) ? REGIONS[0] : REGIONS[new Random().nextInt(REGIONS.length)];
    }
}
