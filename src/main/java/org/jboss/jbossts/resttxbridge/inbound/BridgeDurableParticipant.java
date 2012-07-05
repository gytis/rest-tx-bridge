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

//import org.jboss.logging.Logger;
import org.jboss.jbossts.resttxbridge.annotation.Participant;
import org.jboss.jbossts.star.util.TxSupport;

import com.arjuna.ats.internal.jta.transaction.arjunacore.jca.SubordinationManager;

/**
 * Participant of REST transaction. Waits for the requests from REST-AT coordinator and terminates mapped JTA transactions.
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

    /**
     * TODO use logger instead of System.out
     */
    // private static final Logger LOG = Logger.getLogger(BridgeDurableParticipant.class);

    /**
     * Returns link to the participant terminator.
     * 
     * @param participantId
     * @param uriInfo
     * @return Link to the participant terminator.
     */
    @HEAD
    public Response headParticipant(@PathParam("participantId") String participantId, @Context UriInfo uriInfo) {
        System.out.println("BridgeDurableParticipant.headParticipant()");

        Response.ResponseBuilder builder = Response.ok();

        TxSupport.addLinkHeader(builder, uriInfo, TxSupport.TERMINATOR_LINK, TxSupport.TERMINATOR_LINK);

        return builder.build();
    }

    /**
     * TODO implement. Store transaction status in bridge
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
     * @param uriInfo
     * @param content
     * @return
     * @throws SystemException
     * @throws XAException
     */
    @PUT
    public Response terminateParticipant(@PathParam("participantId") String participantId, @Context UriInfo uriInfo,
            String content) throws XAException, SystemException {

        System.out.println("BridgeDurableParticipant.terminateParticipant(). participantId=" + participantId + ", status="
                + content);

        String txStatus = TxSupport.getStatus(content);

        if (TxSupport.isPrepare(txStatus)) {
            return prepare(participantId, uriInfo);
        } else if (TxSupport.isCommit(txStatus)) {
            return commit(participantId, uriInfo);
        } else if (txStatus.equals(TxSupport.COMMITTED_ONE_PHASE)) {
            return commitOnePhase(participantId, uriInfo);
        } else if (TxSupport.isAbort(txStatus)) {
            return rollback(participantId, uriInfo);
        }

        return Response.status(HttpURLConnection.HTTP_BAD_REQUEST).build();
    }

    /**
     * Prepares subordinate transaction. Returns Response with one of the following transaction statuses: TxSupport.PREPARED
     * TxSupport.READONLY TxSupport.ABORTED
     * 
     * Status code 409 is returned if transaction does not exist or preparation of subordinate transaction failed.
     * 
     * @param participantId
     * @param uriInfo
     * @return Response
     */
    private Response prepare(String participantId, UriInfo uriInfo) {
        int result = -1;
        Xid xid;

        try {
            xid = getXid(participantId, uriInfo);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            cleanup(participantId);
            return Response.status(409).build();
        }

        XATerminator xaTerminator = SubordinationManager.getXATerminator();

        try {
            result = xaTerminator.prepare(xid);
        } catch (XAException e) {
            System.err.println(e.getMessage());
            cleanup(participantId);
            return Response.status(409).build();
        }

        if (result != XAResource.XA_OK) {
            System.out.println("BridgeDurableParticipant was not prepared. XAResource status=" + result);
            cleanup(participantId);
        }

        if (result == XAResource.XA_OK) {
            return Response.ok(TxSupport.toStatusContent(TxSupport.PREPARED)).build();
        } else if (result == XAResource.XA_RDONLY) {
            return Response.ok(TxSupport.toStatusContent(TxSupport.READONLY)).build();
        }

        return Response.ok(TxSupport.toStatusContent(TxSupport.ABORTED)).build();
    }

    /**
     * Commits subordinate transaction. Returns transaction status TxSupport.COMMITTED on success or status code 409 on failure.
     * 
     * @param participantId
     * @param uriInfo
     * @return Response
     */
    private Response commit(String participantId, UriInfo uriInfo) {
        System.out.println("BridgeDurableParticipant.commit(participantId=" + participantId + ")");

        try {
            Xid xid = getXid(participantId, uriInfo);
            XATerminator xaTerminator = SubordinationManager.getXATerminator();
            xaTerminator.commit(xid, false);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return Response.status(409).build();
        }

        cleanup(participantId);

        return Response.ok(TxSupport.toStatusContent(TxSupport.COMMITTED)).build();
    }

    /**
     * Prepares and commits subordinate transaction.
     * 
     * Returns transaction status TxSupport.COMMITTED on success and status code 409 on failure.
     * 
     * @param participantId
     * @param uriInfo
     * @return Response
     */
    private Response commitOnePhase(String participantId, UriInfo uriInfo) {
        System.out.println("BridgeDurableParticipant.commitOnePhase(participantId=" + participantId + ")");

        Xid xid = null;
        XATerminator xaTerminator = SubordinationManager.getXATerminator();
        int prepared = -1;
        boolean commited = false;

        try {
            xid = getXid(participantId, uriInfo);
            prepared = xaTerminator.prepare(xid);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }

        if (prepared == XAResource.XA_OK) {
            try {
                xaTerminator.commit(xid, false);
                commited = true;
            } catch (XAException e) {
                System.err.println(e.getMessage());

                try {
                    xaTerminator.rollback(xid);
                } catch (XAException e1) {
                    System.err.println(e1.getMessage());
                }
            }
        }

        cleanup(participantId);

        if (commited) {
            return Response.ok(TxSupport.toStatusContent(TxSupport.COMMITTED)).build();
        }

        return Response.status(409).build();
    }

    /**
     * Rolls back subordinate transaction.
     * 
     * Returns transaction status TxSupport.ABORTED on success and status code 409 on failure.
     * 
     * @param participantId
     * @param uriInfo
     * @return Response
     */
    private Response rollback(String participantId, UriInfo uriInfo) {
        System.out.println("BridgeDurableParticipant.rollback(participantId=" + participantId + ")");

        try {
            Xid xid = getXid(participantId, uriInfo);
            XATerminator xaTerminator = SubordinationManager.getXATerminator();
            xaTerminator.rollback(xid);

        } catch (Exception e) {
            System.err.println(e.getMessage());
            return Response.status(409).build();
        } finally {
            cleanup(participantId);
        }

        return Response.ok(TxSupport.toStatusContent(TxSupport.ABORTED)).build();
    }

    /**
     * Cleans up mappings of this REST-AT.
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
     * @param uriInfo
     * @return Xid
     * @throws XAException
     * @throws SystemException
     * @throws RollbackException
     * @throws IllegalStateException
     * @throws IllegalArgumentException
     */
    private Xid getXid(String participantId, UriInfo uriInfo) throws IllegalStateException, XAException, SystemException, RollbackException {
        String txUrl = InboundBridgeManager.getParticipantTransaction(participantId);
        InboundBridge inboundBridge = InboundBridgeManager.getInboundBridge(txUrl, uriInfo.getBaseUri().toString());

        return inboundBridge.getXid();
    }

}
