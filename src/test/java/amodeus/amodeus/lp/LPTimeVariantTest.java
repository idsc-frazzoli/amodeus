/* amodeus - Copyright (c) 2018, ETH Zurich, Institute for Dynamic Systems and Control */
package amodeus.amodeus.lp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.matsim.amodeus.config.AmodeusConfigGroup;
import org.matsim.amodeus.config.modal.GeneratorConfig;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;

import amodeus.amodeus.options.LPOptions;
import amodeus.amodeus.options.LPOptionsBase;
import amodeus.amodeus.options.ScenarioOptions;
import amodeus.amodeus.options.ScenarioOptionsBase;
import amodeus.amodeus.prep.VirtualNetworkCreator;
import amodeus.amodeus.test.TestFileHandling;
import amodeus.amodeus.util.io.Locate;
import amodeus.amodeus.util.io.MultiFileTools;
import amodeus.amodeus.util.math.GlobalAssert;
import amodeus.amodeus.virtualnetwork.core.VirtualNetwork;
import ch.ethz.idsc.tensor.RealScalar;
import ch.ethz.idsc.tensor.Tensors;

public class LPTimeVariantTest {
    private static VirtualNetwork<Link> virtualNetwork2;
    private static VirtualNetwork<Link> virtualNetwork3;
    private static Population population;
    private static Network network;
    private static LPOptions lpOptions;
    private static int endTime;

    @BeforeClass
    public static void setUp() throws IOException {
        System.out.println(LPTimeVariant.class.getName());
        // copy scenario data into main directory
        File scenarioDirectory = new File(Locate.repoFolder(LPTimeVariantTest.class, "amodeus"), "resources/testScenario");
        File workingDirectory = MultiFileTools.getDefaultWorkingDirectory();
        GlobalAssert.that(workingDirectory.isDirectory());
        TestFileHandling.copyScnearioToMainDirectory(scenarioDirectory.getAbsolutePath(), workingDirectory.getAbsolutePath());

        /* input data */
        scenarioDirectory = new File(Locate.repoFolder(LPTimeVariantTest.class, "amodeus"), "resources/testScenario");
        ScenarioOptions scenarioOptions = new ScenarioOptions(scenarioDirectory, ScenarioOptionsBase.getDefault());
        File configFile = new File(scenarioOptions.getPreparerConfigName());
        AmodeusConfigGroup avCg = new AmodeusConfigGroup();
        Config config = ConfigUtils.loadConfig(configFile.getAbsolutePath(), avCg);
        GeneratorConfig genConfig = avCg.getModes().values().iterator().next().getGeneratorConfig();
        int numRt = genConfig.getNumberOfVehicles();
        endTime = (int) config.qsim().getEndTime().seconds();
        Scenario scenario = ScenarioUtils.loadScenario(config);
        network = scenario.getNetwork();
        population = scenario.getPopulation();

        // create 2 node virtual network
        scenarioOptions.setProperty(ScenarioOptionsBase.NUMVNODESIDENTIFIER, "2");
        VirtualNetworkCreator virtualNetworkCreator = scenarioOptions.getVirtualNetworkCreator();
        virtualNetwork2 = virtualNetworkCreator.create(network, population, scenarioOptions, numRt, endTime);

        // create 3 node virtual network
        scenarioOptions.setProperty(ScenarioOptionsBase.NUMVNODESIDENTIFIER, "3");
        virtualNetworkCreator = scenarioOptions.getVirtualNetworkCreator();
        virtualNetwork3 = virtualNetworkCreator.create(network, population, scenarioOptions, numRt, endTime);

        lpOptions = new LPOptions(workingDirectory, LPOptionsBase.getDefault());
    }

    @Test
    public void testCreation() {
        System.out.println(virtualNetwork2.getvLinksCount());
        assertEquals(virtualNetwork2.getvLinksCount(), 2);
        assertEquals(virtualNetwork2.getvNodesCount(), 2);

        assertEquals(virtualNetwork3.getvLinksCount(), 6);
        assertEquals(virtualNetwork3.getvNodesCount(), 3);
    }

    @Test
    public void testLP2Nodes() {
        // init LP time-invariant
        LPTimeVariant lp = new LPTimeVariant(virtualNetwork2, network, lpOptions, Tensors.of(Tensors.of(Tensors.vector(0, 0), Tensors.vector(0, 0))), 100, endTime);
        assertNull(lp.getAlphaRate_ij());
        assertNull(lp.getAlphaAbsolute_ij());
        assertNull(lp.getFRate_ij());
        assertNull(lp.getFAbsolute_ij());
        assertNull(lp.getV0_i());
        assertEquals(lp.getTimeIntervalLength(), 30 * 3600); // there is only one time interval over the whole day

        // test trivial case
        lp = new LPTimeVariant(virtualNetwork2, network, lpOptions, Tensors.of(Tensors.of(Tensors.vector(0, 0), Tensors.vector(0, 0))), 100, endTime);
        lp.initiateLP();
        lp.solveLP(false);
        assertEquals(lp.getAlphaAbsolute_ij(), Tensors.of(Tensors.of(Tensors.vector(0, 0), Tensors.vector(0, 0))));
        assertEquals(lp.getAlphaRate_ij(), Tensors.of(Tensors.of(Tensors.vector(0, 0), Tensors.vector(0, 0))));

        // test infeasible cases
        try {
            lp = new LPTimeVariant(virtualNetwork2, network, lpOptions, Tensors.of(Tensors.of(Tensors.vector(0, -1), Tensors.vector(0, 0))), 100, endTime);
            fail();
        } catch (Exception exception) {
            // ---
        }

        try {
            lp = new LPTimeVariant(virtualNetwork2, network, lpOptions, Tensors.of(Tensors.of(Tensors.vector(0, 0.01), Tensors.vector(0, 0))), 100, endTime);
            fail();
        } catch (Exception exception) {
            // ---
        }

        // test simple rounding case
        lp = new LPTimeVariant(virtualNetwork2, network, lpOptions, Tensors.of(Tensors.of(Tensors.vector(0, 0.00001), Tensors.vector(0, 0))), 100, endTime);
        lp.initiateLP();
        lp.solveLP(false);
        assertEquals(lp.getAlphaAbsolute_ij(), Tensors.of(Tensors.of(Tensors.vector(0, 0), Tensors.vector(0, 0))));
        assertEquals(lp.getAlphaRate_ij(), Tensors.of(Tensors.of(Tensors.vector(0, 0), Tensors.vector(0, 0))));

        // test simple case with one timeStep
        lp = new LPTimeVariant(virtualNetwork2, network, lpOptions, Tensors.of(Tensors.of(Tensors.vector(0, 1), Tensors.vector(0, 0))), 100, endTime);
        lp.initiateLP();
        lp.solveLP(false);
        assertEquals(lp.getAlphaAbsolute_ij(), Tensors.of(Tensors.of(Tensors.vector(0, 0), Tensors.vector(1, 0))));
        assertEquals(lp.getAlphaRate_ij(), Tensors.of(Tensors.of(Tensors.vector(0, 0), Tensors.vector(1, 0))).divide(RealScalar.of(30 * 3600)));

        // test simple case with one timeStep
        lp = new LPTimeVariant(virtualNetwork2, network, lpOptions, Tensors.of(Tensors.of(Tensors.vector(0, 0), Tensors.vector(1, 0))), 100, endTime);
        lp.initiateLP();
        lp.solveLP(false);
        assertEquals(lp.getAlphaAbsolute_ij(), Tensors.of(Tensors.of(Tensors.vector(0, 1), Tensors.vector(0, 0))));
        assertEquals(lp.getAlphaRate_ij(), Tensors.of(Tensors.of(Tensors.vector(0, 1), Tensors.vector(0, 0))).divide(RealScalar.of(30 * 3600)));

        // test simple case with one timeStep
        lp = new LPTimeVariant(virtualNetwork2, network, lpOptions, Tensors.of(Tensors.of(Tensors.vector(0, 1), Tensors.vector(1, 0))), 100, endTime);
        lp.initiateLP();
        lp.solveLP(false);
        assertEquals(lp.getAlphaAbsolute_ij(), Tensors.of(Tensors.of(Tensors.vector(0, 0), Tensors.vector(0, 0))));
        assertEquals(lp.getAlphaRate_ij(), Tensors.of(Tensors.of(Tensors.vector(0, 0), Tensors.vector(0, 0))));

        // test simple case with one timeStep
        lp = new LPTimeVariant(virtualNetwork2, network, lpOptions, Tensors.of(Tensors.of(Tensors.vector(1, 1), Tensors.vector(1, 2))), 100, endTime);
        lp.initiateLP();
        lp.solveLP(false);
        assertEquals(lp.getAlphaAbsolute_ij(), Tensors.of(Tensors.of(Tensors.vector(0, 0), Tensors.vector(0, 0))));
        assertEquals(lp.getAlphaRate_ij(), Tensors.of(Tensors.of(Tensors.vector(0, 0), Tensors.vector(0, 0))));

        // test case with one timeStep
        lp = new LPTimeVariant(virtualNetwork2, network, lpOptions, Tensors.of(Tensors.of(Tensors.vector(0, 2), Tensors.vector(1, 0))), 100, endTime);
        lp.initiateLP();
        lp.solveLP(false);
        assertEquals(lp.getAlphaAbsolute_ij(), Tensors.of(Tensors.of(Tensors.vector(0, 0), Tensors.vector(1, 0))));
        assertEquals(lp.getAlphaRate_ij(), Tensors.of(Tensors.of(Tensors.vector(0, 0), Tensors.vector(1, 0)).divide(RealScalar.of(30 * 3600))));
        assertEquals(lp.getFAbsolute_ij(), Tensors.of(Tensors.of(Tensors.vector(0, 2), Tensors.vector(1, 0))));
        assertEquals(lp.getFRate_ij(), Tensors.of(Tensors.of(Tensors.vector(0, 2), Tensors.vector(1, 0)).divide(RealScalar.of(30 * 3600))));

        // test case with two timeSteps
        lp = new LPTimeVariant(virtualNetwork2, network, lpOptions,
                Tensors.of(Tensors.of(Tensors.vector(0, 2), Tensors.vector(1, 0)), Tensors.of(Tensors.vector(0, 1), Tensors.vector(2, 0))), 100, endTime);
        lp.initiateLP();
        lp.solveLP(false);
        assertEquals(lp.getAlphaAbsolute_ij(), Tensors.of(Tensors.of(Tensors.vector(0, 0), Tensors.vector(0, 0)), Tensors.of(Tensors.vector(0, 0), Tensors.vector(0, 0))));
        assertEquals(lp.getAlphaRate_ij(),
                Tensors.of(Tensors.of(Tensors.vector(0, 0), Tensors.vector(0, 0)), Tensors.of(Tensors.vector(0, 0), Tensors.vector(0, 0))).divide(RealScalar.of(12 * 3600)));
        assertEquals(lp.getTimeIntervalLength(), 15 * 3600);
    }

    @Test
    public void testLP3Nodes() {
        // init LP time-invariant
        LPTimeVariant lp = new LPTimeVariant(virtualNetwork3, network, lpOptions, Tensors.of(Tensors.of(Tensors.vector(0, 0, 0), Tensors.vector(0, 0, 0), Tensors.vector(0, 0, 0))),
                100, endTime);
        assertNull(lp.getAlphaRate_ij());
        assertNull(lp.getAlphaAbsolute_ij());
        assertNull(lp.getFRate_ij());
        assertNull(lp.getFAbsolute_ij());
        assertNull(lp.getV0_i()); // the 10 vehicles from av.xml are distributed equally
        assertEquals(lp.getTimeIntervalLength(), 30 * 3600); // there is only one time interval [0, endTime]

        // test trivial case
        lp.initiateLP();
        lp.solveLP(false);
        assertEquals(lp.getAlphaAbsolute_ij(), Tensors.of(Tensors.of(Tensors.vector(0, 0, 0), Tensors.vector(0, 0, 0), Tensors.vector(0, 0, 0))));
        assertEquals(lp.getAlphaRate_ij(), Tensors.of(Tensors.of(Tensors.vector(0, 0, 0), Tensors.vector(0, 0, 0), Tensors.vector(0, 0, 0))));

        // test infeasible cases
        try {
            lp = new LPTimeVariant(virtualNetwork3, network, lpOptions, Tensors.of(Tensors.of(Tensors.vector(0, -1, 0), Tensors.vector(0, 0, 0), Tensors.vector(0, 0, 0))), 100,
                    endTime);
            fail();
        } catch (Exception exception) {
            // ---
        }

        try {
            lp = new LPTimeVariant(virtualNetwork3, network, lpOptions, Tensors.of(Tensors.of(Tensors.vector(0, 0.01, 0), Tensors.vector(0, 0, 0), Tensors.vector(0, 0, 0))), 100,
                    endTime);
            fail();
        } catch (Exception exception) {
            // ---
        }

        // test simple rounding case
        lp = new LPTimeVariant(virtualNetwork3, network, lpOptions, Tensors.of(Tensors.of(Tensors.vector(0, 0.00001, 0), Tensors.vector(0, 0, 0), Tensors.vector(0, 0, 0))), 100,
                endTime);
        lp.initiateLP();
        lp.solveLP(false);
        assertEquals(lp.getAlphaAbsolute_ij(), Tensors.of(Tensors.of(Tensors.vector(0, 0, 0), Tensors.vector(0, 0, 0), Tensors.vector(0, 0, 0))));
        assertEquals(lp.getAlphaRate_ij(), Tensors.of(Tensors.of(Tensors.vector(0, 0, 0), Tensors.vector(0, 0, 0), Tensors.vector(0, 0, 0))));

        // test simple case with one timeStep
        lp = new LPTimeVariant(virtualNetwork3, network, lpOptions, Tensors.of(Tensors.of(Tensors.vector(0, 1, 0), Tensors.vector(0, 0, 0), Tensors.vector(0, 0, 0))), 100,
                endTime);
        lp.initiateLP();
        lp.solveLP(false);
        assertEquals(lp.getAlphaAbsolute_ij(), Tensors.of(Tensors.of(Tensors.vector(0, 0, 0), Tensors.vector(1, 0, 0), Tensors.vector(0, 0, 0))));

        // test simple case with one timeStep
        lp = new LPTimeVariant(virtualNetwork3, network, lpOptions, Tensors.of(Tensors.of(Tensors.vector(0, 0, 1), Tensors.vector(0, 0, 0), Tensors.vector(0, 0, 0))), 100,
                endTime);
        lp.initiateLP();
        lp.solveLP(false);
        assertEquals(lp.getAlphaAbsolute_ij(), Tensors.of(Tensors.of(Tensors.vector(0, 0, 0), Tensors.vector(0, 0, 0), Tensors.vector(1, 0, 0))));

        // test simple case with one timeStep
        lp = new LPTimeVariant(virtualNetwork3, network, lpOptions, Tensors.of(Tensors.of(Tensors.vector(0, 1, 1), Tensors.vector(0, 0, 0), Tensors.vector(0, 0, 0))), 100,
                endTime);
        lp.initiateLP();
        lp.solveLP(false);
        assertEquals(lp.getAlphaAbsolute_ij(), Tensors.of(Tensors.of(Tensors.vector(0, 0, 0), Tensors.vector(1, 0, 0), Tensors.vector(1, 0, 0))));

        // test simple case with one timeStep
        lp = new LPTimeVariant(virtualNetwork3, network, lpOptions, Tensors.of(Tensors.of(Tensors.vector(1, 1, 1), Tensors.vector(0, 0, 0), Tensors.vector(0, 0, 0))), 100,
                endTime);
        lp.initiateLP();
        lp.solveLP(false);
        assertEquals(lp.getAlphaAbsolute_ij(), Tensors.of(Tensors.of(Tensors.vector(0, 0, 0), Tensors.vector(1, 0, 0), Tensors.vector(1, 0, 0))));

        // test simple case with one timeStep
        lp = new LPTimeVariant(virtualNetwork3, network, lpOptions, Tensors.of(Tensors.of(Tensors.vector(0, 1, 1), Tensors.vector(1, 0, 1), Tensors.vector(1, 1, 0))), 100,
                endTime);
        lp.initiateLP();
        lp.solveLP(false);
        assertEquals(lp.getAlphaAbsolute_ij(), Tensors.of(Tensors.of(Tensors.vector(0, 0, 0), Tensors.vector(0, 0, 0), Tensors.vector(0, 0, 0))));

        // test simple case with one timeStep
        lp = new LPTimeVariant(virtualNetwork3, network, lpOptions, Tensors.of(Tensors.of(Tensors.vector(0, 1, 0), Tensors.vector(0, 0, 1), Tensors.vector(1, 0, 0))), 100,
                endTime);
        lp.initiateLP();
        lp.solveLP(false);
        assertEquals(lp.getAlphaAbsolute_ij(), Tensors.of(Tensors.of(Tensors.vector(0, 0, 0), Tensors.vector(0, 0, 0), Tensors.vector(0, 0, 0))));

        // test simple case with one timeStep
        lp = new LPTimeVariant(virtualNetwork3, network, lpOptions, Tensors.of(Tensors.of(Tensors.vector(0, 2, 1), Tensors.vector(1, 0, 1), Tensors.vector(1, 1, 0))), 100,
                endTime);
        lp.initiateLP();
        lp.solveLP(false);
        assertEquals(lp.getAlphaAbsolute_ij(), Tensors.of(Tensors.of(Tensors.vector(0, 0, 0), Tensors.vector(1, 0, 0), Tensors.vector(0, 0, 0))));

        // test simple case with one timeStep
        lp = new LPTimeVariant(virtualNetwork3, network, lpOptions, Tensors.of(Tensors.of(Tensors.vector(0, 2, 2), Tensors.vector(1, 0, 1), Tensors.vector(1, 1, 0))), 100,
                endTime);
        lp.initiateLP();
        lp.solveLP(false);
        assertEquals(lp.getAlphaAbsolute_ij(), Tensors.of(Tensors.of(Tensors.vector(0, 0, 0), Tensors.vector(1, 0, 0), Tensors.vector(1, 0, 0))));
    }

    @AfterClass
    public static void tearDownOnce() throws IOException {
        TestFileHandling.removeGeneratedFiles();
    }
}
