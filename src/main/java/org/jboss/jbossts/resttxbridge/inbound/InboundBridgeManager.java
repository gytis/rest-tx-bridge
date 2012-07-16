package org.jboss.jbossts.resttxbridge.inbound;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.xa.XAException;
import javax.transaction.xa.Xid;

import org.jboss.jbossts.star.util.TxSupport;
//import org.jboss.logging.Logger;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.jta.xa.XATxConverter;

/**
 * Class to manage inbound bridge instances.
 * 
 * @author Gytis Trikleris
 */
public final class InboundBridgeManager {

    /**
     * TODO use logger instead of System.out
     */
    // private static final Logger LOG = Logger.getLogger(InboundBridgeManager.class);

    /**
     * Timer to schedule map cleaning task.
     */
    private static final Timer timer = new Timer();

    /**
     * Mappings of rest transactions and inbound bridges. Key - URL of REST-AT; value - Instance of InboundBridge
     */
    private static final ConcurrentMap<String, InboundBridge> inboundBridgeMappings = new ConcurrentHashMap<String, InboundBridge>();

    /**
     * Mappings of participants and transactions. Key - participant id; value - URL of REST-AT.
     * 
     * This mapping is required when transaction coordinator is communicating with participant. When request to the participant
     * is made only participant id is received, therefore we need to get transaction URL somehow.
     */
    private static final ConcurrentMap<String, String> participantMappings = new ConcurrentHashMap<String, String>();

    public static void startTimer() {
        timer.schedule(new ClearMapTask(), 0, 20 * 1000);
    }
    
    /**
     * TODO if rest transaction is associated with new bridge (new request within same transaction was made immediately after
     * recovery) - notify rest-at coordinator about new participant url
     * 
     * Adds inbound bridge to the map.
     * 
     * Method is used by inbound bridge recovery manager to enlist recovered bridges.
     * 
     * @param inboundBridge
     * @return boolean
     */
    public static boolean addInboundBridge(InboundBridge inboundBridge) {
        InboundBridge mappedInboundBridge = inboundBridgeMappings.get(inboundBridge.getTxUrl());

        if (inboundBridge.equals(mappedInboundBridge)) {
            // Bridge is already mapped
            return true;
        }

        if (mappedInboundBridge != null) {
            // REST-AT is already mapped with another bridge
            return false;
        }

        if (participantMappings.containsKey(inboundBridge.getParticipantId())) {
            // Participant is already mapped with another REST-AT
            return false;
        }

        inboundBridgeMappings.put(inboundBridge.getTxUrl(), inboundBridge);
        participantMappings.put(inboundBridge.getParticipantId(), inboundBridge.getTxUrl());

        return true;
    }

    /**
     * Returns inbound bridge for given REST-AT. If inbound bridge mapping does not exist, new bridge with subordinate
     * transaction and REST-AT participant is created.
     * 
     * Base URL is needed when registering bridge's REST-AT participant.
     * 
     * @param txUrl
     * @param baseUrl
     * @return InboundBridge
     * @throws RollbackException
     * @throws SystemException
     * @throws XAException
     * @throws IllegalStateException
     * @throws IllegalArgumentException
     */
    public static InboundBridge getInboundBridge(String txUrl, String baseUrl) throws IllegalStateException, XAException,
            SystemException, RollbackException {
        System.out.println("InboundBridgeManager.getInboundBridge(txUrl=" + txUrl + ")");
        if (txUrl == null) {
            throw new IllegalArgumentException("Transaction URL is required");
        }

        if (baseUrl == null) {
            throw new IllegalArgumentException("Base URL is required");
        }

        if (!inboundBridgeMappings.containsKey(txUrl)) {
            createInboundBridgeMapping(txUrl, baseUrl);
        }

        return inboundBridgeMappings.get(txUrl);
    }

    /**
     * Returns URL of REST-AT associated with given participant.
     * 
     * @param participantId
     * @return String
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
     * Removes mapping of given REST-AT and inbound bridge.
     * 
     * @param txUrl
     * @throws SystemException
     * @throws IllegalArgumentException
     */
    public static synchronized void removeInboundBridgeMapping(String txUrl) throws SystemException {
        System.out.println("InboundBridgeManager.removeInboundBridgeMapping(txUrl=" + txUrl + ")");

        if (txUrl == null) {
            throw new IllegalArgumentException("Transaction URL is required");
        }

        inboundBridgeMappings.get(txUrl).stop();
        inboundBridgeMappings.remove(txUrl);
    }

    /**
     * Removes mapping of given participant and REST-AT.
     * 
     * Bridge mapping is also removed.
     * 
     * @param participantId
     * @throws SystemException
     * @throws IllegalArgumentException
     */
    public static synchronized void removeParticipantMapping(String participantId) throws SystemException {
        System.out.println("InboundBridgeManager.removeParticipantMapping(participantId=" + participantId + ")");

        if (participantId == null) {
            throw new IllegalArgumentException("Participant ID is required");
        }

        String txUrl = participantMappings.get(participantId);
        removeInboundBridgeMapping(txUrl);
        participantMappings.remove(participantId);
    }

    /**
     * Creates new inbound bridge and maps it with given REST-AT.
     * 
     * As a result bridge's participant is enlisted with REST-AT.
     * 
     * @param txUrl
     * @param baseUrl
     * @throws XAException
     * @throws SystemException
     * @throws RollbackException
     * @throws IllegalStateException
     */
    private static synchronized void createInboundBridgeMapping(String txUrl, String baseUrl) throws XAException,
            SystemException, IllegalStateException, RollbackException {

        System.out.println("InboundBridgeManager.createMapping(txUrl=" + txUrl + ")");

        if (inboundBridgeMappings.containsKey(txUrl)) {
            return;
        }

        // Xid for driving the subordinate transaction
        Xid xid = XATxConverter.getXid(new Uid(), false, InboundBridge.XARESOURCE_FORMAT_ID);

        // construct the participantId in such as way as we can recognize it at recovery time
        String participantId = BridgeDurableParticipant.TYPE_IDENTIFIER + new Uid().fileStringForm();

        String recoveryUrl = enlistParticipant(txUrl, participantId, baseUrl);
        String participantTerminationUrl = baseUrl + BridgeDurableParticipant.PARTICIPANT_SEGMENT + "/" + participantId;
        InboundBridge bridge = new InboundBridge(xid, txUrl, recoveryUrl, participantId, participantTerminationUrl);
        inboundBridgeMappings.put(txUrl, bridge);
    }
    
    /**
     * Enlists participant with REST-AT.
     * 
     * @param txUrl
     * @param participantId
     * @param baseUrl
     * @return URL to recover participant
     * @throws UnsupportedEncodingException
     */
    private static synchronized String enlistParticipant(String txUrl, String participantId, String baseUrl) {
        System.out.println("InboundBridgeManager.enlistParticipant()");

        if (!baseUrl.substring(baseUrl.length() - 1).equals("/")) {
            baseUrl = baseUrl + "/";
        }

        String participantUrl = baseUrl + BridgeDurableParticipant.PARTICIPANT_SEGMENT + "/" + participantId;
        String pUrls = TxSupport.getParticipantUrls(participantUrl, participantUrl);

        Map<String, String> links = new HashMap<String, String>();

        new TxSupport().httpRequest(new int[] { HttpURLConnection.HTTP_CREATED }, txUrl, "POST", TxSupport.POST_MEDIA_TYPE,
                pUrls, links);

        System.out.println("Enlisted bridge, recovery coordinator url is " + links.get(TxSupport.LOCATION_LINK));
        
        participantMappings.put(participantId, txUrl);
        
        return links.get(TxSupport.LOCATION_LINK);
    }

    /**
     * TODO implement
     * 
     * Nested class to execute cleaning of inbound bridges mappings.
     * 
     * @author Gytis Trikleris
     */
    private static class ClearMapTask extends TimerTask {

        @Override
        public void run() {
            System.out.println("InboundBridgeManager.ClearMapTask.run");

            int[] expectedStatusCodes = new int[] { HttpURLConnection.HTTP_NOT_FOUND, HttpURLConnection.HTTP_OK };
            System.out.println(inboundBridgeMappings);
            for (String txUrl : inboundBridgeMappings.keySet()) {
                String response = "";

                try {
                    response = new TxSupport().httpRequest(expectedStatusCodes, txUrl, "GET", null, null, null);
                } catch (Exception e) {
                    continue;
                }

                String status = TxSupport.getStatus(response);
                System.out.println(status);
            }
        }
    }

}
