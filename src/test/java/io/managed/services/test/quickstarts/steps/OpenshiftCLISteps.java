package io.managed.services.test.quickstarts.steps;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.managed.services.test.Environment;
import io.managed.services.test.quickstarts.QuarkusApplicationTest;
import io.managed.services.test.quickstarts.contexts.RhoasCLIContext;
import lombok.extern.log4j.Log4j2;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import static org.testng.Assert.assertNotNull;

@Log4j2
public class OpenshiftCLISteps {

    private static final String APP_YAML_SERVICE_PATH = "quarkus/cliconfig/service.yaml";
    private static final String APP_YAML_DEPLOYMENT_PATH = "quarkus/cliconfig/deployment.yaml";

    DefaultOpenShiftClient oc;

    private String ocNamespaceName;

    private final RhoasCLIContext rhoasCLIContext;

    public OpenshiftCLISteps(RhoasCLIContext rhoasCLIContext) {
        this.rhoasCLIContext = rhoasCLIContext;
    }

    @Given("you are logged in to the oc CLI")
    public void you_are_logged_in_to_the_openshift_cli() {

        assertNotNull(Environment.PRIMARY_USERNAME, "the PRIMARY_USERNAME env is null");
        assertNotNull(Environment.PRIMARY_PASSWORD, "the PRIMARY_PASSWORD env is null");
        assertNotNull(Environment.DEV_CLUSTER_SERVER, "the DEV_CLUSTER_SERVER env is null");
        assertNotNull(Environment.DEV_CLUSTER_TOKEN, "the DEV_CLUSTER_TOKEN env is null");

        // OC
        // TODO change to the usage of KubernetesClientBuilder, more at https://developers.redhat.com/articles/2022/07/15/new-http-clients-java-generator-and-more-fabric8-600#
        log.info("initialize openshift client");
        var config = new ConfigBuilder()
            .withMasterUrl(Environment.DEV_CLUSTER_SERVER)
            .withOauthToken(Environment.DEV_CLUSTER_TOKEN)
            .withNamespace(Environment.DEV_CLUSTER_NAMESPACE)
            .withTrustCerts(true)
            .build();
        oc = new DefaultOpenShiftClient(config);
    }

    @When("you use your oc project {word}")
    public void you_use_your_oc_project(String project) {
        this.ocNamespaceName = project;
    }

    @When("you apply openshift resources from your workdir")
    public void youAppliedOpenshiftResources(DataTable filesToBeApplied) throws FileNotFoundException {

        // obtain all resources which are to be applied
        var resources = filesToBeApplied.asList();

        // get workdir from the cli steps
        var workdir =  this.rhoasCLIContext.requireCLI().getWorkdir();

        for (String singleResource : resources) {
            String wholePath = workdir + "/" + singleResource;
            File file = new File(wholePath);
            InputStream inputStream = new FileInputStream(file);
            oc.load(inputStream).inNamespace(this.ocNamespaceName).createOrReplace();
        }
    }

    @When("you deploy oc resources for running your quarkus application connected using NodePort")
    public void youDeployOcResources() {
        log.info("deploy the something app");
        var serviceResourceInputStream = QuarkusApplicationTest.class.getClassLoader().getResourceAsStream(APP_YAML_SERVICE_PATH);
        var deploymentResourceInputStream = QuarkusApplicationTest.class.getClassLoader().getResourceAsStream(APP_YAML_DEPLOYMENT_PATH);

        // service is applied as services are prone to stay pending in deletion state for several hours
        try {
            oc.services().load(serviceResourceInputStream).createOrReplace();
        } catch (KubernetesClientException e) {
            log.warn(e.getMessage());
        }
        oc.resourceList(oc.load(deploymentResourceInputStream).get()).createOrReplace();
    }

    // teardown
    @After(order = 10100)
    public void cleanOcResources() {
        log.info("clear OC resources");

        // deployment of quarkus application
        try {
            var deploymentResourceInputStream = QuarkusApplicationTest.class.getClassLoader().getResourceAsStream(APP_YAML_DEPLOYMENT_PATH);
            oc.resourceList(oc.load(deploymentResourceInputStream).get()).delete();
        } catch (Exception ignored) { }
    }
}
