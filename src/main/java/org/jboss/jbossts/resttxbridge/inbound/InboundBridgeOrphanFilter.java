package org.jboss.jbossts.resttxbridge.inbound;

import java.io.IOException;

import javax.transaction.xa.Xid;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.exceptions.ObjectStoreException;
import com.arjuna.ats.arjuna.objectstore.RecoveryStore;
import com.arjuna.ats.arjuna.objectstore.StoreManager;
import com.arjuna.ats.arjuna.state.InputObjectState;
import com.arjuna.ats.internal.arjuna.common.UidHelper;
import com.arjuna.ats.internal.jta.transaction.arjunacore.subordinate.jca.SubordinateAtomicAction;
import com.arjuna.ats.jta.recovery.XAResourceOrphanFilter;

public class InboundBridgeOrphanFilter implements XAResourceOrphanFilter {

    /**
     * Called by the XARecoveryModule for each in-doubt Xid.
     * Implementations should return
     *   Vote.ROLLBACK if they recognize the xid and believe it should be aborted.
     *   Vote.LEAVE_ALONE if they recognize the xid and do not want the XARecovery module to roll it back.
     *   Vote.ABSTAIN if they do not recognize the xid.
     * Each registered XAResourceOrphanFilter will be consulted before any rollback on each recovery pass,
     * so they may change their mind over time e.g. if new information becomes available due to other recovery
     * activity.
     *
     * @param xid The in-doubt xid.
     * @return a Vote in accordance with the guidelines above.
     */
    @Override
    public Vote checkXid(Xid xid) {
        if (xid.getFormatId() != InboundBridge.XARESOURCE_FORMAT_ID) {
            System.out.println("InboundBridgeOrphanFilter.checkXid - ABSTAIN");
            return Vote.ABSTAIN;
        }
        
        if (isInStore(xid)) {
            System.out.println("InboundBridgeOrphanFilter.checkXid - LEAVE_ALONE");
            return Vote.LEAVE_ALONE;
        }

        System.out.println("InboundBridgeOrphanFilter.checkXid - ROLLBACK");
        return Vote.ROLLBACK;
    }
    
    private boolean isInStore(Xid xid) {
        RecoveryStore recoveryStore = StoreManager.getRecoveryStore();
        InputObjectState states = new InputObjectState();
        
        try {
            if (recoveryStore.allObjUids(SubordinateAtomicAction.getType(), states) && states.notempty()) {
                boolean finished = false;
                
                do {
                    Uid uid = UidHelper.unpackFrom(states);
                    
                    if (uid.notEquals(Uid.nullUid())) {
                        SubordinateAtomicAction saa = new SubordinateAtomicAction(uid, true);
                        if (saa.getXid().equals(xid)) {
                            return true;
                        }
                    } else {
                        finished = true;
                    }
                } while (!finished);
            }
        } catch (ObjectStoreException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return false;
    }

}
