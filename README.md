Rest TX Bridge
======================================================
Author: Gytis Trikleris (gytist@gmail.com)
Technologies: REST-AT Bridge

What is it?
-----------
Bridge between REST-AT and JTA transactions.

Build and Deploy
-------------------------

1. Download and build rest-tx coordinator (https://github.com/jbosstm/narayana/tree/master/rest-tx).

      mvn clean install

2. Set correct value for jbossHome property in Arquillian configuration file:

        src/test/resources/arquillian.xml
		
3. Go to the root directory of the project and execute following command.

        mvn clean install

4. Arquillian test will be executed and output will be printed to the console. If everything went successfully code will be stored in your local Maven repository.

5. In order to make use of the bridge in other projects (e.g. quickstarts), please copy rest-tx-bridge.jar, restat-util-XXX.jar (created in step 1), and module.xml to

        $JBOSS_HOME/modules/org/jboss/rts/main
        
6. If version of the restat-util-XXX.jar is not 5.0.0.M2-SNAPSHOT, please update the module.xml with correct version. 