package org.jboss.jbossts.resttxbridge.inbound.service;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;

import org.jboss.jbossts.resttxbridge.inbound.BridgeDurableParticipant;
import org.jboss.jbossts.resttxbridge.inbound.provider.InboundBridgePostProcessInterceptor;
import org.jboss.jbossts.resttxbridge.inbound.provider.InboundBridgePreProcessInterceptor;


/**
 * 
 * @author Gytis Trikleris
 * 
 */
public final class TransactionalApplication extends Application {

    public Set<Class<?>> getClasses() {
        Set<Class<?>> classes = new HashSet<Class<?>>();

        classes.add(BridgeDurableParticipant.class);

        return classes;
    }

    public Set<Object> getSingletons() {
        Set<Object> singletons = new HashSet<Object>();

        singletons.add(new DummyParticipant());
        singletons.add(new TransactionalResource());
        singletons.add(new InboundBridgePreProcessInterceptor());
        singletons.add(new InboundBridgePostProcessInterceptor());

        return singletons;
    }

}
