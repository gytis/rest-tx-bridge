package org.jboss.jbossts.resttxbridge.inbound.test.recovery;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import org.jboss.jbossts.resttxbridge.annotation.Transactional;

/**
 * 
 * @author Gytis Trikleris
 * 
 */
@Path("/")
public class TransactionalResourceForRecovery {

    @POST
    @Transactional
    public Response dummyPost() {
        System.out.println("TransactionalResourceForRecovery.dummyPost");

        return Response.ok().build();
    }

}
