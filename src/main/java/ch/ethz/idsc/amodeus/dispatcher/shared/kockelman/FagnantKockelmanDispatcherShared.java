/* amodeus - Copyright (c) 2018, ETH Zurich, Institute for Dynamic Systems and Control */
package ch.ethz.idsc.amodeus.dispatcher.shared.kockelman;

import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.router.FastAStarLandmarksFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelTime;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import ch.ethz.idsc.amodeus.analysis.ScenarioParameters;
import ch.ethz.idsc.amodeus.dispatcher.core.RoboTaxi;
import ch.ethz.idsc.amodeus.dispatcher.core.RoboTaxiUtils;
import ch.ethz.idsc.amodeus.dispatcher.core.SharedRebalancingDispatcher;
import ch.ethz.idsc.amodeus.dispatcher.shared.SharedCourse;
import ch.ethz.idsc.amodeus.dispatcher.util.AbstractRoboTaxiDestMatcher;
import ch.ethz.idsc.amodeus.dispatcher.util.AbstractVirtualNodeDest;
import ch.ethz.idsc.amodeus.dispatcher.util.EasyPathCalculator;
import ch.ethz.idsc.amodeus.dispatcher.util.EuclideanDistanceFunction;
import ch.ethz.idsc.amodeus.dispatcher.util.GlobalBipartiteMatching;
import ch.ethz.idsc.amodeus.dispatcher.util.RandomVirtualNodeDest;
import ch.ethz.idsc.amodeus.matsim.SafeConfig;
import ch.ethz.idsc.amodeus.net.MatsimAmodeusDatabase;
import ch.ethz.idsc.amodeus.util.math.GlobalAssert;
import ch.ethz.matsim.av.config.AVConfig;
import ch.ethz.matsim.av.config.AVDispatcherConfig;
import ch.ethz.matsim.av.config.AVGeneratorConfig;
import ch.ethz.matsim.av.dispatcher.AVDispatcher;
import ch.ethz.matsim.av.framework.AVModule;
import ch.ethz.matsim.av.passenger.AVRequest;
import ch.ethz.matsim.av.router.AVRouter;

/** Implementation of the ride sharing strategy proposed by:
 * Fagnant, D. J., & Kockelman, K. M. (2015). Dynamic ride-sharing and optimal fleet sizing for a system of shared autonomous vehicles (No. 15-1962).
 * 
 * The strategy goes through the Requests in the order of the submission time. For each request it is first checked if a valid ride sharing possibility is
 * present within a radius of 5 min (MAXWAITTIME). If that is not the cse it is checked if another vehicle is available within 5 minutes which is currently
 * without designates requests. If this is also not possible then the vehicle is put on a wait list which increases the search radius in the next dispatching
 * step.
 * Ride Sharing is considered valid if 5 constraints are fulfilled:
 * 1. Current passengers’ trip duration increases ≤ 20% (total trip duration with ride-sharing vs. without ride-sharing);
 * 2. Current passengers’ remaining trip time increases ≤ 40%;
 * 3. New traveler’s total trip time increase grows by ≤ Max(20% total trip without ride-sharing, or 3 minutes);
 * 4. New travelers will be picked up at least within the next 5 minutes;
 * 5. Total planned trip time to serve all passengers ≤ remaining time to serve the current trips + time to serve the new trip + drop-off time, if
 * not pooled. */
public class FagnantKockelmanDispatcherShared extends SharedRebalancingDispatcher {

    /** Dispatcher Settings Identifiers */
    private static final String MAXWAITTIMEIDENTIFIER = "maxWaitTime";
    private static final String MAXDRIVETIMEINCREASEIDENTIFIER = "maxDriveTimeIncrease";
    private static final String MAXREMAININGTIMEINCREASEIDENTIFIER = "maxRemainingTimeIncrease";
    private static final String MAXABSOLUTETRAVELTIMEINCREASEIDENTIFIER = "maxAbsolutDriveTimeIncrease";

    /** general Dispatcher Settings */
    private final int dispatchPeriod;

    /** ride sharing parameters */
    private static final double WAITLISTTIME = 300.0;// Normal: 300, Time after which a request is put on to the wait list
    private static final double MAXWAITTIME = 600.0; // Normal is 600
    private static final double EXTREEMWAITTIME = 3600.0 * 24; // The extrem wait list is used here as in AMoDeus requests can not be rejected. This list guarantees for requests
                                                               // waiting for more than MaxWaitTime that a taxi can be found
    /** rebalancing Parameters */
    private static final double BINSIZETRAVELDEMAND = 3600.0; // Assumption made for the Request records required for rebalancing
    private static final double REBALANCINGGRIDDISTANCE = 3218.69; // 2.0 miles in [m]
    private static final int MINNUMBERROBOTAXISINBLOCKTOREBALANCE = 5;

    /** Ride Sharing Constraints */
    private final double maxWaitTime;
    private final double maxDriveTimeIncrease;
    private final double maxRemainingTimeIncrease;
    private final double newTravelTimeIncreaseAllowed;

    private final double dropoffDuration;
    private final double pickupDuration;

    /** data structures for a fast search and simpler calulations */
    // unassigned Robo Taxis in the Scenario sorted by its coordinates in a Tree Structure
    // private final Set<RoboTaxi> unassignedRoboTaxis = new HashSet<>();
    private final RoboTaxiMaintainer roboTaxiMaintainer;
    // Maintains All the Information about the Requests. keeps track of Assignements, Pickups, ...
    private final RequestMaintainer requestMaintainer = new RequestMaintainer(MAXWAITTIME, WAITLISTTIME, EXTREEMWAITTIME);
    // Calulator for fastest travel times in the newtwork
    private final LeastCostPathCalculator calculator;
    // Rebalancing Executor
    private final GridRebalancing kockelmanRebalancing;
    private final RouteValidation kockelmanRouteValidation;

    private static final double MAXLAGTRAVELTIMECALCULATION = 180000.0;
    private final TravelTimeCalculatorCached timeDb;

    protected FagnantKockelmanDispatcherShared(Network network, //
            Config config, AVConfig avConfig, AVDispatcherConfig avDispatcherConfig, //
            TravelTime travelTime, AVRouter router, EventsManager eventsManager, //
            MatsimAmodeusDatabase db) {
        super(config, avDispatcherConfig, travelTime, router, eventsManager, db);
        SafeConfig safeConfig = SafeConfig.wrap(avDispatcherConfig);
        dispatchPeriod = safeConfig.getInteger(ScenarioParameters.DISPATCHPERIODSTRING, 300);
        maxWaitTime = safeConfig.getInteger(MAXWAITTIMEIDENTIFIER, 300);// Normal is 300
        maxDriveTimeIncrease = safeConfig.getDouble(MAXDRIVETIMEINCREASEIDENTIFIER, 1.2);// Normal is 1.2
        maxRemainingTimeIncrease = safeConfig.getDouble(MAXREMAININGTIMEINCREASEIDENTIFIER, 1.4);// Normal is 1.4
        newTravelTimeIncreaseAllowed = safeConfig.getInteger(MAXABSOLUTETRAVELTIMEINCREASEIDENTIFIER, 180); // Normal is 180= (3min);

        dropoffDuration = avConfig.getTimingParameters().getDropoffDurationPerStop();
        pickupDuration = avConfig.getTimingParameters().getDropoffDurationPerStop();

        roboTaxiMaintainer = new RoboTaxiMaintainer(network);

        FastAStarLandmarksFactory factory = new FastAStarLandmarksFactory();
        calculator = EasyPathCalculator.prepPathCalculator(network, factory);
        timeDb = TravelTimeCalculatorCached.of(calculator, MAXLAGTRAVELTIMECALCULATION);
        this.kockelmanRebalancing = new GridRebalancing(network, timeDb, REBALANCINGGRIDDISTANCE, MINNUMBERROBOTAXISINBLOCKTOREBALANCE, BINSIZETRAVELDEMAND, dispatchPeriod);
        kockelmanRouteValidation = new RouteValidation(maxWaitTime, maxDriveTimeIncrease, maxRemainingTimeIncrease, dropoffDuration, pickupDuration, newTravelTimeIncreaseAllowed);
    }

    @Override
    protected void redispatch(double now) {
        final long round_now = Math.round(now);
        requestMaintainer.updatePickupTimes(getAVRequests(), now);

        if (round_now % dispatchPeriod == 0) {
            timeDb.update(now);

            /** prepare the registers for the dispatching */
            roboTaxiMaintainer.update(getRoboTaxis(), getDivertableUnassignedRoboTaxis());
            requestMaintainer.addUnassignedRequests(getUnassignedAVRequests(), timeDb);
            requestMaintainer.updateLastHourRequests(now, BINSIZETRAVELDEMAND);

            /** calculate Rebalance before (!) dispatching */
            Set<Link> lastHourRequests = requestMaintainer.getRequestLinksLastHour();
            RebalancingDirectives rebalanceDirectives = kockelmanRebalancing.getRebalancingDirectives(round_now, //
                    roboTaxiMaintainer.getUnassignedRoboTaxis(), //
                    requestMaintainer.getCopyOfUnassignedAVRequests(), lastHourRequests);

            /** for all AV Requests in the order of their submision, try to find the closest
             * vehicle and assign */
            for (AVRequest avRequest : requestMaintainer.getInOrderOffSubmissionTime()) {
                Set<RoboTaxi> robotaxisWithMenu = getRoboTaxis().stream().filter(rt -> RoboTaxiUtils.plansPickupsOrDropoffs(rt)).collect(Collectors.toSet());

                /** THIS IS WHERE WE CALCULATE THE SHARING POSSIBILITIES */
                Optional<Entry<RoboTaxi, List<SharedCourse>>> rideSharingRoboTaxi = kockelmanRouteValidation.getClosestValidSharingRoboTaxi(robotaxisWithMenu, avRequest, now,
                        timeDb, requestMaintainer, roboTaxiMaintainer, maxWaitTime);

                if (rideSharingRoboTaxi.isPresent()) {
                    /** in Case we have a sharing possibility we assign */
                    RoboTaxi roboTaxi = rideSharingRoboTaxi.get().getKey();
                    GlobalAssert.that(kockelmanRouteValidation.menuFulfillsConstraints(roboTaxi, rideSharingRoboTaxi.get().getValue(), avRequest, now, timeDb, requestMaintainer));
                    addSharedRoboTaxiPickup(roboTaxi, avRequest);
                    requestMaintainer.removeFromUnasignedRequests(avRequest);
                    rebalanceDirectives.removefromDirectives(roboTaxi);
                    roboTaxi.updateMenu(rideSharingRoboTaxi.get().getValue());

                } else {
                    /** in Case No sharing possibility is present, try to find a close enough vehicle */
                    Optional<RoboTaxi> emptyRoboTaxi = RoboTaxiUtilsFagnant.getClosestUnassignedRoboTaxiWithinMaxTime(roboTaxiMaintainer, avRequest,
                            requestMaintainer.calculateWaitTime(avRequest), now, timeDb);
                    if (emptyRoboTaxi.isPresent()) {
                        /** In case we have a close vehicle which is free lets assign it */
                        addSharedRoboTaxiPickup(emptyRoboTaxi.get(), avRequest); // give directive
                        roboTaxiMaintainer.assign(emptyRoboTaxi.get()); // the assigned Robotaxi is not unasigned anymore
                        rebalanceDirectives.removefromDirectives(emptyRoboTaxi.get()); // this taxi can not be rebalanced anymore
                        requestMaintainer.removeFromUnasignedRequests(avRequest); // the request is not unassigned anymore
                    } else {
                        /** Assignement was not possible as no Taxi was able to fulfil the constraints -> wait list! */
                        if (!requestMaintainer.isOnWaitList(avRequest)) {
                            requestMaintainer.addToWaitList(avRequest);
                        } else { // and if it was already on the wait list put it to the extrem wait list
                            requestMaintainer.addToExtreemWaitList(avRequest);
                        }
                    }
                }

            }

            /** execute New Rebalance Directives */
            for (Entry<RoboTaxi, Link> entry : rebalanceDirectives.getDirectives().entrySet()) {
                roboTaxiMaintainer.assign(entry.getKey());
                setRoboTaxiRebalance(entry.getKey(), entry.getValue());
            }

            /** For all robotaxis which were on rebalance and did not receive a new directive
             * stop on current link */
            getRebalancingRoboTaxis().stream().//
                    filter(rt -> !rebalanceDirectives.getDirectives().containsKey(rt)).//
                    forEach(rt -> {
                        setRoboTaxiRebalance(rt, rt.getDivertableLocation());
                    });
            roboTaxiMaintainer.clear();
        }
    }

    public static class Factory implements AVDispatcherFactory {
        @Inject
        @Named(AVModule.AV_MODE)
        private TravelTime travelTime;

        @Inject
        private EventsManager eventsManager;

        @Inject
        @Named(AVModule.AV_MODE)
        private Network network;

        @Inject
        private Config config;

        @Inject
        private AVConfig avConfig;

        @Inject
        private MatsimAmodeusDatabase db;

        @Override

        public AVDispatcher createDispatcher(AVDispatcherConfig avDispatcherConfig, AVRouter router) {
            @SuppressWarnings("unused")
            AVGeneratorConfig generatorConfig = avDispatcherConfig.getParent().getGeneratorConfig();
            @SuppressWarnings("unused")
            AbstractVirtualNodeDest abstractVirtualNodeDest = new RandomVirtualNodeDest();
            @SuppressWarnings("unused")
            AbstractRoboTaxiDestMatcher abstractVehicleDestMatcher = new GlobalBipartiteMatching(EuclideanDistanceFunction.INSTANCE);

            return new FagnantKockelmanDispatcherShared(network, config, avConfig, avDispatcherConfig, travelTime, router, eventsManager, db);
        }
    }
}