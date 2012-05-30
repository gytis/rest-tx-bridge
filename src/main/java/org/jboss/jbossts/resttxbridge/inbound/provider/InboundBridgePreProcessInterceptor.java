package org.jboss.jbossts.resttxbridge.inbound.provider;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.Provider;

import org.jboss.jbossts.resttxbridge.annotation.Participant;
import org.jboss.jbossts.resttxbridge.annotation.Transactional;
import org.jboss.jbossts.resttxbridge.inbound.InboundBridge;
import org.jboss.jbossts.resttxbridge.inbound.InboundBridgeManager;
import org.jboss.jbossts.resttxbridge.inbound.Utils;
import org.jboss.resteasy.annotations.interception.ServerInterceptor;
import org.jboss.resteasy.core.ResourceMethod;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.spi.Failure;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.interception.PreProcessInterceptor;


/**
 * 
 * @author Gytis Trikleris
 * 
 *         Provider class intercepts every request before method is executed. If method is annotated with
 *         <code>Transactional</code> annotation, transaction bridge is initiated.
 */
@Provider
@ServerInterceptor
public final class InboundBridgePreProcessInterceptor implements PreProcessInterceptor {

    /**
     * Main interceptor's method.
     */
    @Override
    public ServerResponse preProcess(HttpRequest request, ResourceMethod resourceMethod) throws Failure,
            WebApplicationException {

        System.out.println("InboundBridgePreProcessInterceptor.preProcess()");

        String txUrl = null;

        if (resourceMethod.getMethod().getDeclaringClass().isAnnotationPresent(Participant.class)) {
            String participantId = Utils.getParticipantId(request);
            txUrl = Utils.getTransactionUrl(participantId);

        } else if (resourceMethod.getMethod().isAnnotationPresent(Transactional.class)) {
            txUrl = Utils.getTransactionUrl(request);
        }

        // If transaction URL is not provided - there is no transaction to map.
        if (txUrl != null) {
            startBridge(txUrl, request.getUri());
        }

        // Null allows intercepted method to be executed
        return null;
    }

    /**
     * Starts REST-TX to JTA bridge.
     * 
     * @param txUrl
     */
    private void startBridge(String txUrl, UriInfo uriInfo) {
        System.out.println("InboundBridgePreProcessInterceptor.startBridge()");

        try {
            InboundBridge bridge = InboundBridgeManager.getInboundBridge(txUrl, uriInfo.getBaseUri().toString());
            bridge.start();
        } catch (Exception e) {
            // TODO log exception
            e.printStackTrace();
        }
    }

}
