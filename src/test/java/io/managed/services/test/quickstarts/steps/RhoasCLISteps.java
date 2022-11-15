package io.managed.services.test.quickstarts.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.managed.services.test.Environment;
import io.managed.services.test.cli.CLI;
import io.managed.services.test.cli.CLIDownloader;
import io.managed.services.test.cli.CLIUtils;
import io.managed.services.test.cli.CliGenericException;
import io.managed.services.test.client.exception.ApiGenericException;
import io.managed.services.test.quickstarts.contexts.KafkaInstanceContext;
import io.managed.services.test.quickstarts.contexts.OpenShiftAPIContext;
import io.managed.services.test.quickstarts.contexts.RhoasCLIContext;
import io.managed.services.test.quickstarts.contexts.ServiceAccountContext;
import io.vertx.core.Vertx;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.testng.Assert.assertTrue;


@Log4j2
public class RhoasCLISteps {

    private final Vertx vertx = Vertx.vertx();
    private static final String SERVICE_ACCOUNT_UNIQUE_NAME = "cucumber-sa-qs-" + Environment.LAUNCH_KEY;

    // contexts
    private final KafkaInstanceContext kafkaInstanceContext;
    private final ServiceAccountContext serviceAccountContext;
    private final RhoasCLIContext rhoasCLIContext;
    private final OpenShiftAPIContext openShiftAPIContext;

    // constants
    private static final String CONFIG_MAP_K8FILE_NAME = "rhoas-services.yaml";
    private static final String SECRET_K8FILE_NAME = "rhoas-secrets.yaml";

    public RhoasCLISteps(KafkaInstanceContext kafkaInstanceContext, ServiceAccountContext serviceAccountContext,
                         RhoasCLIContext rhoasCLIContext, OpenShiftAPIContext openShiftAPIContext) {
        this.kafkaInstanceContext = kafkaInstanceContext;
        this.serviceAccountContext = serviceAccountContext;
        this.rhoasCLIContext = rhoasCLIContext;
        this.openShiftAPIContext = openShiftAPIContext;
    }

    @SneakyThrows
    @Given("you are logged in to the rhoas CLI")
    public void you_are_logged_in_to_the_rhoas_cli() {

        var downloader = CLIDownloader.defaultDownloader();

        // download the cli
        var binary = downloader.downloadCLIInTempDir();
        this.rhoasCLIContext.setCli(new CLI(binary));

        log.info("validate cli");
        log.debug(this.rhoasCLIContext.requireCLI().help());

        // log into the CLI
        log.info("login the CLI");
        CLIUtils.login(vertx, this.rhoasCLIContext.requireCLI(), Environment.PRIMARY_USERNAME, Environment.PRIMARY_PASSWORD).get();

        log.info("verify that we are logged-in");
        var kafkas = this.rhoasCLIContext.requireCLI().listKafka();

    }

    @Given("you use your running kafka instance in OpenShift Streams for Apache Kafka")
    public void you_use_your_running_kafka_instance_in_openshift_streams_for_apache_kafka() throws CliGenericException {
        log.info("set kafka instance '{}' to be used", kafkaInstanceContext.requireKafkaInstance().getName());
        log.debug(kafkaInstanceContext.requireKafkaInstance());
        this.rhoasCLIContext.requireCLI().useKafka(kafkaInstanceContext.requireKafkaInstance().getId());
    }

    @When("You generate connection configuration information for the service context as an OpenShift configuration map")
    public void you_generate_connection_configuration_information_for_the_service_context_as_an_openshift_config_map() throws CliGenericException {
        this.rhoasCLIContext.requireCLI().generateKafkaConfigAsConfigMap(CONFIG_MAP_K8FILE_NAME);
        log.info("configuration of kafka created as kubernetes configuration map yaml resource");
    }

    @When("You Create a new service account as an OpenShift secret")
    public void you_generate_a_new_service_account_as_an_openshift_secret() throws CliGenericException, IOException, ApiGenericException {

        var cli = this.rhoasCLIContext.requireCLI();
        cli.createServiceAccountAsSecret(SERVICE_ACCOUNT_UNIQUE_NAME, SECRET_K8FILE_NAME, null);
        log.info("connection credentials created as kubernetes secret resource yaml file");

        // load input (secret k8 resource definition) from generated file
        String stringPathToSecret = cli.getWorkdir() + "/" + SECRET_K8FILE_NAME;
        FileInputStream fis = new FileInputStream(stringPathToSecret);
        String data = IOUtils.toString(fis, StandardCharsets.UTF_8);
        log.debug(data);

        // find value of client ID (service account id)
        final Pattern pattern = Pattern.compile("RHOAS_SERVICE_ACCOUNT_CLIENT_ID: (\\S*)", Pattern.MULTILINE);
        Matcher m = pattern.matcher(data);
        assertTrue(m.find(), "generated k8 resource 'secret' file does not hold expected RHOAS_SERVICE_ACCOUNT_CLIENT_ID value");
        String serviceAccountId = m.group(1);
        log.debug("obtained service account id: '{}'", serviceAccountId);

        // get created service account
        var sa = this.openShiftAPIContext.getSecurityMgmtApi().getServiceAccountById(serviceAccountId);
        this.serviceAccountContext.setServiceAccount(sa);
    }
}
