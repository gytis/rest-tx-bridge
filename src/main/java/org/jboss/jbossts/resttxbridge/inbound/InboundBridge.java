package org.jboss.jbossts.resttxbridge.inbound;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import javax.transaction.InvalidTransactionException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

//import org.jboss.logging.Logger;

import com.arjuna.ats.internal.jta.transaction.arjunacore.jca.SubordinationManager;
import com.arjuna.ats.jta.TransactionManager;

/**
 * Inbound bridge stores information about REST-AT and JTA transactions. On creation of the bridge new subordinate JTA
 * transaction is created and bridge enlists it self as a participant to participate in recovery.
 * 
 * @author Gytis Trikleris
 */
@SuppressWarnings("serial")
public final class InboundBridge implements XAResource, Serializable {

    /**
     * Unique (well, hopefully) formatId so we can distinguish our own Xids.
     */
    public static final int XARESOURCE_FORMAT_ID = 131081;

    /**
     * TODO use logger instead of System.out
     */
    // private static Logger LOG = Logger.getLogger(InboundBridge.class);

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

    /**
     * Empty constructor for serialisation.
     */
    public InboundBridge() {
        System.out.println("InboundBridge.InboundBridge()");
    }

    /**
     * Constructor creates new transaction and enlists himself to it.
     * 
     * @param xid
     * @param txUrl
     * @param participantId
     * @throws XAException
     * @throws SystemException
     * @throws IllegalStateException
     * @throws RollbackException
     */
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
     * Associate the JTA transaction to the current Thread.
     * 
     * Method is used by inbound bridge preprocess interceptor.
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
     * Disassociate the JTA transaction from the current Thread.
     * 
     * Method is used by inbound bridge postprocess interceptor.
     * 
     * @throws SystemException
     */
    public void stop() throws SystemException {
        System.out.println("InboundBridge.stop()");

        TransactionManager.transactionManager().suspend();
    }

    /**
     * Returns XID of subordinate transaction.
     * 
     * @return Xid
     */
    public Xid getXid() {
        System.out.println("InboundBridge.getXid(). Returns xid=" + xid);
        return xid;
    }

    /**
     * Sets XID of subordinate transaction.
     * 
     * @param xid
     */
    public void setXid(Xid xid) {
        System.out.println("InboundBridge.setXid(Xid)");
        this.xid = xid;
    }

    /**
     * Returns URL of REST-AT transaction.
     * 
     * @return String
     */
    public String getTxUrl() {
        System.out.println("InboundBridge.getTxUrl(). Returns txUrl=" + txUrl);
        return txUrl;
    }

    /**
     * Sets URL of REST-AT transaction.
     * 
     * @param txUrl
     */
    public void setTxUrl(String txUrl) {
        System.out.println("InboundBridge.setTxUrl(String)");
        this.txUrl = txUrl;
    }

    /**
     * Returns ID of bridge's REST-AT participant.
     * 
     * @return String
     */
    public String getParticipantId() {
        System.out.println("InboundBridge.getParticipantId(). Returns particpantId=" + participantId);
        return participantId;
    }

    /**
     * Sets ID of bridge's REST-AT participant.
     * 
     * @param participantId
     */
    public void setParticipantId(String participantId) {
        System.out.println("InboundBridge.setParticipantId(String)");
        this.participantId = participantId;
    }

    /**
     * Compares this InboundBridge with another object.
     * 
     * @param o
     * @return boolean
     * @see java.lang.Object#equals(Object)
     */
    public boolean equals(Object o) {
        System.out.println("InboundBridge.equals(Object)");
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
     * Get the JTA subordinate transaction with current XID.
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

    /**
     * Serialises instance to the object output stream.
     * 
     * @param out
     * @throws IOException
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
        System.out.println("InboundBridge.writeObject(ObjectOutputStream). Persists xid=" + xid + ",txUrl=" + txUrl
                + ",participantId=" + participantId);

        out.writeObject(xid);
        out.writeObject(txUrl);
        out.writeObject(participantId);
    }

    /**
     * Recreates bridge from the object input stream.
     * 
     * @param in
     * @throws IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        System.out.println("InboundBridge.readObject(ObjectInputStream)");

        xid = (Xid) in.readObject();
        txUrl = (String) in.readObject();
        participantId = (String) in.readObject();

        InboundBridgeRecoveryModule.addRecoveredBridge(this);
    }

    // XAResource methods.

    @Override
    public void commit(Xid arg0, boolean arg1) throws XAException {
        System.out.println("InboundBridge.commit(Xid, boolean)");
    }

    @Override
    public void end(Xid arg0, int arg1) throws XAException {
        System.out.println("InboundBridge.end(Xid, int)");
    }

    @Override
    public void forget(Xid arg0) throws XAException {
        System.out.println("InboundBridge.forget(Xid)");
    }

    @Override
    public int getTransactionTimeout() throws XAException {
        System.out.println("InboundBridge.getTransactionTimeout()");
        return 0;
    }

    @Override
    public boolean isSameRM(XAResource arg0) throws XAException {
        System.out.println("InboundBridge.isSameRM(XAResource)");
        return false;
    }

    @Override
    public int prepare(Xid arg0) throws XAException {
        System.out.println("InboundBridge.prepare(Xid)");
        return XAResource.XA_OK;
    }

    @Override
    public Xid[] recover(int arg0) throws XAException {
        System.out.println("InboundBridge.recover(int)");
        return new Xid[0]; // TODO why not null?
    }

    @Override
    public void rollback(Xid arg0) throws XAException {
        System.out.println("InboundBridge.rollback(Xid)");
    }

    @Override
    public boolean setTransactionTimeout(int arg0) throws XAException {
        System.out.println("InboundBridge.setTransactionTimeout(int)");
        return false;
    }

    @Override
    public void start(Xid arg0, int arg1) throws XAException {
        System.out.println("InboundBridge.start(Xid, int)");
    }

}
