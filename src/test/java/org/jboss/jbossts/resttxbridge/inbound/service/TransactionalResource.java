package org.jboss.jbossts.resttxbridge.inbound.service;

import javax.transaction.Transaction;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.hornetq.utils.json.JSONArray;
import org.jboss.jbossts.resttxbridge.annotation.Transactional;
import org.jboss.jbossts.resttxbridge.inbound.xa.LoggingXAResource;

import com.arjuna.ats.jta.TransactionManager;


/**
 * 
 * @author Gytis Trikleris
 * 
 */
@Path("/")
public class TransactionalResource {

    LoggingXAResource loggingXAResource;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String getInvocations() {
        if (loggingXAResource == null) {
            throw new WebApplicationException(409);
        }

        return new JSONArray(loggingXAResource.getInvocations()).toString();
    }

    @POST
    @Transactional
    public Response enlistXAResource() {
        System.out.println("TransactionalResource.enlistXAResource");

        try {
            loggingXAResource = new LoggingXAResource();

            Transaction t = TransactionManager.transactionManager().getTransaction();
            t.enlistResource(loggingXAResource);

        } catch (Exception e) {
            e.printStackTrace();

            return Response.serverError().build();
        }

        return Response.ok().build();
    }

    @PUT
    public Response resetXAResource() {
        if (loggingXAResource != null) {
            loggingXAResource.resetInvocations();
        }

        return Response.ok().build();
    }

}
