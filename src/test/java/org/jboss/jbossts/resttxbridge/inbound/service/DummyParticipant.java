package org.jboss.jbossts.resttxbridge.inbound.service;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.hornetq.utils.json.JSONArray;
import org.jboss.jbossts.star.util.TxSupport;

/**
 * 
 * @author Gytis Trikleris
 *
 */
@Path(DummyParticipant.PARTICIPANT_SEGMENT)
public final class DummyParticipant {

    /**
     * Participant URL segment.
     */
    public static final String PARTICIPANT_SEGMENT = "dummy-participant-resource";

    private final List<String> invocations = new ArrayList<String>();

    @Context
    private UriInfo uriInfo;

    /**
     * Returns links to the participant terminator.
     * 
     * @return Link to the participant terminator.
     */
    @HEAD
    public Response headParticipant() {
        System.out.println("DummyParticipant.headParticipant()");
        invocations.add("DummyParticipant.headParticipant()");

        Response.ResponseBuilder builder = Response.ok();

        TxSupport.addLinkHeader(builder, uriInfo, TxSupport.TERMINATOR_LINK, TxSupport.TERMINATOR_LINK);

        return builder.build();
    }

    /**
     * Returns current status of the participant.
     * 
     * @param participantId
     * @return
     */
    @GET
    public Response getStatus() {
        System.out.println("DummyParticipant.getStatus()");
        invocations.add("DummyParticipant.getStatus()");

        // TODO check status of the subordinate transaction

        return null;
    }

    /**
     * Terminates participant.
     * 
     * @param content
     * @return
     */
    @PUT
    public Response terminateParticipant(String content) {
        System.out.println("DummyParticipant.terminateParticipant(" + content + ")");
        invocations.add("DummyParticipant.terminateParticipant(" + content + ")");

        String txStatus = TxSupport.getStatus(content);
        String responseStatus = null;

        if (TxSupport.isPrepare(txStatus)) {
            responseStatus = TxSupport.PREPARED;

        } else if (TxSupport.isCommit(txStatus)) {
            responseStatus = TxSupport.COMMITTED;

        } else if (txStatus.equals(TxSupport.COMMITTED_ONE_PHASE)) {
            responseStatus = TxSupport.COMMITTED;

        } else if (TxSupport.isAbort(txStatus)) {
            responseStatus = TxSupport.ABORTED;
        }

        if (responseStatus == null) {
            return Response.status(HttpURLConnection.HTTP_BAD_REQUEST).build();
        } else {
            return Response.ok(TxSupport.toStatusContent(responseStatus)).build();
        }
    }

    @GET
    @Path("invocations")
    @Produces(MediaType.APPLICATION_JSON)
    public String getInvocations() {
        return new JSONArray(invocations).toString();
    }

    @PUT
    @Path("invocations")
    public Response resetInvocations() {
        invocations.clear();
        return Response.ok().build();
    }

}
