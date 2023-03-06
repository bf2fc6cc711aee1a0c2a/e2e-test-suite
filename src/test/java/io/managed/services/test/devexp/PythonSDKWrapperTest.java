package io.managed.services.test.devexp;

import com.openshift.cloud.api.kas.models.KafkaRequest;
import io.managed.services.test.Environment;
import io.managed.services.test.pythonSDKWrapper.PythonSDKWrapper;
import io.managed.services.test.pythonSDKWrapper.kafkamgmt.utils.SDKUtils;
import lombok.SneakyThrows;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;


/**
 * Test the application services CLI[1] kafka commands.
 * <p>
 * The tests download the CLI from GitHub to the local machine where the test suite is running
 * and perform all operations using the CLI.
 * <p>
 * By default the latest version of the CLI is downloaded otherwise a specific version can be set using
 * the CLI_VERSION env. The CLI platform (linux, mac, win) and arch (amd64, arm) is automatically detected,
 * or it can be enforced using the CLI_PLATFORM and CLI_ARCH env.
 * <p>
 * 1. https://github.com/redhat-developer/app-services-cli
 * <p>
 * <b>Requires:</b>
 * <ul>
 *     <li> PRIMARY_USERNAME
 *     <li> PRIMARY_PASSWORD
 * </ul>
 */
@Test
public class PythonSDKWrapperTest {
    private static final Logger LOGGER = LogManager.getLogger(KafkaCLITest.class);

    private static final String KAFKA_INSTANCE_NAME = "sdk-e2e-test-instance-" + Environment.LAUNCH_KEY;

    private PythonSDKWrapper pythonSDK;

    private KafkaRequest kafka;

    @BeforeClass
    public void bootstrap() {
        assertNotNull(Environment.OFFLINE_TOKEN, "the OFFLINE_TOKEN env is null");
        Path userDirectory = Paths.get("")
                .toAbsolutePath();
        this.pythonSDK = new PythonSDKWrapper(userDirectory);
    }


    @Test()
    @SneakyThrows
    public void testCreateKafkaInstance() {

        LOGGER.info("create kafka instance with name {}", KAFKA_INSTANCE_NAME);
        var k = pythonSDK.createKafka(KAFKA_INSTANCE_NAME, Environment.CLOUD_PROVIDER, Environment.DEFAULT_KAFKA_REGION, Environment.SDK_PLAN);
        LOGGER.debug(k);

        LOGGER.info("wait for kafka instance: {}", k.getId());
        kafka = SDKUtils.waitUntilKafkaIsReady(pythonSDK, k.getId());
        LOGGER.debug(kafka);
    }

    @SneakyThrows
    public void testListKafkaInstances() {

        var list = pythonSDK.listKafka();
        LOGGER.debug(list);

        var exists = list.getItems().stream()
                .filter(k -> KAFKA_INSTANCE_NAME.equals(k.getName()))
                .findAny();
        assertTrue(exists.isPresent());
    }


    @Test(dependsOnMethods = "testCreateKafkaInstance", priority = 3, enabled = true)
    @SneakyThrows
    public void testDeleteKafkaInstance() {

        LOGGER.info("delete kafka instance '{}'", kafka.getId());
        pythonSDK.deleteKafka(kafka.getId());

        SDKUtils.waitUntilKafkaIsDeleted(pythonSDK, kafka.getId());
    }
}
