package org.jboss.jbossts.resttxbridge.inbound.test.junit;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.net.HttpURLConnection;

import org.jboss.arquillian.container.test.api.Config;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.jbossts.resttxbridge.inbound.test.common.DummyParticipant;
import org.jboss.jbossts.star.provider.HttpResponseException;
import org.jboss.jbossts.star.util.TxSupport;
import org.jboss.resteasy.client.ClientRequest;
import org.jboss.resteasy.client.ClientResponse;
import org.jboss.resteasy.spi.Link;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * 
 * @author Gytis Trikleris
 * 
 */
@RunWith(Arquillian.class)
public class InboundBridgeRecoveryTest {

    private static final String MANIFEST_MF = "Manifest-Version: 1.0\n"
            + "Dependencies: org.jboss.jts, org.hornetq, org.jboss.logging\n";

    private static final String DEPLOYMENT_NAME = "rest-tx-bridge-recovery-test";

    private static final String REST_TX_DEPLOYMENT_NAME = "rest-tx";

    private static final String CONTAINER_NAME = "jbossas-manual";

    private static final String TXN_MGR_URL = "http://localhost:8080/rest-tx/tx/transaction-manager";

    private static final String BASE_URL = "http://localhost:8080/" + DEPLOYMENT_NAME;

    private static final String DUMMY_URL = BASE_URL + "/" + DummyParticipant.PARTICIPANT_SEGMENT;

    private String javaVmArguments = "-Xms64m -Xmx512m -XX:MaxPermSize=256m -Dcom.arjuna.ats.arjuna.recovery.periodicRecoveryPeriod=10 ";
    
    private String bytemanArgs = "-Dorg.jboss.byteman.verbose -Djboss.modules.system.pkgs=org.jboss.byteman -Dorg.jboss.byteman.transform.all -javaagent:target/test-classes/lib/byteman.jar=script:target/test-classes/scripts/@BMScript@.btm,boot:target/test-classes/lib/byteman.jar,listener:true ";

    private String bytemanArguments;

    @ArquillianResource
    private ContainerController controller;

    @ArquillianResource
    private Deployer deployer;

    @Deployment(name = DEPLOYMENT_NAME, testable = false, managed = false)
    @TargetsContainer(CONTAINER_NAME)
    public static Archive<?> createTestArchive() {
        WebArchive archive = ShrinkWrap.create(WebArchive.class, DEPLOYMENT_NAME + ".war")
                .addAsWebInfResource(new File("src/test/webapp", "WEB-INF/web-recovery.xml"), "web.xml")
                .addPackages(false, "org.jboss.jbossts.resttxbridge.annotation")
                .addPackages(false, "org.jboss.jbossts.resttxbridge.inbound")
                .addPackages(false, "org.jboss.jbossts.resttxbridge.inbound.provider")
                .addPackages(false, "org.jboss.jbossts.resttxbridge.inbound.test.recovery")
                .addPackages(false, "org.jboss.jbossts.resttxbridge.inbound.test.common")
                .addPackages(true, "org.jboss.jbossts.star").setManifest(new StringAsset(MANIFEST_MF));

        return archive;
    }

    @Deployment(name = REST_TX_DEPLOYMENT_NAME, testable = false, managed = false)
    @TargetsContainer(CONTAINER_NAME)
    public static Archive<?> createRestTxArchive() {
        WebArchive archive = ShrinkWrap.createFromZipFile(WebArchive.class, new File(
                "target/test-classes/lib/rest-tx-web-5.0.0.M2-SNAPSHOT.war"));

        return archive;
    }

    @Before
    public void setUp() {
        // javaVmArguments += " -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5006 ";

        File file = new File("testlog");
        if (file.isFile() && file.exists()) {
            file.delete();
        }

        // Ensure ObjectStore is empty:
        String jbossHome = System.getenv("JBOSS_HOME");
        if (jbossHome == null) {
            Assert.fail("$JBOSS_HOME not set");
        } else {
            File objectStore = new File(jbossHome + File.separator + "standalone" + File.separator + "data" + File.separator
                    + "tx-object-store");
            System.out.println("Deleting: " + objectStore.getPath());

            if (objectStore.exists()) {
                boolean success = deleteDirectory(objectStore);
                if (!success) {
                    System.err.println("Failed to remove tx-object-store");
                    Assert.fail("Failed to remove tx-object-store: " + objectStore.getPath());
                } else {
                    System.out.println("remove tx-object-store: " + objectStore.getPath());
                }
            }

        }
    }

    @Test
    public void testCrashWithThreeLogEntries() throws Exception {
        System.out.println("===== testCrashWithThreeLogEntries =====");

        bytemanArguments = bytemanArgs.replace("@BMScript@", "CrashWithThreeLogEntries");
        Config config = new Config();
        config.add("javaVmArguments", javaVmArguments + bytemanArguments);

        controller.start(CONTAINER_NAME, config.map());
        deployer.deploy(DEPLOYMENT_NAME);
        deployer.deploy(REST_TX_DEPLOYMENT_NAME);

        TxSupport txn = new TxSupport(TXN_MGR_URL);

        // Start transaction
        txn.startTx();

        // Enlist dummy participant
        String pUrls = TxSupport.getParticipantUrls(DUMMY_URL, DUMMY_URL);
        new TxSupport().httpRequest(new int[] { HttpURLConnection.HTTP_CREATED }, txn.txUrl(), "POST",
                TxSupport.POST_MEDIA_TYPE, pUrls, null);

        // Call to the transactional resource
        ClientResponse<String> clientResponse = new ClientRequest(BASE_URL).addLink(
                new Link("durableparticipant", "durableparticipant", txn.txUrl(), null, null)).post(String.class);
        assertEquals(200, clientResponse.getStatus());

        try {
            txn.commitTx();
        } catch (HttpResponseException e) {
            // After crash participant won't return any response and exception will be thrown.
        }

        controller.kill(CONTAINER_NAME);

        config.add("javaVmArguments", javaVmArguments);
        controller.start(CONTAINER_NAME, config.map());
        deployer.deploy(DEPLOYMENT_NAME);
        deployer.deploy(REST_TX_DEPLOYMENT_NAME);

        // Triggers registration of REST-AT JAX-RS resources
        txn.getTransactions();

        // Wait for the recovery
        for (int i = 0; i < 60 * 10; i++) {
            Thread.sleep(1000);
            System.out.print(i + " ");
        }

        deployer.undeploy(REST_TX_DEPLOYMENT_NAME);
        deployer.undeploy(DEPLOYMENT_NAME);
        controller.stop(CONTAINER_NAME);
        controller.kill(CONTAINER_NAME);
    }
    
    @Test
    public void testCrashWithTwoLogEntries() throws Exception {
        System.out.println("===== testCrashWithTwoLogEntries =====");

        bytemanArguments = bytemanArgs.replace("@BMScript@", "CrashWithTwoLogEntries");
        Config config = new Config();
        config.add("javaVmArguments", javaVmArguments + bytemanArguments);

        controller.start(CONTAINER_NAME, config.map());
        deployer.deploy(DEPLOYMENT_NAME);
        deployer.deploy(REST_TX_DEPLOYMENT_NAME);

        TxSupport txn = new TxSupport(TXN_MGR_URL);

        // Start transaction
        txn.startTx();

        // Enlist dummy participant
        String pUrls = TxSupport.getParticipantUrls(DUMMY_URL, DUMMY_URL);
        new TxSupport().httpRequest(new int[] { HttpURLConnection.HTTP_CREATED }, txn.txUrl(), "POST",
                TxSupport.POST_MEDIA_TYPE, pUrls, null);

        // Call to the transactional resource
        ClientResponse<String> clientResponse = new ClientRequest(BASE_URL).addLink(
                new Link("durableparticipant", "durableparticipant", txn.txUrl(), null, null)).post(String.class);
        assertEquals(200, clientResponse.getStatus());

        try {
            txn.commitTx();
        } catch (HttpResponseException e) {
            // After crash participant won't return any response and exception will be thrown.
        }

        controller.kill(CONTAINER_NAME);

        config.add("javaVmArguments", javaVmArguments);
        controller.start(CONTAINER_NAME, config.map());
        deployer.deploy(DEPLOYMENT_NAME);
        deployer.deploy(REST_TX_DEPLOYMENT_NAME);

        // Triggers registration of REST-AT JAX-RS resources
        txn.getTransactions();
        
        // Triggers bridge recovery module registration
        new ClientRequest(BASE_URL).post(String.class);
        
        // Wait for the recovery
        for (int i = 0; i < 60 * 10; i++) {
            Thread.sleep(1000);
            System.out.print(i + " ");
        }

        deployer.undeploy(REST_TX_DEPLOYMENT_NAME);
        deployer.undeploy(DEPLOYMENT_NAME);
        controller.stop(CONTAINER_NAME);
        controller.kill(CONTAINER_NAME);
    }

    private boolean deleteDirectory(File path) {
        if (path.exists()) {
            File[] files = path.listFiles();
            for (int i = 0; i < files.length; i++) {
                if (files[i].isDirectory()) {
                    deleteDirectory(files[i]);
                } else {
                    files[i].delete();
                }
            }
        }
        return (path.delete());
    }

}
