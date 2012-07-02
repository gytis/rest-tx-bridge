package org.jboss.jbossts.resttxbridge.inbound.test.junit;

import static org.junit.Assert.assertEquals;

import java.net.HttpURLConnection;
import java.util.HashMap;

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
        config.add("javaVmArguments", javaVmArguments + bytemanArguments);

        System.out.println(controller);
        
        controller.start(CONTAINER_NAME, config.map());
        deployer.deploy(DEPLOYMENT_NAME);
        deployer.deploy(REST_AT_DEPLOYMENT_NAME);
        
        TxSupport txn = new TxSupport(TXN_MGR_URL);

        // Start transaction
        txn.startTx();

        // Enlist dummy participant
        String pUrls = TxSupport.getParticipantUrls(DUMMY_URL, DUMMY_URL);
        new TxSupport().httpRequest(new int[] {HttpURLConnection.HTTP_CREATED},
                txn.txUrl(), "POST", TxSupport.POST_MEDIA_TYPE, pUrls, null);
        
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
        deployer.deploy(REST_AT_DEPLOYMENT_NAME);
        
        // Trigger registration of REST-AT JAX-RS resources
        System.out.println(txn.getTransactions());
        
        // Wait for the recovery
        for (int i = 0; i < 60*10; i++) {
            Thread.sleep(1000);
            System.out.print(i + " ");
        }
        
        deployer.undeploy(REST_AT_DEPLOYMENT_NAME);
        deployer.undeploy(DEPLOYMENT_NAME);
        controller.stop(CONTAINER_NAME);
        controller.kill(CONTAINER_NAME);
    }
    
}
