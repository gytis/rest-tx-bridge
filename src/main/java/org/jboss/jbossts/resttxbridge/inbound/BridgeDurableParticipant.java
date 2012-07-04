package org.jboss.jbossts.resttxbridge.inbound;

import java.net.HttpURLConnection;

import javax.resource.spi.XATerminator;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.log4j.Logger;
import org.jboss.jbossts.resttxbridge.annotation.Participant;
import org.jboss.jbossts.star.util.TxSupport;

import com.arjuna.ats.internal.jta.transaction.arjunacore.jca.SubordinationManager;


/**
 * Participant of REST transaction.
 * Waits for the requests from REST-AT coordinator and terminates mapped JTA transactions.
 * 
 * @author Gytis Trikleris
 */
@Path(BridgeDurableParticipant.PARTICIPANT_SEGMENT + "/{participantId}")
@Participant
public final class BridgeDurableParticipant {

    /**
     * Unique String used to prefix IDs at participant registration, so that the recovery module can identify relevant
     * instances.
     */
    public static final String TYPE_IDENTIFIER = "RestTxBridgeDurableParticipant_";

    /**
     * Participant URL segment.
     */
    public static final String PARTICIPANT_SEGMENT = "participant-resource";
    
    private static final Logger LOG = Logger.getLogger(BridgeDurableParticipant.class);

    // TODO move to method declarations
    @Context
    private UriInfo uriInfo;

    /**
     * Returns links to the participant terminator.
     * 
     * @param participantId
     * @param uriInfo
     * @return Link to the participant terminator.
     */
    @HEAD
    public Response headParticipant(@PathParam("participantId") String participantId) {
        System.out.println("BridgeDurableParticipant.headParticipant()");

        Response.ResponseBuilder builder = Response.ok();

        TxSupport.addLinkHeader(builder, uriInfo, TxSupport.TERMINATOR_LINK, TxSupport.TERMINATOR_LINK);

        return builder.build();
    }

    /**
     * TODO implement
     * 
     * Returns current status of the participant.
     * 
     * @return
     * @throws SystemException
     * @throws XAException
     */
    @GET
    public Response getStatus(@PathParam("participantId") String participantId) {
        System.out.println("BridgeDurableParticipant.getStatus()");

        return null;
    }

    /**
     * Terminates participant. Content has to contain one of the following statuses: TxSupport.PREPARED TxSupport.COMMITED
     * TxSupport.ABORTED
     * 
     * @param participantId
     * @param content
     * @return
     * @throws SystemException
     * @throws XAException
     */
    @PUT
    public Response terminateParticipant(@PathParam("participantId") String participantId, String content) throws XAException,
            SystemException {

        System.out.println("BridgeDurableParticipant.terminateParticipant(). participantId=" + participantId + ", status="
                + content);

        String txStatus = TxSupport.getStatus(content);
        
        if (TxSupport.isPrepare(txStatus)) {
            return prepare(participantId);
        } else if (TxSupport.isCommit(txStatus)) {
            return commit(participantId);
        } else if (txStatus.equals(TxSupport.COMMITTED_ONE_PHASE)) {
            return commitOnePhase(participantId);
        } else if (TxSupport.isAbort(txStatus)) {
            return rollback(participantId);
        }
        
        return Response.status(HttpURLConnection.HTTP_BAD_REQUEST).build();
    }
    
    /**
     * Prepares subordinate transaction. Returns Response with one of the following statuses: TxSupport.PREPARED
     * TxSupport.READONLY TxSupport.ABORTED
     * 
     * @param participantId
     * @return Response
     */
    private Response prepare(String participantId) {
        int result = -1;
        Xid xid;
        
        try {
            xid = getXid(participantId);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return Response.status(409).build();
        }
        
        XATerminator xaTerminator = SubordinationManager.getXATerminator();
        
        try {
            result = xaTerminator.prepare(xid);
        } catch (XAException e) {
            System.err.println(e.getMessage());
            return Response.status(409).build();
        }
        
        if (result != XAResource.XA_OK) {
            System.out.println("BridgeDurableParticipant was not prepared. XAResource status=" + result);
            cleanup(participantId);
        }
        
        if (result == XAResource.XA_OK) {
            return Response.ok(TxSupport.toStatusContent(TxSupport.PREPARED)).build();
        } else if (result == XAResource.XA_RDONLY) {
            // TODO should DELETE request be executed as mentioned in spec (12p. line 378)
            return Response.ok(TxSupport.toStatusContent(TxSupport.READONLY)).build();
        }
        
        return Response.ok(TxSupport.toStatusContent(TxSupport.ABORTED)).build();
    }

    /**
     * Commits subordinate transaction.
     * 
     * @param participantId
     * @return Response
     */
    private Response commit(String participantId) {
        System.out.println("BridgeDurableParticipant.commit(participantId=" + participantId + ")");
        
        try {
            Xid xid = getXid(participantId);
            XATerminator xaTerminator = SubordinationManager.getXATerminator();
            xaTerminator.commit(xid, false);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return Response.status(409).build();
        } finally {
            cleanup(participantId);
        }
        
        return Response.ok(TxSupport.COMMITTED).build();
    }

    /**
     * Prepares and commits subordinate transaction.
     * 
     * @param participantId
     * @return Response
     */
    private Response commitOnePhase(String participantId) {
        System.out.println("BridgeDurableParticipant.commitOnePhase(participantId=" + participantId + ")");
        
        boolean commited = false;
        try {
            Xid xid = getXid(participantId);
            XATerminator xaTerminator = SubordinationManager.getXATerminator();
            if (xaTerminator.prepare(xid) == XAResource.XA_OK) {
                commit(participantId);
                commited = true;
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        } finally {
            cleanup(participantId);
        }
        
        if (commited) {
            return Response.ok(TxSupport.COMMITTED).build();
        }

        return Response.status(409).build();
    }

    /**
     * Rolls back subordinate transaction.
     * 
     * @param participantId
     * @return Response
     */
    private Response rollback(String participantId) {
        System.out.println("BridgeDurableParticipant.rollback(participantId=" + participantId + ")");
        
        try {
            Xid xid = getXid(participantId);
            XATerminator xaTerminator = SubordinationManager.getXATerminator();
            xaTerminator.rollback(xid);

        } catch (Exception e) {
            System.err.println(e.getMessage());
            return Response.status(409).build();
        } finally {
            cleanup(participantId);
        }
        
        return Response.ok(TxSupport.ABORTED).build();
    }

    /**
     * Cleans up references.
     * 
     * @param participantId
     */
    private void cleanup(String participantId) {
        System.out.println("BridgeDurableParticipant.cleanup(participantId=" + participantId + ")");

        try {
            InboundBridgeManager.removeParticipantMapping(participantId);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    /**
     * Returns Xid of the subordinate transaction which is mapped with given bridge durable participant.
     * 
     * @param participantId
     * @return
     * @throws XAException
     * @throws SystemException
     * @throws RollbackException 
     * @throws IllegalStateException 
     */
    private Xid getXid(String participantId) throws XAException, SystemException, IllegalStateException, RollbackException {
        String txUrl = InboundBridgeManager.getParticipantTransaction(participantId);
        InboundBridge inboundBridge = InboundBridgeManager.getInboundBridge(txUrl, uriInfo.getBaseUri().toString());

        return inboundBridge.getXid();
    }

}
