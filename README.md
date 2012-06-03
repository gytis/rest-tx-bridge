Rest TX Bridge
======================================================
Author: Gytis Trikleris (gytist@gmail.com)
Technologies: Rest TX Bridge

What is it?
-----------
Bridge between rest-tx and JPA transactions.

Build and Deploy
-------------------------

1. Download and deploy rest-tx coordinator (https://github.com/jbosstm/narayana/tree/master/rest-tx).

2. Make sure that "org.jboss.narayana.rts:restat-util:5.0.0.M2-SNAPSHOT" artifact is in your local Maven repository (comes from 1 step).

3. Set correct value for jbossHome property in Arquillian configuration file:

        src/test/resources/arquillian.xml
		
4. Go to the root directory of the project and execute following command.

        mvn clean install

5. Arquillian test will be executed and output will be printed to the console. If everything went successfully code will be stored in your local Maven repository.