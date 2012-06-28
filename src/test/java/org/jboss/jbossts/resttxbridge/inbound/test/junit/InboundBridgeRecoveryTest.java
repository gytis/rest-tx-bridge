package org.jboss.jbossts.resttxbridge.inbound.test.junit;

import static org.junit.Assert.assertEquals;

import java.net.HttpURLConnection;

import org.jboss.arquillian.container.test.api.Config;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.jbossts.star.provider.HttpResponseException;
import org.jboss.jbossts.star.util.TxSupport;
import org.jboss.resteasy.client.ClientRequest;
import org.jboss.resteasy.client.ClientResponse;
import org.jboss.resteasy.spi.Link;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * 
 * @author Gytis Trikleris
 * 
 */
@RunWith(Arquillian.class)
public class InboundBridgeRecoveryTest extends BaseCrashTest {
    
    public InboundBridgeRecoveryTest() {
        scriptName = "CrashBeforeCommit";
    }
    
    @Test
    public void InboundBridgeCrashAfterPrepareTest() throws Exception {
        System.out.println("===== testCommit =====");

        Config config = new Config();
        config.add("javaVmArguments", javaVmArguments);

        System.out.println(controller);
        
        controller.start(CONTAINER_NAME, config.map());
        deployer.deploy(DEPLOYMENT_NAME);
        
        TxSupport txn = new TxSupport(TXN_MGR_URL);

        // Start transaction
        txn.startTx();

        // Enlist dummy participant
        String pUrls = TxSupport.getParticipantUrls(DUMMY_URL + "/1", DUMMY_URL + "/1");
        new TxSupport().httpRequest(new int[] {HttpURLConnection.HTTP_CREATED},
                txn.txUrl(), "POST", TxSupport.POST_MEDIA_TYPE, pUrls, null);
        
        // Enlist second dummy participant
        pUrls = TxSupport.getParticipantUrls(DUMMY_URL + "/2", DUMMY_URL + "/2");
        new TxSupport().httpRequest(new int[] {HttpURLConnection.HTTP_CREATED},
                txn.txUrl(), "POST", TxSupport.POST_MEDIA_TYPE, pUrls, null);
        
        // Call to the transactional resource
//        ClientResponse<String> clientResponse = new ClientRequest(BASE_URL).addLink(
//                new Link("durableparticipant", "durableparticipant", txn.txUrl(), null, null)).post(String.class);
//        assertEquals(200, clientResponse.getStatus());
        
        System.out.println("Transactions of REST-AT coordinator: " + txn.getTransactions());
        
        try {
            // Commit transaction
            txn.commitTx();
        } catch (HttpResponseException e) {
            // TODO: handle exception
        }
        
        controller.kill(CONTAINER_NAME);
        controller.start(CONTAINER_NAME);
        deployer.deploy(DEPLOYMENT_NAME);
        
        for (int i = 0; i < 120; i++) {
            Thread.sleep(1000);
            System.out.print(i + " ");
        }
        
//        clientResponse = new ClientRequest(BASE_URL).addLink(
//                new Link("durableparticipant", "durableparticipant", txn.txUrl(), null, null)).post(String.class);
//        assertEquals(200, clientResponse.getStatus());
        
        
//        controller.kill(CONTAINER_NAME);
//        config.add("javaVmArguments", javaVmArguments);
//        controller.start(CONTAINER_NAME, config.map());
//        deployer.undeploy(DEPLOYMENT_NAME);
//        deployer.deploy(DEPLOYMENT_NAME);
        
        
        deployer.undeploy(DEPLOYMENT_NAME);
        controller.stop(CONTAINER_NAME);
        controller.kill(CONTAINER_NAME);
    }
    
}
