package uk.ac.ncl.gt.resttxbridge.inbound.provider;

import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;

import org.jboss.resteasy.annotations.interception.ServerInterceptor;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.interception.PostProcessInterceptor;

import uk.ac.ncl.gt.resttxbridge.annotation.Participant;
import uk.ac.ncl.gt.resttxbridge.annotation.Transactional;
import uk.ac.ncl.gt.resttxbridge.inbound.InboundBridge;
import uk.ac.ncl.gt.resttxbridge.inbound.InboundBridgeManager;
import uk.ac.ncl.gt.resttxbridge.inbound.Utils;


@Provider
@ServerInterceptor
public final class InboundBridgePostProcessInterceptor implements PostProcessInterceptor {
    
    // TODO Is it ok to store it in this place?
    @Context HttpRequest request;
    
    
    @Override
    public void postProcess(ServerResponse response) {
        System.out.println("InboundBridgePostProcessInterceptor.postProcess()");
        
        if (response.getResourceMethod().isAnnotationPresent(Participant.class)) {
            String participantId = Utils.getParticipantId(request);
            String txUrl = Utils.getTransactionUrl(participantId);
            
            if (txUrl != null) {
                stopBridge(txUrl);
            }
            
        } else if (response.getResourceMethod().isAnnotationPresent(Transactional.class)) {
            String txUrl = Utils.getTransactionUrl(request);
            
            // If transaction url is not provided - there is no transaction to map.
            if (txUrl != null) {
                stopBridge(txUrl);
            }
        }
    }

    private void stopBridge(String txUrl) {
        System.out.println("InboundBridgePostProcessInterceptor.stopBridge()");
        
        try {
            InboundBridge bridge = InboundBridgeManager.getInboundBridge(txUrl);
            bridge.stop();
        } catch (Exception e) {
            // TODO log exception
            e.printStackTrace();
        }
    }
    
}
