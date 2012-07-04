package org.jboss.jbossts.resttxbridge.inbound.test.recovery;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;

import org.jboss.jbossts.resttxbridge.inbound.BridgeDurableParticipant;
import org.jboss.jbossts.resttxbridge.inbound.provider.InboundBridgePostProcessInterceptor;
import org.jboss.jbossts.resttxbridge.inbound.provider.InboundBridgePreProcessInterceptor;
import org.jboss.jbossts.resttxbridge.inbound.test.common.DummyParticipant;

public class TransactionalApplicationForRecovery extends Application {
    public Set<Class<?>> getClasses() {
        Set<Class<?>> classes = new HashSet<Class<?>>();

        classes.add(BridgeDurableParticipant.class);
        classes.add(InboundBridgePreProcessInterceptor.class);
        classes.add(InboundBridgePostProcessInterceptor.class);

        return classes;
    }

    public Set<Object> getSingletons() {
        Set<Object> singletons = new HashSet<Object>();

        singletons.add(new DummyParticipant());
        singletons.add(new TransactionalResourceForRecovery());

        return singletons;
    }
}
