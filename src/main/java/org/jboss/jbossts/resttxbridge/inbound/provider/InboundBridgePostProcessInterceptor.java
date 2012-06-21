package org.jboss.jbossts.resttxbridge.inbound.provider;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.Provider;

import org.jboss.jbossts.resttxbridge.annotation.Participant;
import org.jboss.jbossts.resttxbridge.annotation.Transactional;
import org.jboss.jbossts.resttxbridge.inbound.InboundBridge;
import org.jboss.jbossts.resttxbridge.inbound.InboundBridgeManager;
import org.jboss.jbossts.resttxbridge.inbound.Utils;
import org.jboss.logging.Logger;
import org.jboss.resteasy.annotations.interception.ServerInterceptor;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.interception.PostProcessInterceptor;


/**
 * 
 * @author Gytis Trikleris
 * 
 */
@Provider
@ServerInterceptor
public final class InboundBridgePostProcessInterceptor implements PostProcessInterceptor {

    private static final Logger LOG = Logger.getLogger(InboundBridgePostProcessInterceptor.class);
    
    // TODO Is it OK to store it in this place?
    @Context
    private HttpRequest request;

    @Override
    public void postProcess(ServerResponse response) {
        System.out.println("InboundBridgePostProcessInterceptor.postProcess()");

        String txUrl = null;

        if (response.getResourceMethod().getDeclaringClass().isAnnotationPresent(Participant.class)) {
            String participantId = Utils.getParticipantId(request);
            txUrl = Utils.getTransactionUrl(participantId);

        } else if (response.getResourceMethod().isAnnotationPresent(Transactional.class)) {
            txUrl = Utils.getTransactionUrl(request);
        }

        // If transaction URL is not provided - there is no transaction to map.
        if (txUrl != null) {
            stopBridge(txUrl, request.getUri());
        }
    }

    private void stopBridge(String txUrl, UriInfo uriInfo) {
        System.out.println("InboundBridgePostProcessInterceptor.stopBridge(txUrl=" + txUrl + ")");

        try {
            InboundBridge bridge = InboundBridgeManager.getInboundBridge(txUrl, uriInfo.getBaseUri().toString());
            bridge.stop();
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

}
