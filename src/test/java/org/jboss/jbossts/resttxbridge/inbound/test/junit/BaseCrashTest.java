package org.jboss.jbossts.resttxbridge.inbound.test.junit;

import java.io.File;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.jbossts.resttxbridge.inbound.test.common.DummyParticipant;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Assert;

import com.arjuna.ats.arjuna.common.RecoveryEnvironmentBean;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;

public class BaseCrashTest {
    public static final String DEPLOYMENT_NAME = "rest-tx-bridge-recovery-test";

    public static final String CONTAINER_NAME = "jbossas-manual";

    public static final String TXN_MGR_URL = "http://localhost:8080/rest-tx/tx/transaction-manager";

    public static final String BASE_URL = "http://localhost:8080/" + DEPLOYMENT_NAME;
    
    public static final String DUMMY_URL = BASE_URL + "/" + DummyParticipant.PARTICIPANT_SEGMENT;

    protected String BytemanArgs = "-Xms64m -Xmx512m -XX:MaxPermSize=256m -Dorg.jboss.byteman.verbose -Djboss.modules.system.pkgs=org.jboss.byteman -Dorg.jboss.byteman.transform.all -javaagent:target/test-classes/lib/byteman.jar=script:target/test-classes/scripts/@BMScript@.btm,boot:target/test-classes/lib/byteman.jar,listener:true";

    protected String javaVmArguments;

    protected String testName;

    protected String scriptName;

    @ArquillianResource
    protected ContainerController controller;

    @ArquillianResource
    protected Deployer deployer;

    private static final String MANIFEST_MF = "Manifest-Version: 1.0\n"
            + "Dependencies: org.jboss.jts, org.hornetq, org.jboss.logging\n";

    @Deployment(name = DEPLOYMENT_NAME, testable = false, managed = false)
    @TargetsContainer(CONTAINER_NAME)
    public static Archive<?> createTestArchive() {
        WebArchive archive = ShrinkWrap.create(WebArchive.class, DEPLOYMENT_NAME + ".war")
                .addAsWebInfResource(new File("src/test/webapp", "WEB-INF/web-recovery.xml"), "web.xml")
                .addPackages(false, "org.jboss.jbossts.resttxbridge.annotation")
                .addPackages(false, "org.jboss.jbossts.resttxbridge.inbound")
                .addPackages(false, "org.jboss.jbossts.resttxbridge.inbound.provider")
                .addPackages(false, "org.jboss.jbossts.resttxbridge.inbound.test.recovery")
                .addPackages(false, "org.jboss.jbossts.resttxbridge.inbound.test.common")
                .addPackages(true, "org.jboss.jbossts.star")
                .setManifest(new StringAsset(MANIFEST_MF));
        System.out.println(archive.toString(true));
        return archive;
    }

    @Before
    public void setUp() {
        RecoveryEnvironmentBean recoveryEnvironmentBean = BeanPopulator
                .getDefaultInstance(RecoveryEnvironmentBean.class);
        
        recoveryEnvironmentBean.setPeriodicRecoveryPeriod(10);
        
        javaVmArguments = BytemanArgs.replace("@BMScript@", scriptName);

        javaVmArguments += " -Dcom.arjuna.ats.arjuna.recovery.PeriodicRecoveryPeriod=10";
        
        File file = new File("testlog");
        if (file.isFile() && file.exists()) {
            file.delete();
        }

        // Ensure ObjectStore is empty:
        String jbossHome = System.getenv("JBOSS_HOME");
        if (jbossHome == null) {
            Assert.fail("$JBOSS_HOME not set");
        } else {
            File objectStore = new File(jbossHome + File.separator + "standalone" + File.separator + "data" + File.separator
                    + "tx-object-store");
            System.out.println("Deleting: " + objectStore.getPath());

            if (objectStore.exists()) {
                boolean success = deleteDirectory(objectStore);
                if (!success) {
                    System.err.println("Failed to remove tx-object-store");
                    Assert.fail("Failed to remove tx-object-store: " + objectStore.getPath());
                } else {
                    System.out.println("remove tx-object-store: " + objectStore.getPath());
                }
            }

        }
    }

    @After
    public void tearDown() {
        String log = "target/log";

        if (testName != null && scriptName != null) {
            String logFileName = scriptName + "." + testName;
            File file = new File("testlog");
            File logDir = new File(log);

            if (!logDir.exists()) {
                logDir.mkdirs();
            }

            if (file.isFile() && file.exists()) {
                file.renameTo(new File(log + "/" + logFileName));
            }
        }
    }

    // protected void runTest(String testClass) throws Exception {
    // Config config = new Config();
    // // javaVmArguments += " -Dtest=" + testClass;
    // config.add("javaVmArguments", javaVmArguments);
    //
    // controller.start(CONTAINER_NAME, config.map());
    // deployer.deploy(DEPLOYMENT_NAME);
    //
    // // // Waiting for crashing
    // // controller.kill(CONTAINER_NAME);
    // //
    // // // Boot jboss-as after crashing
    // // config.add("javaVmArguments", javaVmArguments);
    // // controller.start(CONTAINER_NAME, config.map());
    //
    // controller.kill(CONTAINER_NAME);
    // }

    private boolean deleteDirectory(File path) {
        if (path.exists()) {
            File[] files = path.listFiles();
            for (int i = 0; i < files.length; i++) {
                if (files[i].isDirectory()) {
                    deleteDirectory(files[i]);
                } else {
                    files[i].delete();
                }
            }
        }
        return (path.delete());
    }

}