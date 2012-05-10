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
public final class BridgeDurableParticipant {

    /**
     * Uniq String used to prefix ids at participant registration, so that the
     * recovery module can identify relevant instances.
     */
    public static final String TYPE_IDENTIFIER = "RestTxBridgeDurableParticipant_";

    /**
     * Uniq (well, hopefully) formatId so we can distinguish our own Xids.
     * 
     * TODO it is taken from WS bridge so probably has to be changed.
     */
    public static final int XARESOURCE_FORMAT_ID = 131080;

    /**
     * Participant url segment.
     */
    public static final String PARTICIPANT_SEGMENT = "/participant-resource";


    /**
     * Returns links to the participant terminator.
     * 
     * @param participantId
     * @param uriInfo
     * @return Link to the participant terminator.
     */
    @HEAD
    @Participant
    public Response headParticipant(
            @PathParam("participantId") String participantId,
            @Context UriInfo uriInfo) {

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
    @Participant
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
    @Participant
    public Response terminateParticipant(
            @PathParam("participantId") String participantId, String content) throws XAException, SystemException {
        
        System.out.println("BridgeDurableParticipant.terminateParticipant(). participantId=" + participantId + ", status=" + content);
        
        // TODO check if transaction is active
        
        String txStatus = TxSupport.getStatus(content);

        if (TxSupport.isPrepare(txStatus)) {
            String status = prepare(participantId);
            
            if (TxSupport.isPrepare(status)) {
                return Response.ok(TxSupport.toStatusContent(TxSupport.PREPARED)).build();
                
            } else if (TxSupport.isReadOnly(status)) {
                return Response.ok(TxSupport.toStatusContent(TxSupport.READONLY)).build();
                
            } else if (TxSupport.isAbort(status)) {
                return Response.ok(TxSupport.toStatusContent(TxSupport.ABORTED)).build();
                
            } else {
                throw new WebApplicationException(403);
            }
            
        } else if (TxSupport.isCommit(txStatus)) {
            commit(participantId);
            return Response.ok(TxSupport.toStatusContent(TxSupport.COMMITTED)).build();
            
        } else if (txStatus.equals(TxSupport.COMMITTED_ONE_PHASE)) {
            commit(participantId);
            return Response.ok(TxSupport.toStatusContent(TxSupport.COMMITTED)).build();
            
        } else if (TxSupport.isAbort(txStatus)) {
            rollback(participantId);
            return Response.ok(TxSupport.toStatusContent(TxSupport.ABORTED)).build();
        }

        return Response.status(HttpURLConnection.HTTP_BAD_REQUEST).build();
    }
    
    
    private String prepare(String participantId) {
        System.out.println("BridgeDurableParticipant.prepare(participantId=" + participantId + ")");
        
        try {
            Xid xid = getXid(participantId);
            XATerminator xaTerminator = SubordinationManager.getXATerminator();
            
            int result = xaTerminator.prepare(xid);
            if (result == XAResource.XA_OK) {
                return TxSupport.PREPARED;
                
            } else if (result == XAResource.XA_RDONLY) {
                cleanup(participantId);
                return TxSupport.READONLY;
            }
            
        } catch (Exception e) {
            // TODO log exception
            e.printStackTrace();
        }
        
        cleanup(participantId);
        return TxSupport.ABORTED;
    }
    
    
    private void commit(String participantId) {
        System.out.println("BridgeDurableParticipant.commit(participantId=" + participantId + ")");
        
        try {
            Xid xid = getXid(participantId);
            XATerminator xaTerminator = SubordinationManager.getXATerminator();
            
            xaTerminator.commit(xid, false);
            
            System.out.println("Participant <" + participantId + "> was commited");
            
        } catch (Exception e) {
            // TODO log exception
            e.printStackTrace();
        } finally {
            cleanup(participantId);
        }
    }
    
    
    private void rollback(String participantId) {
        System.out.println("BridgeDurableParticipant.rollback(participantId=" + participantId + ")");
        
        try {
            Xid xid = getXid(participantId);
            XATerminator xaTerminator = SubordinationManager.getXATerminator();
            
            xaTerminator.rollback(xid);
            
            System.out.println("Participant <" + participantId + "> was rolled back");
            
        } catch (Exception e) {
            // TODO log exception
            e.printStackTrace();
        } finally {
            cleanup(participantId);
        }
    }
    
    
    private void cleanup(String participantId) {
        System.out.println("BridgeDurableParticipant.cleanup(participantId=" + participantId + ")");
        
        InboundBridgeManager.removeParticipantMapping(participantId);
    }
    
    
    private Xid getXid(String participantId) throws XAException, SystemException {
        String txUrl = InboundBridgeManager.getParticipantTransaction(participantId);
        InboundBridge inboundBridge = InboundBridgeManager.getInboundBridge(txUrl);
        
        return inboundBridge.getXid();
    }

}
