package uk.ac.ncl.gt.resttxbridge.inbound;

import java.io.File;

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

import uk.ac.ncl.gt.resttxbridge.annotation.Transactional;
import uk.ac.ncl.gt.resttxbridge.inbound.demo.DemoResource;
import uk.ac.ncl.gt.resttxbridge.inbound.provider.InboundBridgePreProcessInterceptor;
import uk.ac.ncl.gt.resttxbridge.inbound.service.InboundBridgeService;


@RunWith(Arquillian.class)
public class InboundBridgeTest {

    private static final String TXN_MGR_URL = "http://localhost:8080/rest-tx/tx/transaction-manager";
    
    private static final String BASE_URL = "http://localhost:8080/rest-tx-bridge-test";

    private static final String TRANSACTIONAL_DEMO_PATH = BASE_URL + "/demo";

//    private static final String SIMPLE_DEMO_PATH = BASE_URL + "/demo/simple";


    @Deployment
    public static WebArchive createDeployment() {
        WebArchive archive = ShrinkWrap
                .create(WebArchive.class, "rest-tx-bridge-test.war")
                .addPackage(Transactional.class.getPackage())
                .addPackage(InboundBridgeManager.class.getPackage())
                .addPackage(DemoResource.class.getPackage())
                .addPackage(InboundBridgePreProcessInterceptor.class.getPackage())
                .addPackage(InboundBridgeService.class.getPackage())
                .addAsLibraries(DependencyResolvers
                        .use(MavenDependencyResolver.class)
                        .loadMetadataFromPom("pom.xml")
                        .artifacts(
                                "org.jboss.narayana.rts:restat-util:5.0.0.M2-SNAPSHOT",
                                "org.jboss.narayana.jta:narayana-jta:5.0.0.M1")
//                        .includeDependenciesFromPom("pom.xml")
                        .resolveAsFiles())
                .setWebXML(new File("src/main/webapp/WEB-INF/web.xml"));
        
        System.out.println(archive.toString(true));
        
        return archive;
    }


    @Test
    public void test() throws Exception {
        TxSupport txn = new TxSupport(TXN_MGR_URL);
        
        txn.startTx();
        
        ClientRequest clientRequest = new ClientRequest(TRANSACTIONAL_DEMO_PATH);
        clientRequest.addLink(new Link("coordinator",
                "coordinator", txn.txUrl(), null, null));
//        clientRequest.addLink(new Link(txn.TERMINATOR_LINK,
//                txn.TERMINATOR_LINK, txn.txTerminatorUrl(), null, null));
//        clientRequest.addLink(new Link(txn.PARTICIPANT_LINK,
//                txn.PARTICIPANT_LINK, txn.enlistUrl(), null, null));
        
        ClientResponse<String> clientResponse = clientRequest.get(String.class);
        
        System.out.println(clientResponse.getEntity());
        
        txn.commitTx();
        
        Thread.sleep(5000);
    }

}
