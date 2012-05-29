package uk.ac.ncl.gt.resttxbridge.inbound;

import javax.naming.InitialContext;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.xa.XAException;
import javax.transaction.xa.Xid;

import com.arjuna.ats.internal.jta.transaction.arjunacore.jca.SubordinationManager;
import com.arjuna.ats.jta.TransactionManager;

/**
 * 
 * @author Gytis Trikleris
 * 
 */
public final class InboundBridge {

    /**
     * Identifier for the subordinate transaction.
     */
    private final Xid xid;

    public InboundBridge(Xid xid) throws XAException, SystemException {
        System.out.println("InboundBridge(xid=" + xid + ")");

        this.xid = xid;
        getTransaction();
    }

    /**
     * Associate the JTA transaction to the current Thread. Used by inbound bridge preprocess interceptor.
     * 
     * @throws Exception
     */
    public void start() throws Exception {

        System.out.println("InboundBridge.start()");

        Transaction tx = getTransaction();
        TransactionManager.transactionManager().resume(tx);
        
        System.out.println(TransactionManager.transactionManager().getTransaction());
        
        javax.transaction.TransactionManager tm = (javax.transaction.TransactionManager) new InitialContext().lookup("java:jboss/TransactionManager");
        System.out.println(tm.getTransaction());
    }

    /**
     * Disassociate the JTA transaction from the current Thread. Used by inbound bridge postprocess interceptor.
     * 
     * @throws SystemException
     */
    public void stop() throws SystemException {
        System.out.println("InboundBridge.stop()");

        TransactionManager.transactionManager().suspend();
    }

    /**
     * 
     * @return
     */
    public Xid getXid() {
        return xid;
    }

    /**
     * Get the JTA Transaction which corresponds to the Xid of the instance.
     * 
     * @return
     * @throws XAException
     * @throws SystemException
     */
    private Transaction getTransaction() throws XAException, SystemException {
        System.out.println("InboundBridge.getTransaction()");
        System.out.println(Thread.currentThread().getId());
        
        Transaction tx = SubordinationManager.getTransactionImporter().importTransaction(xid);

        switch (tx.getStatus()) {
        // TODO: other cases?
            case Status.STATUS_ACTIVE:
            case Status.STATUS_MARKED_ROLLBACK:
            case Status.STATUS_COMMITTING:
                break;
            default:
                throw new IllegalStateException("Transaction not in state ACTIVE");
        }

        return tx;
    }

}
