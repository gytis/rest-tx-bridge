package uk.ac.ncl.gt.resttxbridge.inbound.service;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;

import uk.ac.ncl.gt.resttxbridge.inbound.BridgeDurableParticipant;
import uk.ac.ncl.gt.resttxbridge.inbound.demo.DemoResource;
import uk.ac.ncl.gt.resttxbridge.inbound.provider.InboundBridgePostProcessInterceptor;
import uk.ac.ncl.gt.resttxbridge.inbound.provider.InboundBridgePreProcessInterceptor;


public final class InboundBridgeService extends Application {

    public Set<Class<?>> getClasses() {
        Set<Class<?>> classes = new HashSet<Class<?>>();

        classes.add(DemoResource.class);
        classes.add(BridgeDurableParticipant.class);

        return classes;
    }


    public Set<Object> getSingletons() {
        Set<Object> singletons = new HashSet<Object>();

        singletons.add(new InboundBridgePreProcessInterceptor());
        singletons.add(new InboundBridgePostProcessInterceptor());

        return singletons;
    }

}
