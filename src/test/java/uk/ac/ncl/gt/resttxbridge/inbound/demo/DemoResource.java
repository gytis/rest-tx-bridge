package uk.ac.ncl.gt.resttxbridge.inbound.demo;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import uk.ac.ncl.gt.resttxbridge.annotation.Transactional;

@Path("/demo")
public final class DemoResource {

    @GET
    @Transactional
    public String transactionalGet() {
        return "transactionalGet";
    }
    
    @GET
    @Path("/simple")
    public String simpleGet() {
        return "simpleGet";
    }
    
}
