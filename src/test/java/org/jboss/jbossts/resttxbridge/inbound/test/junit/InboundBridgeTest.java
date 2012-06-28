package org.jboss.jbossts.resttxbridge.inbound.test.junit;

import static org.junit.Assert.*;

import java.io.File;
import java.net.HttpURLConnection;

import org.hornetq.utils.json.JSONArray;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.jbossts.resttxbridge.inbound.test.common.DummyParticipant;
import org.jboss.jbossts.star.util.TxSupport;
import org.jboss.resteasy.client.ClientRequest;
import org.jboss.resteasy.client.ClientResponse;
import org.jboss.resteasy.spi.Link;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * 
 * @author Gytis Trikleris
 *
 */
@RunWith(Arquillian.class)
public class InboundBridgeTest {
    
    public static final String DEPLOYMENT_NAME = "rest-tx-bridge-test";

    public static final String CONTAINER_NAME = "jbossas-manual";

    private static final String ManifestMF = "Manifest-Version: 1.0\n"
            + "Dependencies: org.jboss.jts, org.hornetq, org.jboss.logging\n";
    
    private static final String TXN_MGR_URL = "http://localhost:8080/rest-tx/tx/transaction-manager";

    private static final String BASE_URL = "http://localhost:8080/rest-tx-bridge-test";
    
    private static final String DUMMY_URL = BASE_URL + "/" + DummyParticipant.PARTICIPANT_SEGMENT;

    @ArquillianResource
    private ContainerController controller;

    @ArquillianResource
    private Deployer deployer;
    
    @Deployment(name = DEPLOYMENT_NAME, testable = false, managed = false)
    @TargetsContainer(CONTAINER_NAME)
    public static WebArchive createDeployment() {
        WebArchive archive = ShrinkWrap.create(WebArchive.class, DEPLOYMENT_NAME + ".war")
                .addAsWebInfResource(new File("src/test/webapp", "WEB-INF/web-base.xml"), "web.xml")
                .addPackages(false, "org.jboss.jbossts.resttxbridge.annotation")
                .addPackages(false, "org.jboss.jbossts.resttxbridge.inbound")
                .addPackages(false, "org.jboss.jbossts.resttxbridge.inbound.provider")
                .addPackages(false, "org.jboss.jbossts.resttxbridge.inbound.test.base")
                .addPackages(false, "org.jboss.jbossts.resttxbridge.inbound.test.common")
                .addPackages(true, "org.jboss.jbossts.star")
                .setManifest(new StringAsset(ManifestMF));

        return archive;
    }
    
    @Before
    public void setUp() {
        controller.start(CONTAINER_NAME);
        deployer.deploy(DEPLOYMENT_NAME);
    }
    
    @After
    public void tearDown() {
        deployer.undeploy(DEPLOYMENT_NAME);
        controller.stop(CONTAINER_NAME);
        controller.kill(CONTAINER_NAME);
    }

    @Test
    public void testCommit() throws Exception {
        System.out.println("===== testCommit =====");

        TxSupport txn = new TxSupport(TXN_MGR_URL);

        // Reset invocations
        ClientResponse<String> clientResponse = new ClientRequest(BASE_URL).put(String.class);

        // Start transaction
        txn.startTx();

        // Enlist XAResource
        clientResponse = new ClientRequest(BASE_URL).addLink(
                new Link("durableparticipant", "durableparticipant", txn.txUrl(), null, null)).post(String.class);

        assertEquals(200, clientResponse.getStatus());

        // Commit transaction
        txn.commitTx();

        // Allow some time for two-phase commit to complete
        Thread.sleep(5000);

        // Get invocations
        clientResponse = new ClientRequest(BASE_URL).get(String.class);
        JSONArray jsonArray = new JSONArray(clientResponse.getEntity());

        assertEquals(4, jsonArray.length());
        assertEquals("LoggingXAResource.start", jsonArray.get(0));
        assertEquals("LoggingXAResource.end", jsonArray.get(1));
        assertEquals("LoggingXAResource.prepare", jsonArray.get(2));
        assertEquals("LoggingXAResource.commit", jsonArray.get(3));
    }

    @Test
    public void testRollback() throws Exception {
        System.out.println("===== testRollback =====");

        TxSupport txn = new TxSupport(TXN_MGR_URL);

        // Reset invocations
        ClientResponse<String> clientResponse = new ClientRequest(BASE_URL).put(String.class);

        // Start transaction
        txn.startTx();

        // Enlist XAResource
        clientResponse = new ClientRequest(BASE_URL).addLink(new Link("coordinator", "coordinator", txn.txUrl(), null, null))
                .post(String.class);

        assertEquals(200, clientResponse.getStatus());

        // Rollback transaction
        txn.rollbackTx();

        // Allow some time for two-phase commit to complete
        Thread.sleep(5000);

        // Get invocations
        clientResponse = new ClientRequest(BASE_URL).get(String.class);
        JSONArray jsonArray = new JSONArray(clientResponse.getEntity());

        assertEquals(3, jsonArray.length());
        assertEquals("LoggingXAResource.start", jsonArray.get(0));
        assertEquals("LoggingXAResource.end", jsonArray.get(1));
        assertEquals("LoggingXAResource.rollback", jsonArray.get(2));
    }

    @Test
    public void testCommitWithTwoParticipants() throws Exception {
        System.out.println("===== testCommitWithTwoParticipants =====");

        TxSupport txn = new TxSupport(TXN_MGR_URL);

        // Reset XAResource invocations
        ClientResponse<String> clientResponse = new ClientRequest(BASE_URL).put(String.class);
        assertEquals(200, clientResponse.getStatus());
        
        // Reset dummy participant invocations
        clientResponse = new ClientRequest(DUMMY_URL + "/invocations").put(String.class);
        assertEquals(200, clientResponse.getStatus());

        // Start transaction
        txn.startTx();

        // Enlist dummy participant
        String pUrls = TxSupport.getParticipantUrls(DUMMY_URL, DUMMY_URL);
        new TxSupport().httpRequest(new int[] {HttpURLConnection.HTTP_CREATED},
                txn.txUrl(), "POST", TxSupport.POST_MEDIA_TYPE, pUrls, null);
        
        
        // Enlist XAResource
        clientResponse = new ClientRequest(BASE_URL).addLink(
                new Link("durableparticipant", "durableparticipant", txn.txUrl(), null, null)).post(String.class);

        assertEquals(200, clientResponse.getStatus());

        // Commit transaction
        txn.commitTx();

        // Allow some time for two-phase commit to complete
        Thread.sleep(5000);

        // Get XAResource invocations
        clientResponse = new ClientRequest(BASE_URL).get(String.class);
        JSONArray jsonArray = new JSONArray(clientResponse.getEntity());

        assertEquals(4, jsonArray.length());
        assertEquals("LoggingXAResource.start", jsonArray.get(0));
        assertEquals("LoggingXAResource.end", jsonArray.get(1));
        assertEquals("LoggingXAResource.prepare", jsonArray.get(2));
        assertEquals("LoggingXAResource.commit", jsonArray.get(3));
        
        // Get dummy participant invocations
        clientResponse = new ClientRequest(DUMMY_URL + "/invocations").get(String.class);
        jsonArray = new JSONArray(clientResponse.getEntity());
        
        assertEquals(2, jsonArray.length());
        assertEquals("DummyParticipant.terminateParticipant(txStatus=TransactionPrepared)", jsonArray.get(0));
        assertEquals("DummyParticipant.terminateParticipant(txStatus=TransactionCommitted)", jsonArray.get(1));
    }
    
    
    @Test
    public void testRollbackWithTwoParticipants() throws Exception {
        System.out.println("===== testRollbackWithTwoParticipants =====");

        TxSupport txn = new TxSupport(TXN_MGR_URL);

        // Reset XAResource invocations
        ClientResponse<String> clientResponse = new ClientRequest(BASE_URL).put(String.class);
        assertEquals(200, clientResponse.getStatus());
        
        // Reset dummy participant invocations
        clientResponse = new ClientRequest(DUMMY_URL + "/invocations").put(String.class);
        assertEquals(200, clientResponse.getStatus());

        // Start transaction
        txn.startTx();

        // Enlist dummy participant
        String pUrls = TxSupport.getParticipantUrls(DUMMY_URL, DUMMY_URL);
        new TxSupport().httpRequest(new int[] {HttpURLConnection.HTTP_CREATED},
                txn.txUrl(), "POST", TxSupport.POST_MEDIA_TYPE, pUrls, null);
        
        
        // Enlist XAResource
        clientResponse = new ClientRequest(BASE_URL).addLink(
                new Link("durableparticipant", "durableparticipant", txn.txUrl(), null, null)).post(String.class);

        assertEquals(200, clientResponse.getStatus());

        // Rollback transaction
        txn.rollbackTx();

        // Allow some time for two-phase commit to complete
        Thread.sleep(5000);

        // Get XAResource invocations
        clientResponse = new ClientRequest(BASE_URL).get(String.class);
        JSONArray jsonArray = new JSONArray(clientResponse.getEntity());

        assertEquals(3, jsonArray.length());
        assertEquals("LoggingXAResource.start", jsonArray.get(0));
        assertEquals("LoggingXAResource.end", jsonArray.get(1));
        assertEquals("LoggingXAResource.rollback", jsonArray.get(2));
        
        // Get dummy participant invocations
        clientResponse = new ClientRequest(DUMMY_URL + "/invocations").get(String.class);
        jsonArray = new JSONArray(clientResponse.getEntity());
        
        assertEquals(1, jsonArray.length());
        assertEquals("DummyParticipant.terminateParticipant(txStatus=TransactionRolledBack)", jsonArray.get(0));
    }

}
