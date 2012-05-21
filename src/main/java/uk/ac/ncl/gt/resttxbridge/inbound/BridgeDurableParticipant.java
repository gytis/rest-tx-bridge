package uk.ac.ncl.gt.resttxbridge.inbound;

import java.net.HttpURLConnection;

import javax.resource.spi.XATerminator;
import javax.transaction.SystemException;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.jboss.jbossts.star.util.TxSupport;

import com.arjuna.ats.internal.jta.transaction.arjunacore.jca.SubordinationManager;

import uk.ac.ncl.gt.resttxbridge.annotation.Participant;


@Path(BridgeDurableParticipant.PARTICIPANT_SEGMENT + "/{participantId}")
@Participant
public final class BridgeDurableParticipant {

    /**
     * Unique String used to prefix IDs at participant registration, so that the
     * recovery module can identify relevant instances.
     */
    public static final String TYPE_IDENTIFIER = "RestTxBridgeDurableParticipant_";

    /**
     * Unique (well, hopefully) formatId so we can distinguish our own Xids.
     * 
     * TODO it is taken from WS bridge so probably has to be changed.
     */
    public static final int XARESOURCE_FORMAT_ID = 131080;

    /**
     * Participant URL segment.
     */
    public static final String PARTICIPANT_SEGMENT = "participant-resource";


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
    public Response headParticipant(
            @PathParam("participantId") String participantId) {

        System.out.println("BridgeDurableParticipant.headParticipant()");

        Response.ResponseBuilder builder = Response.ok();

        TxSupport.addLinkHeader(builder, uriInfo, TxSupport.TERMINATOR_LINK,
                TxSupport.TERMINATOR_LINK);

        return builder.build();
    }


    /**
     * Returns current status of the participant.
     * 
     * @return
     * @throws SystemException 
     * @throws XAException 
     */
    @GET
    public Response getStatus(@PathParam("participantId") String participantId) {
        System.out.println("BridgeDurableParticipant.getStatus()");

        // TODO check status of the subordinate transaction
        
        return null;
    }


    /**
     * Terminates participant.
     * Content has to contain one of the following statuses:
     *      TxSupport.PREPARED
     *      TxSupport.COMMITED
     *      TxSupport.ABORTED
     * 
     * @param participantId
     * @param content
     * @return
     * @throws SystemException 
     * @throws XAException 
     */
    @PUT
    public Response terminateParticipant(
            @PathParam("participantId") String participantId, String content) throws XAException, SystemException {
        
        System.out.println("BridgeDurableParticipant.terminateParticipant(). participantId=" + participantId + ", status=" + content);
        
        // TODO check if transaction is active
        
        String txStatus = TxSupport.getStatus(content);
        String responseStatus = null;

        if (TxSupport.isPrepare(txStatus)) {
            String status = prepare(participantId);
            
            if (TxSupport.isPrepare(status)) {
                responseStatus = TxSupport.PREPARED;
                
            } else if (TxSupport.isReadOnly(status)) {
                responseStatus = TxSupport.READONLY;
                
            } else if (TxSupport.isAbort(status)) {
                responseStatus = TxSupport.ABORTED;
                
            } else {
                throw new WebApplicationException(403);
            }
            
        } else if (TxSupport.isCommit(txStatus)) {
            commit(participantId);
            responseStatus = TxSupport.COMMITTED;
            
        } else if (txStatus.equals(TxSupport.COMMITTED_ONE_PHASE)) {
            commitOnePhase(participantId);
            responseStatus = TxSupport.COMMITTED;
            
        } else if (TxSupport.isAbort(txStatus)) {
            rollback(participantId);
            responseStatus = TxSupport.ABORTED;
        }

        if (responseStatus == null) {
            return Response.status(HttpURLConnection.HTTP_BAD_REQUEST).build();
        } else {
            return Response.ok(TxSupport.toStatusContent(responseStatus)).build();
        }
    }
    
    
    /**
     * Prepares subordinate transaction.
     * Returns one of the following statuses:
     *      TxSupport.PREPARED
     *      TxSupport.READONLY
     *      TxSupport.ABORTED
     * 
     * @param participantId
     * @return Transaction status
     */
    private String prepare(String participantId) {
        System.out.println("BridgeDurableParticipant.prepare(participantId=" + participantId + ")");
        
        String responseStatus = TxSupport.ABORTED;
        
        try {
            Xid xid = getXid(participantId);
            XATerminator xaTerminator = SubordinationManager.getXATerminator();
            
            int result = xaTerminator.prepare(xid);
            if (result == XAResource.XA_OK) {
                responseStatus = TxSupport.PREPARED;
                
            } else if (result == XAResource.XA_RDONLY) {
                responseStatus = TxSupport.READONLY;
            }
            
        } catch (Exception e) {
            // TODO log exception
            e.printStackTrace();
        }
        
        if (!TxSupport.isPrepare(responseStatus)) {
            cleanup(participantId);
        }
        
        return responseStatus;
    }
    
    
    /**
     * Commits subordinate transaction.
     * 
     * @param participantId
     */
    private void commit(String participantId) {
        System.out.println("BridgeDurableParticipant.commit(participantId=" + participantId + ")");
        
        try {
            Xid xid = getXid(participantId);
            XATerminator xaTerminator = SubordinationManager.getXATerminator();
            
            xaTerminator.commit(xid, false);
            
        } catch (Exception e) {
            // TODO log exception
            e.printStackTrace();
        } finally {
            cleanup(participantId);
        }
    }
    
    
    /**
     * Prepares and commits subordinate transaction.
     * 
     * @param participantId
     */
    private void commitOnePhase(String participantId) {
        System.out.println("BridgeDurableParticipant.commitOnePhase(participantId=" + participantId + ")");
        
        try {
            Xid xid = getXid(participantId);
            XATerminator xaTerminator = SubordinationManager.getXATerminator();
            
            if (xaTerminator.prepare(xid) == XAResource.XA_OK) {
                commit(participantId);
            } else {
                cleanup(participantId);
            }
            
        } catch (Exception e) {
            // TODO log exception
            e.printStackTrace();
            cleanup(participantId);
        }
    }
    
    
    /**
     * Rolls back subordinate transaction. 
     * @param participantId
     */
    private void rollback(String participantId) {
        System.out.println("BridgeDurableParticipant.rollback(participantId=" + participantId + ")");
        
        try {
            Xid xid = getXid(participantId);
            XATerminator xaTerminator = SubordinationManager.getXATerminator();
            
            xaTerminator.rollback(xid);
            
        } catch (Exception e) {
            // TODO log exception
            e.printStackTrace();
        } finally {
            cleanup(participantId);
        }
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
            // TODO log exception
            e.printStackTrace();
        }
    }
    
    
    /**
     * Returns Xid of the subordinate transaction which is mapped with
     * given bridge durable participant.
     * 
     * @param participantId
     * @return
     * @throws XAException
     * @throws SystemException
     */
    private Xid getXid(String participantId) throws XAException, SystemException {
        String txUrl = InboundBridgeManager.getParticipantTransaction(participantId);
        InboundBridge inboundBridge = InboundBridgeManager.getInboundBridge(
                txUrl, uriInfo.getBaseUri().toString());
        
        return inboundBridge.getXid();
    }

}
