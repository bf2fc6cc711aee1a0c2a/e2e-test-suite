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
import java.util.function.Function;

/**
 * Class which holds environment variables for system tests.
 */
@Log4j2
public class Environment {

    private static final Map<String, String> VALUES = new HashMap<>();
    private static final JsonNode JSON_DATA = loadConfigurationFile();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm");

    /*
     * Definition of env vars
     */
    private static final String CLOUD_PROVIDER_ENV = "CLOUD_PROVIDER";
    private static final String CONFIG_FILE_ENV = "CONFIG_FILE";

    private static final String LOG_DIR_ENV = "LOG_DIR";

    // TODO remove PRIMARY_USERNAME and PRIMARY_PASSWORD, kept only as long as cli revokes validity of offline tokens when logging out
    private static final String PRIMARY_USERNAME_ENV = "PRIMARY_USERNAME";
    private static final String PRIMARY_PASSWORD_ENV = "PRIMARY_PASSWORD";

    private static final String SECONDARY_USERNAME_ENV = "SECONDARY_USERNAME";
    private static final String ADMIN_USERNAME_ENV = "ADMIN_USERNAME";

    // OFFLINE_TOKENS of users used in tests
    private static final String PRIMARY_OFFLINE_TOKEN_ENV = "PRIMARY_OFFLINE_TOKEN";
    private static final String SECONDARY_OFFLINE_TOKEN_ENV = "SECONDARY_OFFLINE_TOKEN";
    private static final String ALIEN_OFFLINE_TOKEN_ENV = "ALIEN_OFFLINE_TOKEN";
    private static final String DIFF_ORG_OFFLINE_TOKEN_ENV = "DIFF_ORG_OFFLINE_TOKEN";
    private static final String ADMIN_OFFLINE_TOKEN_ENV = "ADMIN_OFFLINE_TOKEN";

    private static final String OPENSHIFT_API_URI_ENV = "OPENSHIFT_API_URI";

    private static final String REDHAT_SSO_URI_ENV = "REDHAT_SSO_URI";
    private static final String REDHAT_SSO_REALM_ENV = "REDHAT_SSO_REALM";
    private static final String REDHAT_SSO_CLIENT_ID_ENV = "REDHAT_SSO_CLIENT_ID";
    private static final String REDHAT_SSO_REDIRECT_URI_ENV = "REDHAT_SSO_REDIRECT_URI";
    private static final String REDHAT_SSO_LOGIN_FORM_ID_ENV = "REDHAT_SSO_LOGIN_FORM_ID";

    private static final String OPENSHIFT_IDENTITY_URI_ENV = "OPENSHIFT_IDENTITY_URI";
    private static final String OPENSHIFT_IDENTITY_REALM_ENV = "OPENSHIFT_IDENTITY_REALM_ENV";
    private static final String OPENSHIFT_IDENTITY_CLIENT_ID_ENV = "OPENSHIFT_IDENTITY_CLIENT_ID_ENV";
    private static final String OPENSHIFT_IDENTITY_REDIRECT_URI_ENV = "OPENSHIFT_IDENTITY_REDIRECT_URI_ENV";
    private static final String OPENSHIFT_IDENTITY_LOGIN_FORM_ID_ENV = "OPENSHIFT_IDENTITY_LOGIN_FORM_ID";

    private static final String DEV_CLUSTER_SERVER_ENV = "DEV_CLUSTER_SERVER";
    private static final String DEV_CLUSTER_NAMESPACE_ENV = "DEV_CLUSTER_NAMESPACE";
    private static final String DEV_CLUSTER_TOKEN_ENV = "DEV_CLUSTER_TOKEN";

    private static final String RHOAS_OPERATOR_NAMESPACE_ENV = "RHOAS_OPERATOR_NAMESPACE";

    private static final String CLI_DOWNLOAD_ORG_ENV = "CLI_DOWNLOAD_ORG";
    private static final String CLI_DOWNLOAD_REPO_ENV = "CLI_DOWNLOAD_REPO";
    private static final String CLI_VERSION_ENV = "CLI_VERSION";
    private static final String CLI_PLATFORM_ENV = "CLI_PLATFORM";
    private static final String CLI_ARCH_ENV = "CLI_ARCH";
    private static final String CLI_EXCLUDE_VERSIONS_ENV = "CLI_EXCLUDE_VERSIONS";
    private static final String GITHUB_TOKEN_ENV = "GITHUB_TOKEN";

    private static final String LAUNCH_KEY_ENV = "LAUNCH_KEY";

    private static final String SKIP_TEARDOWN_ENV = "SKIP_TEARDOWN";

    private static final String SKIP_KAFKA_TEARDOWN_ENV = "SKIP_KAFKA_TEARDOWN";
    private static final String DEFAULT_KAFKA_REGION_ENV = "DEFAULT_KAFKA_REGION";
    private static final String KAFKA_INSECURE_TLS_ENV = "KAFKA_INSECURE_TLS";
    private static final String KAFKA_INSTANCE_API_TEMPLATE_ENV = "KAFKA_INSTANCE_API_TEMPLATE";

    private static final String PROMETHEUS_PUSH_GATEWAY_ENV = "PROMETHEUS_PUSH_GATEWAY";

    private static final String STRATOSPHERE_SCENARIO_1_USER_OFFLINE_TOKEN_ENV  = "STRATOSPHERE_SCENARIO_1_USER_OFFLINE_TOKEN";
    private static final String STRATOSPHERE_SCENARIO_2_USER_OFFLINE_TOKEN_ENV  = "STRATOSPHERE_SCENARIO_2_USER_OFFLINE_TOKEN";
    private static final String STRATOSPHERE_SCENARIO_3_USER_OFFLINE_TOKEN_ENV  = "STRATOSPHERE_SCENARIO_3_USER_OFFLINE_TOKEN";
    private static final String STRATOSPHERE_SCENARIO_4_USER_OFFLINE_TOKEN_ENV  = "STRATOSPHERE_SCENARIO_4_USER_OFFLINE_TOKEN";

    private static final String STRATOSPHERE_SCENARIO_1_AWS_ACCOUNT_ID_ENV = "STRATOSPHERE_SCENARIO_1_AWS_ACCOUNT_ID";
    private static final String STRATOSPHERE_SCENARIO_2_AWS_ACCOUNT_ID_ENV = "STRATOSPHERE_SCENARIO_2_AWS_ACCOUNT_ID";
    private static final String STRATOSPHERE_SCENARIO_3_AWS_ACCOUNT_ID_ENV = "STRATOSPHERE_SCENARIO_3_AWS_ACCOUNT_ID";
    private static final String STRATOSPHERE_SCENARIO_3_RHM_ACCOUNT_ID_ENV = "STRATOSPHERE_SCENARIO_3_RHM_ACCOUNT_ID";
    private static final String STRATOSPHERE_SCENARIO_4_AWS_ACCOUNT_ID_ENV = "STRATOSPHERE_SCENARIO_4_AWS_ACCOUNT_ID";

    private static final String PROMETHEUS_WEB_CLIENT_ACCESS_TOKEN_ENV = "PROMETHEUS_WEB_CLIENT_ACCESS_TOKEN";
    private static final String PROMETHEUS_WEB_CLIENT_ROUTE_ENV = "PROMETHEUS_WEB_CLIENT_ROUTE";

    private static final String STAGE_DATA_PLANE_ADMIN_CLIENT_ID_ENV = "STAGE_DATA_PLANE_ADMIN_CLIENT_ID";
    private static final String STAGE_DATA_PLANE_ADMIN_CLIENT_SECRET_ENV = "STAGE_DATA_PLANE_ADMIN_CLIENT_SECRET";

    private static final String OFFLINE_TOKEN_ENV = "OFFLINE_TOKEN";

    private static final String SDK_KAFKA_NAME_ENV = "SDK_KAFKA_NAME";

    private static final String SDK_CLOUD_PROVIDER_ENV = "SDK_CLOUD_PROVIDER";

    private static final String SDK_PLAN_ENV = "SDK_PLAN";

    /*
     * Setup constants from env variables or set default
     */
    public static final String CLOUD_PROVIDER = getOrDefault(CLOUD_PROVIDER_ENV, "aws");
    
    public static final String SUITE_ROOT = System.getProperty("user.dir");
    public static final Path LOG_DIR = getOrDefault(LOG_DIR_ENV, Paths::get, Paths.get(SUITE_ROOT, "target", "logs")).resolve("test-run-" + DATE_FORMAT.format(LocalDateTime.now()));

    public static final String EXECUTION_PROFILE = System.getProperty("execution.profile");

    // TODO PRIMARY USER is the only user who is still logging using browser, can be replaced with offline token as well once: (https://issues.redhat.com/browse/MGDX-332) is done
    public static final String PRIMARY_USERNAME = getOrDefault(PRIMARY_USERNAME_ENV, null);
    public static final String PRIMARY_PASSWORD = getOrDefault(PRIMARY_PASSWORD_ENV, null);

    public static final String SECONDARY_USERNAME = getOrDefault(SECONDARY_USERNAME_ENV, null);
    public static final String ADMIN_USERNAME = getOrDefault(ADMIN_USERNAME_ENV, null);

    // sso.redhat.com primary user, secondary user, admin user, alien user diff org user respectively (See README.md)
    public static final String PRIMARY_OFFLINE_TOKEN = getOrDefault(PRIMARY_OFFLINE_TOKEN_ENV, null);
    public static final String SECONDARY_OFFLINE_TOKEN = getOrDefault(SECONDARY_OFFLINE_TOKEN_ENV, null);
    // org admin
    public static final String ADMIN_OFFLINE_TOKEN = getOrDefault(ADMIN_OFFLINE_TOKEN_ENV, null);
    public static final String ALIEN_OFFLINE_TOKEN = getOrDefault(ALIEN_OFFLINE_TOKEN_ENV, null);
    // sso.redhat.com different organization user with 1 SKU quota only for stage environment (See README.md)
    public static final String DIFF_ORG_OFFLINE_TOKEN = getOrDefault(DIFF_ORG_OFFLINE_TOKEN_ENV, null);

    // app-services APIs base URI
    public static final String OPENSHIFT_API_URI = getOrDefault(OPENSHIFT_API_URI_ENV, "https://api.stage.openshift.com");

    // sso.redhat.com OAuth ENVs
    public static final String REDHAT_SSO_URI = getOrDefault(REDHAT_SSO_URI_ENV, "https://sso.redhat.com");
    public static final String REDHAT_SSO_REALM = getOrDefault(REDHAT_SSO_REALM_ENV, "redhat-external");
    public static final String REDHAT_SSO_CLIENT_ID = getOrDefault(REDHAT_SSO_CLIENT_ID_ENV, "cloud-services");
    public static final String REDHAT_SSO_REDIRECT_URI = getOrDefault(REDHAT_SSO_REDIRECT_URI_ENV, "https://console.redhat.com");
    public static final String REDHAT_SSO_LOGIN_FORM_ID = getOrDefault(REDHAT_SSO_LOGIN_FORM_ID_ENV, "#rh-password-verification-form");

    // identity.api.openshift.com OAuth ENVs
    // getOrDefault(OPENSHIFT_IDENTITY_URI_ENV, "https://identity.api.stage.openshift.com") -> REDHAT_SSO_URI
    public static final String OPENSHIFT_IDENTITY_URI = getOrDefault(OPENSHIFT_IDENTITY_URI_ENV, REDHAT_SSO_URI);
    //public static final String OPENSHIFT_IDENTITY_URI = getOrDefault(REDHAT_SSO_URI_ENV, REDHAT_SSO_URI);
    // "rhoas" -> REDHAT_SSO_REALM
    public static final String OPENSHIFT_IDENTITY_REALM = getOrDefault(OPENSHIFT_IDENTITY_REALM_ENV, REDHAT_SSO_REALM);
    // "strimzi-ui"
    public static final String OPENSHIFT_IDENTITY_CLIENT_ID = getOrDefault(OPENSHIFT_IDENTITY_CLIENT_ID_ENV, REDHAT_SSO_CLIENT_ID);
    // "https://console.redhat.com/beta/application-services" -> REDHAT_SSO_REDIRECT_URI
    public static final String OPENSHIFT_IDENTITY_REDIRECT_URI = getOrDefault(OPENSHIFT_IDENTITY_REDIRECT_URI_ENV, REDHAT_SSO_REDIRECT_URI);
    // "#rh-password-verification-form" -> REDHAT_SSO_LOGIN_FORM_ID
    public static final String OPENSHIFT_IDENTITY_LOGIN_FORM_ID = getOrDefault(OPENSHIFT_IDENTITY_LOGIN_FORM_ID_ENV, REDHAT_SSO_LOGIN_FORM_ID);

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
    public static final String CLI_EXCLUDE_VERSIONS = getOrDefault(CLI_EXCLUDE_VERSIONS_ENV, "alpha");
    public static final String GITHUB_TOKEN = getOrDefault(GITHUB_TOKEN_ENV, null);

    public static final String LAUNCH_KEY = getOrDefault(LAUNCH_KEY_ENV, "change-me");
    public static final String LAUNCH_SUFFIX = LAUNCH_KEY + "-" + CLOUD_PROVIDER;

    // Skip the whole teardown in some tests, although some of them will need top re-enable it to succeed
    public static final boolean SKIP_TEARDOWN = getOrDefault(SKIP_TEARDOWN_ENV, Boolean::parseBoolean, false);

    // Skip only the Kafka instance delete teardown to speed the local development
    public static final boolean SKIP_KAFKA_TEARDOWN = getOrDefault(SKIP_KAFKA_TEARDOWN_ENV, Boolean::parseBoolean, false);

    // Change the default region where kafka instances will be provisioned if the test suite doesn't decide otherwise
    public static final String DEFAULT_KAFKA_REGION = getOrDefault(DEFAULT_KAFKA_REGION_ENV, "us-east-1");

    public static final boolean KAFKA_INSECURE_TLS = getOrDefault(KAFKA_INSECURE_TLS_ENV, Boolean::parseBoolean, false);
    public static final String KAFKA_INSTANCE_API_TEMPLATE = getOrDefault(KAFKA_INSTANCE_API_TEMPLATE_ENV, "https://admin-server-%s");

    public static final String STRATOSPHERE_SCENARIO_1_USER_OFFLINE_TOKEN = getOrDefault(STRATOSPHERE_SCENARIO_1_USER_OFFLINE_TOKEN_ENV, null);
    public static final String STRATOSPHERE_SCENARIO_2_USER_OFFLINE_TOKEN = getOrDefault(STRATOSPHERE_SCENARIO_2_USER_OFFLINE_TOKEN_ENV, null);
    public static final String STRATOSPHERE_SCENARIO_3_USER_OFFLINE_TOKEN = getOrDefault(STRATOSPHERE_SCENARIO_3_USER_OFFLINE_TOKEN_ENV, null);
    public static final String STRATOSPHERE_SCENARIO_4_USER_OFFLINE_TOKEN = getOrDefault(STRATOSPHERE_SCENARIO_4_USER_OFFLINE_TOKEN_ENV, null);

    public static final String STRATOSPHERE_SCENARIO_1_AWS_ACCOUNT_ID = getOrDefault(STRATOSPHERE_SCENARIO_1_AWS_ACCOUNT_ID_ENV, null);
    public static final String STRATOSPHERE_SCENARIO_2_AWS_ACCOUNT_ID = getOrDefault(STRATOSPHERE_SCENARIO_2_AWS_ACCOUNT_ID_ENV, null);
    public static final String STRATOSPHERE_SCENARIO_3_AWS_ACCOUNT_ID = getOrDefault(STRATOSPHERE_SCENARIO_3_AWS_ACCOUNT_ID_ENV, null);
    public static final String STRATOSPHERE_SCENARIO_3_RHM_ACCOUNT_ID = getOrDefault(STRATOSPHERE_SCENARIO_3_RHM_ACCOUNT_ID_ENV, null);
    public static final String STRATOSPHERE_SCENARIO_4_AWS_ACCOUNT_ID = getOrDefault(STRATOSPHERE_SCENARIO_4_AWS_ACCOUNT_ID_ENV, null);

    public static final String PROMETHEUS_WEB_CLIENT_ACCESS_TOKEN = getOrDefault(PROMETHEUS_WEB_CLIENT_ACCESS_TOKEN_ENV, null);
    public static final String PROMETHEUS_WEB_CLIENT_ROUTE = getOrDefault(PROMETHEUS_WEB_CLIENT_ROUTE_ENV, "https://obs-prometheus-managed-application-services-observability.apps.mk-stage-0622.bd59.p1.openshiftapps.com");


    // admin endpoint credentials (vault-key: clientid, secret) https://gitlab.cee.redhat.com/service/app-interface/-/blob/master/resources/jenkins/managed-services/secrets.yaml#L228-235
    public static final String STAGE_DATA_PLANE_ADMIN_CLIENT_ID = getOrDefault(STAGE_DATA_PLANE_ADMIN_CLIENT_ID_ENV, null);
    public static final String STAGE_DATA_PLANE_ADMIN_CLIENT_SECRET = getOrDefault(STAGE_DATA_PLANE_ADMIN_CLIENT_SECRET_ENV, null);

    public static final String OFFLINE_TOKEN = getOrDefault(OFFLINE_TOKEN_ENV, null);

    public static final String SDK_KAFKA_NAME = getOrDefault(SDK_KAFKA_NAME_ENV, null);

    public static final String SDK_CLOUD_PROVIDER = getOrDefault(SDK_CLOUD_PROVIDER_ENV, null);

    public static final String SDK_PLAN = getOrDefault(SDK_PLAN_ENV, null);

    private Environment() {
    }

    public static Map<String, String> getValues() {
        return VALUES;
    }

    /**
     * Get value from env or  from config or default and parse it to String data type
     *
     * @param varName      variable name
     * @param defaultValue default string value
     * @return value of variable
     */
    private static String getOrDefault(String varName, String defaultValue) {
        return getOrDefault(varName, String::toString, defaultValue);
    }

    /**
     * Get value from env or  from config or default and parse it to defined type
     *
     * @param var          env variable name
     * @param converter    converter from string to defined type
     * @param defaultValue default value if variable is not set in env or config
     * @return value of variable fin defined data type
     */
    private static <T> T getOrDefault(String var, Function<String, T> converter, T defaultValue) {

        var environmentValue = System.getenv(var);

        var configFileValue = Objects.requireNonNull(JSON_DATA).get(var) != null ?
            JSON_DATA.get(var).asText() : null;

        T returnValue = null;
        if (configFileValue != null && !configFileValue.isBlank()) {
            returnValue = converter.apply(configFileValue);
        }

        if (environmentValue != null && !environmentValue.isBlank()) {
            returnValue = converter.apply(environmentValue);
        }

        if (returnValue == null) {
            returnValue = defaultValue;
        }

        VALUES.put(var, String.valueOf(returnValue));
        return returnValue;
    }

    /**
     * Load configuration fom config file
     *
     * @return json object with loaded variables
     */
    private static JsonNode loadConfigurationFile() {
        var envConfigFile = System.getenv(CONFIG_FILE_ENV);
        var configFile = Paths.get(System.getProperty("user.dir"), "config.json").toAbsolutePath().toString();
        if (envConfigFile != null && !envConfigFile.isBlank()) {
            configFile = envConfigFile;
        }

        VALUES.put(CONFIG_FILE_ENV, configFile);

        var mapper = new ObjectMapper();
        try {
            var jsonFile = new File(configFile).getAbsoluteFile();
            return mapper.readTree(jsonFile);
        } catch (IOException ex) {
            log.warn("the json config file '{}' didn't exists or wasn't provided", configFile);
            return mapper.createObjectNode();
        }
    }
}
