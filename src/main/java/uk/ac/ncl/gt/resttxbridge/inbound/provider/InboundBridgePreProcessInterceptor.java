package uk.ac.ncl.gt.resttxbridge.inbound.provider;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.ext.Provider;

import org.jboss.resteasy.annotations.interception.ServerInterceptor;
import org.jboss.resteasy.core.ResourceMethod;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.spi.Failure;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.interception.PreProcessInterceptor;

import uk.ac.ncl.gt.resttxbridge.annotation.Participant;
import uk.ac.ncl.gt.resttxbridge.annotation.Transactional;
import uk.ac.ncl.gt.resttxbridge.inbound.Utils;
import uk.ac.ncl.gt.resttxbridge.inbound.InboundBridge;
import uk.ac.ncl.gt.resttxbridge.inbound.InboundBridgeManager;


/**
 * 
 * @author gytis
 *
 * Provider class intercepts every request before method is executed.
 * If method is annotated with <code>Transactional</code> annotation,
 * transaction bridge is initiated.
 */
@Provider
@ServerInterceptor
public final class InboundBridgePreProcessInterceptor implements
        PreProcessInterceptor {

    /**
     * Main interceptor's method.
     */
    @Override
    public ServerResponse preProcess(HttpRequest request,
            ResourceMethod resourceMethod) throws Failure, WebApplicationException {

        System.out.println("InboundBridgePreProcessInterceptor.preProcess()");
        
        // TODO participant class should be annotated instead of methods
        if (resourceMethod.getMethod().isAnnotationPresent(Participant.class)) {
            String participantId = Utils.getParticipantId(request);
            String txUrl = Utils.getTransactionUrl(participantId);
            
            if (txUrl != null) {
                startBridge(txUrl);
            }
            
        } else if (resourceMethod.getMethod().isAnnotationPresent(Transactional.class)) {
            String txUrl = Utils.getTransactionUrl(request);
            
            // If transaction url is not provided - there is no transaction to map.
            if (txUrl != null) {
                startBridge(txUrl);
            }
        }

        // Null allows intercepted method to be executed
        return null;
    }


    /**
     * Starts inbound rest-tx to jta bridge.
     * @param txUrl
     */
    private void startBridge(String txUrl) {
        System.out.println("InboundBridgePreProcessInterceptor.startBridge()");
        
        try {
            InboundBridge bridge = InboundBridgeManager.getInboundBridge(txUrl);
            bridge.start();
        } catch (Exception e) {
            // TODO log exception
            e.printStackTrace();
        }
    }
    
}
