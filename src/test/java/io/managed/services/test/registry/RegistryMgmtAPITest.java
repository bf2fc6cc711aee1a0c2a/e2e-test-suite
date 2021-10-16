package io.managed.services.test.registry;

import com.openshift.cloud.api.srs.models.RegistryCreate;
import com.openshift.cloud.api.srs.models.Registry;
import io.managed.services.test.Environment;
import io.managed.services.test.TestBase;
import io.managed.services.test.client.exception.ApiGenericException;
import io.managed.services.test.client.registrymgmt.RegistryMgmtApi;
import io.managed.services.test.client.registrymgmt.RegistryMgmtApiUtils;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;

import static io.managed.services.test.TestUtils.assumeTeardown;
import static io.managed.services.test.TestUtils.bwait;
import static io.managed.services.test.TestUtils.message;
import static io.managed.services.test.client.registry.RegistryClientUtils.registryClient;
import static io.managed.services.test.client.registrymgmt.RegistryMgmtApiUtils.cleanRegistry;
import static io.managed.services.test.client.registrymgmt.RegistryMgmtApiUtils.waitUntilRegistryIsReady;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Test Registry Mgmt API.
 * <p>
 * <b>Requires:</b>
 * <ul>
 *     <li> PRIMARY_USERNAME
 *     <li> PRIMARY_PASSWORD
 * </ul>
 */
public class RegistryMgmtAPITest extends TestBase {
    private static final Logger LOGGER = LogManager.getLogger(RegistryMgmtAPITest.class);

    private static final String SERVICE_REGISTRY_NAME = "mk-e2e-sr-" + Environment.LAUNCH_KEY;
    private static final String SERVICE_REGISTRY_2_NAME = "mk-e2e-sr2-" + Environment.LAUNCH_KEY;
    private static final String ARTIFACT_SCHEMA = "{\"type\":\"record\",\"name\":\"Greeting\",\"fields\":[{\"name\":\"Message\",\"type\":\"string\"},{\"name\":\"Time\",\"type\":\"long\"}]}";

    private RegistryMgmtApi registryMgmtApi;
    private Registry registry;

    @BeforeClass
    public void bootstrap() throws Throwable {
        assertNotNull(Environment.PRIMARY_USERNAME, "the PRIMARY_USERNAME env is null");
        assertNotNull(Environment.PRIMARY_PASSWORD, "the PRIMARY_PASSWORD env is null");

        registryMgmtApi = bwait(RegistryMgmtApiUtils.registryMgmtApi(
            Environment.PRIMARY_USERNAME,
            Environment.PRIMARY_PASSWORD));
    }

    @AfterClass(alwaysRun = true)
    public void teardown() {
        assumeTeardown();

        try {
            cleanRegistry(registryMgmtApi, SERVICE_REGISTRY_NAME);
        } catch (Throwable t) {
            LOGGER.error("clean service registry error: ", t);
        }

        try {
            cleanRegistry(registryMgmtApi, SERVICE_REGISTRY_2_NAME);
        } catch (Throwable t) {
            LOGGER.error("clean service registry error: ", t);
        }
    }

    @Test
    public void testCreateRegistry() throws Exception {

        var registryCreateRest = new RegistryCreate()
            .name(SERVICE_REGISTRY_NAME)
            .description("Hello World!");

        var registry = registryMgmtApi.createRegistry(registryCreateRest);
        LOGGER.info("service registry: {}", Json.encode(registry));

        registry = waitUntilRegistryIsReady(registryMgmtApi, registry.getId());
        LOGGER.info("ready service registry: {}", Json.encode(registry));

        assertNotNull(registry.getRegistryUrl());

        this.registry = registry;
    }

    @Test(dependsOnMethods = "testCreateRegistry")
    public void testCreateArtifact() throws Throwable {
        var registryClient = bwait(registryClient(Vertx.vertx(), registry.getRegistryUrl(),
            Environment.PRIMARY_USERNAME, Environment.PRIMARY_PASSWORD));

        LOGGER.info("create artifact on registry");
        var artifactMetaData = registryClient.createArtifact(null, null, IOUtils.toInputStream(ARTIFACT_SCHEMA, StandardCharsets.UTF_8));

        assertEquals(artifactMetaData.getName(), "Greeting");
    }

    @Test(dependsOnMethods = "testCreateRegistry")
    public void testListRegistries() throws ApiGenericException {

        // List registries
        var registries = registryMgmtApi.getRegistries(null, null, null, null);

        assertTrue(registries.getItems().size() > 0, "registries list is empty");

        var found = registries.getItems().stream()
            .anyMatch(r -> SERVICE_REGISTRY_NAME.equals(r.getName()));
        assertTrue(found, message("{} not found in registries list: {}", SERVICE_REGISTRY_NAME, Json.encode(registries)));
    }

    @Test(dependsOnMethods = "testCreateRegistry")
    public void testSearchRegistry() throws ApiGenericException {

        // Search registry by name
        var registries = registryMgmtApi.getRegistries(null, null, null,
            String.format("name = %s", SERVICE_REGISTRY_NAME));

        assertTrue(registries.getItems().size() > 0, "registries list is empty");
        assertTrue(registries.getItems().size() < 2, message("registries list contains more than one result: {}", Json.encode(registries)));
        assertEquals(registries.getItems().get(0).getName(), SERVICE_REGISTRY_NAME);
    }

    @Test(dependsOnMethods = "testCreateRegistry", enabled = false)
    public void testFailToCreateRegistryIfItAlreadyExist() {
        // TODO: Enable after https://github.com/bf2fc6cc711aee1a0c2a/srs-fleet-manager/issues/75

        var registryCreateRest = new RegistryCreate()
            .name(SERVICE_REGISTRY_NAME);

        assertThrows(() -> registryMgmtApi.createRegistry(registryCreateRest));
    }

    @Test(priority = 1, dependsOnMethods = "testCreateRegistry")
    public void testDeleteRegistry() throws Throwable {

        LOGGER.info("delete registry '{}'", registry.getId());
        registryMgmtApi.deleteRegistry(registry.getId());

        LOGGER.info("verify the registry '{}' has been deleted", registry.getId());
        RegistryMgmtApiUtils.waitUntilRegistryIsDeleted(registryMgmtApi, registry.getId());
    }

    @Test(priority = 2)
    public void testDeleteProvisioningRegistry() throws Throwable {

        var registryCreateRest = new RegistryCreate()
            .name(SERVICE_REGISTRY_NAME);

        LOGGER.info("create kafka instance: {}", SERVICE_REGISTRY_2_NAME);
        var registryToDelete = registryMgmtApi.createRegistry(registryCreateRest);

        LOGGER.info("delete the registry: {}", registryToDelete.getId());
        registryMgmtApi.deleteRegistry(registryToDelete.getId());

        LOGGER.info("verify the registry '{}' has been deleted", registryToDelete.getId());
        RegistryMgmtApiUtils.waitUntilRegistryIsDeleted(registryMgmtApi, registryToDelete.getId());
    }
}
