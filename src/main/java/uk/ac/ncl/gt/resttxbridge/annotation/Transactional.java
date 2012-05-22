package uk.ac.ncl.gt.resttxbridge.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 
 * @author Gytis Trikleris
 * 
 * Annotation for JAX-RS methods which should be intercepted by rest-tx-bridge
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Transactional {

}
