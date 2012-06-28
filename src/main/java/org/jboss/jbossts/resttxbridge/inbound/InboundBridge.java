package org.jboss.jbossts.resttxbridge.inbound;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;

import javax.resource.spi.XATerminator;
import javax.transaction.InvalidTransactionException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.jboss.jbossts.star.util.TxSupport;
import org.jboss.logging.Logger;

import com.arjuna.ats.internal.jta.transaction.arjunacore.jca.SubordinationManager;
import com.arjuna.ats.jta.TransactionManager;

/**
 * Inbound bridge stores information about REST-AT and JTA transactions. On creation of the bridge new subordinate JTA
 * transaction is created and bridge enlists it self as a participant to participate in recovery.
 * 
 * @author Gytis Trikleris
 * 
 */
@SuppressWarnings("serial")
public final class InboundBridge implements XAResource, Serializable {

    private static Logger LOG = Logger.getLogger(InboundBridge.class);

    /**
     * Identifier for the subordinate transaction.
     */
    private Xid xid;

    /**
     * URL of the REST transaction.
     */
    private String txUrl;

    /**
     * Id of the REST-AT participant.
     */
    private String participantId;
    
    
    public InboundBridge() {
        System.out.println("InboundBridge.InboundBridge()");
    }

    public InboundBridge(Xid xid, String txUrl, String participantId) throws XAException, SystemException,
            IllegalStateException, RollbackException {
        System.out.println("InboundBridge(xid=" + xid + ",txUrl=" + txUrl + ",participantId=" + participantId + ")");

        this.xid = xid;
        this.txUrl = txUrl;
        this.participantId = participantId;

        Transaction tx = getTransaction();
        tx.enlistResource(this);
    }

    /**
     * Associate the JTA transaction to the current Thread. Used by inbound bridge preprocess interceptor.
     * 
     * @throws SystemException
     * @throws XAException
     * @throws IllegalStateException
     * @throws InvalidTransactionException
     */
    public void start() throws XAException, SystemException, InvalidTransactionException, IllegalStateException {
        System.out.println("InboundBridge.start()");

        Transaction tx = getTransaction();
        TransactionManager.transactionManager().resume(tx);
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
     * @return xid of the JTA transaction.
     */
    public Xid getXid() {
        System.out.println("InboundBridge.getXid(). Returns xid=" + xid);
        return xid;
    }
    
    public void setXid(Xid xid) {
        this.xid = xid;
    }

    /**
     * 
     * @return URL of REST-AT transaction.
     */
    public String getTxUrl() {
        System.out.println("InboundBridge.getTxUrl(). Returns txUrl=" + txUrl);
        return txUrl;
    }
    
    public void setTxUrl(String txUrl) {
        this.txUrl = txUrl;
    }

    public String getParticipantId() {
        System.out.println("InboundBridge.getParticipantId(). Returns particpantId=" + participantId);
        return participantId;
    }
    
    public void setParticipantId(String participantId) {
        this.participantId = participantId;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof InboundBridge)) {
            return false;
        }

        InboundBridge inboundBridge = (InboundBridge) o;

        return this.xid.equals(inboundBridge.xid) && txUrl.equals(inboundBridge.txUrl)
                && participantId.equals(inboundBridge.participantId);
    }

    /**
     * Get the JTA Transaction which corresponds to the xid of the instance.
     * 
     * @return
     * @throws XAException
     * @throws SystemException
     */
    private Transaction getTransaction() throws XAException, SystemException {
        System.out.println("InboundBridge.getTransaction(). Returns transaction with xid=" + xid);

        Transaction tx = SubordinationManager.getTransactionImporter().importTransaction(xid);

        switch (tx.getStatus()) {
            case Status.STATUS_ACTIVE:
            case Status.STATUS_MARKED_ROLLBACK:
            case Status.STATUS_COMMITTING:
                break;
            default:
                throw new IllegalStateException("Transaction is not in state ACTIVE");
        }

        return tx;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        System.out.println("InboundBridge.writeObject(). Persists xid=" + xid + ",txUrl=" + txUrl + ",participantId="
                + participantId);

        out.writeObject(xid);
        out.writeObject(txUrl);
        out.writeObject(participantId);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException, XAException {
        System.out.println("InboundBridge.readObject()");

        xid = (Xid) in.readObject();
        txUrl = (String) in.readObject();
        participantId = (String) in.readObject();

        if (isRestTransactionActive()) {
            if (!InboundBridgeManager.addInboundBridge(this)) {
                // Bridge was not mapped - roll back JTA transaction
                XATerminator xaTerminator = SubordinationManager.getXATerminator();
                xaTerminator.rollback(xid);
            }
        } else {
            // REST transaction is not active - roll back JTA transaction
            XATerminator xaTerminator = SubordinationManager.getXATerminator();
            xaTerminator.rollback(xid);
        }
    }

    private boolean isRestTransactionActive() {
        String response = new TxSupport().httpRequest(new int[] { HttpURLConnection.HTTP_OK }, txUrl, "GET", null, null, null);
        if (response == null) {
            return false;
        }
        
        String[] parts = response.split("=");
        
        if (parts.length != 2) {
            return false;
        }
        
        return TxSupport.isActive(parts[1]);
    }

    // XAResource methods.

    @Override
    public void commit(Xid arg0, boolean arg1) throws XAException {
    }

    @Override
    public void end(Xid arg0, int arg1) throws XAException {
    }

    @Override
    public void forget(Xid arg0) throws XAException {
    }

    @Override
    public int getTransactionTimeout() throws XAException {
        return 0;
    }

    @Override
    public boolean isSameRM(XAResource arg0) throws XAException {
        return false;
    }

    @Override
    public int prepare(Xid arg0) throws XAException {
        return XAResource.XA_OK;
    }

    @Override
    public Xid[] recover(int arg0) throws XAException {
        return null;
    }

    @Override
    public void rollback(Xid arg0) throws XAException {
    }

    @Override
    public boolean setTransactionTimeout(int arg0) throws XAException {
        return false;
    }

    @Override
    public void start(Xid arg0, int arg1) throws XAException {
    }

}
