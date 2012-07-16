package org.jboss.jbossts.resttxbridge.inbound;

import java.net.HttpURLConnection;
import java.util.HashSet;
import java.util.Set;

import javax.resource.spi.XATerminator;
import javax.transaction.xa.XAException;

import org.jboss.jbossts.star.provider.HttpResponseException;
import org.jboss.jbossts.star.util.TxSupport;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.objectstore.RecoveryStore;
import com.arjuna.ats.arjuna.objectstore.StoreManager;
import com.arjuna.ats.arjuna.recovery.RecoveryModule;
import com.arjuna.ats.arjuna.state.InputObjectState;
import com.arjuna.ats.internal.arjuna.common.UidHelper;
import com.arjuna.ats.internal.jta.transaction.arjunacore.jca.SubordinateTransaction;
import com.arjuna.ats.internal.jta.transaction.arjunacore.jca.SubordinationManager;
import com.arjuna.ats.internal.jta.transaction.arjunacore.subordinate.jca.SubordinateAtomicAction;

/**
 * 
 * @author Gytis Trikleris
 * 
 */
public class InboundBridgeRecoveryModule implements RecoveryModule {

    /**
     * Recovered instances of inbound bridge. After second pass every bridge with active REST-AT are passed to inbound bridge
     * manager.
     */
    private static Set<InboundBridge> recoveredBridges = new HashSet<InboundBridge>();

    /**
     * UIDs found in transaction log after first pass.
     */
    private Set<Uid> firstPassUids;

    /**
     * Adds recovered bridge to recovered bridges map. This method is called by InboundBridge.readObject method during recovery.
     * 
     * @param bridge
     */
    public static void addRecoveredBridge(InboundBridge bridge) {
        recoveredBridges.add(bridge);
    }

    /**
     * Called by the RecoveryManager at start up, and then PERIODIC_RECOVERY_PERIOD seconds after the completion, for all
     * RecoveryModules, of the second pass
     */
    @Override
    public void periodicWorkFirstPass() {
        System.out.println("InboundBridgeRecoveryModule.periodicWorkFirstPass");
        
        firstPassUids = getUidsToRecover();
    }

    /**
     * Called by the RecoveryManager RECOVERY_BACKOFF_PERIOD seconds after the completion of the first pass
     */
    @Override
    public void periodicWorkSecondPass() {
        System.out.println("InboundBridgeRecoveryModule.periodicWorkSecondPass");
        
        recoveredBridges.clear();
        Set<Uid> uids = getUidsToRecover();
        uids.retainAll(firstPassUids);

        for (Uid uid : uids) {
            try {
                SubordinateTransaction st = SubordinationManager.getTransactionImporter().recoverTransaction(uid);
                System.out.println(st);
            } catch (XAException e) {
                e.printStackTrace();
            }
        }

        addBridgesToMapping();
    }

    /**
     * Returns UIDs of JTA subordinate transactions with format id specified in inbound bridge class which were found in
     * transaction log.
     * 
     * @return Set<Uid>
     */
    private Set<Uid> getUidsToRecover() {
        System.out.println("InboundBridgeRecoveryModule.getUidsToRecover");
        
        Set<Uid> uids = new HashSet<Uid>();

        try {
            RecoveryStore recoveryStore = StoreManager.getRecoveryStore();
            InputObjectState states = new InputObjectState();

            // Only look in the JCA section of the object store
            if (recoveryStore.allObjUids(SubordinateAtomicAction.getType(), states) && states.notempty()) {
                boolean finished = false;

                do {
                    Uid uid = UidHelper.unpackFrom(states);

                    if (uid.notEquals(Uid.nullUid())) {
                        SubordinateAtomicAction saa = new SubordinateAtomicAction(uid, true);
                        if (saa.getXid().getFormatId() == InboundBridge.XARESOURCE_FORMAT_ID) {
                            uids.add(uid);
                        }

                    } else {
                        finished = true;
                    }

                } while (!finished);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return uids;
    }

    /**
     * Adds bridges with active REST-AT to inbound bridge manager's mapping.
     */
    private void addBridgesToMapping() {
        System.out.println("InboundBridgeRecoveryModule.addBridgesToMapping");
        
        for (InboundBridge bridge : recoveredBridges) {
            if (!isRestTransactionActive(bridge.getTxUrl(), bridge.getRecoveryUrl(), bridge.getParticipantTerminationUrl())
                    || !InboundBridgeManager.addInboundBridge(bridge)) {
                XATerminator xaTerminator = SubordinationManager.getXATerminator();
                try {
                    xaTerminator.rollback(bridge.getXid());
                } catch (XAException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Tell the REST-AT coordinator that a participant is ready for recovery
     */
    private boolean updateParticipantTerminationUrl(String terminatorUrl, String recoveryUrl) {
        System.out.println("InboundBridgeRecoveryModule.updateParticipantTerminationUrl");
        
        TxSupport txn = new TxSupport();
        int expectedResponses[] = new int[] { HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_NOT_FOUND };

        try {
            // you would use this request to obtain the REST-AT coordinators view of the participant
            // txn.httpRequest(expectedResponses, recoveryUrl, "GET", null, null, null);

            String terminationUrlContent = new StringBuilder().append(TxSupport.TERMINATOR_LINK).append('=')
                    .append(terminatorUrl).toString();
            // you would use the next request to tell the REST-AT coordinator which endpoint to terminate the
            // participant on
            txn.httpRequest(expectedResponses, recoveryUrl, "PUT", TxSupport.POST_MEDIA_TYPE, terminationUrlContent, null);
        } catch (HttpResponseException e) {
            return false;
        }

        return txn.getStatus() == HttpURLConnection.HTTP_OK;
    }

    /**
     * Tests if REST-AT is active or if the bridge is recovering.
     * 
     * @param txUrl
     * @return boolean
     */
    private boolean isRestTransactionActive(String txUrl, String recoveryUrl, String participantUrl) {
        System.out.println("InboundBridgeRecoveryModule.isRestTransactionActive");
        
        TxSupport txn = new TxSupport();
        int expectedResponses[] = new int[] { HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_NOT_FOUND };
        try {
            txn.httpRequest(expectedResponses, txUrl, "GET", null, null, null);
        } catch (HttpResponseException e) {
            e.printStackTrace();
        }

        if (txn.getStatus() == HttpURLConnection.HTTP_OK) {
            return TxSupport.isActive(TxSupport.getStatus(txn.getBody()));
        }
        
        return updateParticipantTerminationUrl(participantUrl, recoveryUrl);
    }
}
