package uk.ac.ncl.gt.resttxbridge.inbound;

import static org.junit.Assert.*;

import java.io.File;

import org.hornetq.utils.json.JSONArray;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.jbossts.star.util.TxSupport;
import org.jboss.resteasy.client.ClientRequest;
import org.jboss.resteasy.client.ClientResponse;
import org.jboss.resteasy.spi.Link;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.DependencyResolvers;
import org.jboss.shrinkwrap.resolver.api.maven.MavenDependencyResolver;
import org.junit.Test;
import org.junit.runner.RunWith;


@RunWith(Arquillian.class)
public class InboundBridgeTest {

    private static final String TXN_MGR_URL = "http://localhost:8080/rest-tx/tx/transaction-manager";
    
    private static final String BASE_URL = "http://localhost:8080/rest-tx-bridge-test";


    @Deployment
    public static WebArchive createDeployment() {
        WebArchive archive = ShrinkWrap
                .create(WebArchive.class, "rest-tx-bridge-test.war")
                .addPackage("uk.ac.ncl.gt.resttxbridge.annotation")
                .addPackage("uk.ac.ncl.gt.resttxbridge.inbound")
                .addPackage("uk.ac.ncl.gt.resttxbridge.inbound.provider")
                .addPackage("uk.ac.ncl.gt.resttxbridge.inbound.service")
                .addPackage("uk.ac.ncl.gt.resttxbridge.inbound.xa")
                .addAsLibraries(DependencyResolvers
                        .use(MavenDependencyResolver.class)
                        .loadMetadataFromPom("pom.xml")
                        .artifacts(
                                "org.jboss.narayana.rts:restat-util:5.0.0.M2-SNAPSHOT",
                                "org.jboss.narayana.jta:narayana-jta:5.0.0.M1")
                        .resolveAsFiles())
                .setWebXML(new File("src/test/webapp/WEB-INF/web.xml"));
        
        return archive;
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
        clientResponse = new ClientRequest(BASE_URL)
                .addLink(new Link("durableparticipant", "durableparticipant", txn.txUrl(), null, null))
                .post(String.class);
        
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
        clientResponse = new ClientRequest(BASE_URL)
                .addLink(new Link("coordinator", "coordinator", txn.txUrl(), null, null))
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

}