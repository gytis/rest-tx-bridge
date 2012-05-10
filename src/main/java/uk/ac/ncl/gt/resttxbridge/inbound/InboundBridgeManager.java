package uk.ac.ncl.gt.resttxbridge.inbound;

import java.net.HttpURLConnection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.transaction.SystemException;
import javax.transaction.xa.XAException;
import javax.transaction.xa.Xid;

import org.jboss.jbossts.star.util.TxSupport;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.jta.xa.XATxConverter;


public final class InboundBridgeManager {
    
    /**
     * Base url of transaction bridge.
     */
    public static final String BASE_URL = "http://localhost:8080/rest-tx-bridge-test";
    
    /**
     * Mappings of rest transactions and inbound bridges.
     * Map key is url of transaction.
     */
    private static final ConcurrentMap<String, InboundBridge> inboundBridgeMappings = new ConcurrentHashMap<String, InboundBridge>();
    
    /**
     * Mappings of participants and transactions.
     * Map key is participant id and value is transaction url.
     * 
     * This mapping is required when transaction coordinator is communicating with participant.
     * When request to the participant is made only participant id is received,
     * therefore we need to get transaction url.
     */
    private static final ConcurrentMap<String, String> participantMappings = new ConcurrentHashMap<String, String>();


    /**
     * 
     * @param txUrl
     * @return
     * @throws XAException
     * @throws SystemException
     * @throws IllegalArgumentException
     */
    public static InboundBridge getInboundBridge(String txUrl) throws XAException, SystemException {
        System.out.println("InboundBridgeManager.getInboundBridge(txUrl=" + txUrl + ")");
        
        if (txUrl == null) {
            throw new IllegalArgumentException("Transaction URL is required");
        }
        
        if(!inboundBridgeMappings.containsKey(txUrl)) {
            createInboundBridgeMapping(txUrl);
        }

        return inboundBridgeMappings.get(txUrl);
    }
    
    
    /**
     * 
     * @param participantId
     * @return
     * @throws IllegalArgumentException
     */
    public static synchronized String getParticipantTransaction(String participantId) {
        System.out.println("InboundBridgeManager.getParticipantTransaction(participantId=" + participantId + ")");
        
        if (participantId == null) {
            throw new IllegalArgumentException("Participant ID is required");
        }
        
        return participantMappings.get(participantId);
    }
    
    
    /**
     * 
     * @param txUrl
     * @throws IllegalArgumentException
     */
    public static synchronized void removeInboundBridgeMapping(String txUrl) {
        System.out.println("InboundBridgeManager.removeInboundBridgeMapping(txUrl=" + txUrl + ")");
        
        if (txUrl == null) {
            throw new IllegalArgumentException("Transaction URL is required");
        }
        
        inboundBridgeMappings.remove(txUrl);
    }
    
    
    /**
     * 
     * @param participantId
     * @throws IllegalArgumentException
     */
    public static synchronized void removeParticipantMapping(String participantId) {
        System.out.println("InboundBridgeManager.removeParticipantMapping(participantId=" + participantId + ")");
        
        if (participantId == null) {
            throw new IllegalArgumentException("Participant ID is required");
        }
        
        String txUrl = participantMappings.get(participantId);
        removeInboundBridgeMapping(txUrl);
        participantMappings.remove(participantId);
    }


    /**
     * 
     * @param txUrl
     * @throws XAException
     * @throws SystemException
     */
    private static synchronized void createInboundBridgeMapping(String txUrl) throws XAException, SystemException {
        System.out.println("InboundBridgeManager.createMapping()");

        if (inboundBridgeMappings.containsKey(txUrl)) {
            return;
        }

        // Xid for driving the subordinate transaction
        Xid xid = XATxConverter.getXid(new Uid(), false,
                BridgeDurableParticipant.XARESOURCE_FORMAT_ID);

        // construct the participantId in such as way as we can recognize it at recovery time
        String participantId = BridgeDurableParticipant.TYPE_IDENTIFIER
                + new Uid().fileStringForm();
        
        enlistParticipant(txUrl, participantId);
        InboundBridge bridge = new InboundBridge(xid);
        inboundBridgeMappings.put(txUrl, bridge);
    }
    
    
    /**
     * 
     * @param txUrl
     * @param participantId
     */
    private static synchronized void enlistParticipant(String txUrl, String participantId) {
        System.out.println("InboundBridgeManager.enlistParticipant()");
        
        String participantUrl = BASE_URL
                + BridgeDurableParticipant.PARTICIPANT_SEGMENT
                + "/" + participantId;
        
        String pUrls = TxSupport.getParticipantUrls(participantUrl, participantUrl);
        new TxSupport().httpRequest(
                new int[] {HttpURLConnection.HTTP_CREATED},
                txUrl, "POST", TxSupport.POST_MEDIA_TYPE, pUrls, null);
        participantMappings.put(participantId, txUrl);
    }

}
