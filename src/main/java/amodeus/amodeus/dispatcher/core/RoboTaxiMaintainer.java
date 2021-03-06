/* amodeus - Copyright (c) 2018, ETH Zurich, Institute for Dynamic Systems and Control */
package amodeus.amodeus.dispatcher.core;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.matsim.amodeus.components.AmodeusDispatcher;
import org.matsim.amodeus.config.AmodeusModeConfig;
import org.matsim.amodeus.dvrp.schedule.AmodeusStopTask;
import org.matsim.amodeus.plpc.ParallelLeastCostPathCalculator;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.drt.schedule.DrtDriveTask;
import org.matsim.contrib.drt.schedule.DrtStayTask;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.schedule.Schedule;
import org.matsim.contrib.dvrp.schedule.Schedule.ScheduleStatus;
import org.matsim.contrib.dvrp.schedule.Task;
import org.matsim.contrib.dvrp.tracker.OnlineDriveTaskTracker;
import org.matsim.contrib.dvrp.tracker.TaskTracker;
import org.matsim.contrib.dvrp.util.LinkTimePair;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;

import amodeus.amodeus.net.StorageUtils;
import amodeus.amodeus.util.matsim.SafeConfig;

/** The purpose of RoboTaxiMaintainer is to register {@link RoboTaxi} and provide the collection of
 * available vehicles to derived class.
 * <p>
 * manages assignments of {@link DirectiveInterface} to {@link RoboTaxi}s. path computations
 * attached to assignments are computed in parallel
 * {@link ParallelLeastCostPathCalculator}. */
/* package */ abstract class RoboTaxiMaintainer implements AmodeusDispatcher {
    protected final EventsManager eventsManager;
    private final List<RoboTaxi> roboTaxis = new ArrayList<>();
    private Double private_now = null;
    public InfoLine infoLine = null;
    private final StorageUtils storageUtils;
    protected final String mode;

    RoboTaxiMaintainer(EventsManager eventsManager, Config config, AmodeusModeConfig operatorConfig) {
        SafeConfig safeConfig = SafeConfig.wrap(operatorConfig.getDispatcherConfig());
        this.eventsManager = eventsManager;
        this.infoLine = new InfoLine(safeConfig.getInteger("infoLinePeriod", 10));
        String outputdirectory = config.controler().getOutputDirectory();
        this.storageUtils = new StorageUtils(new File(outputdirectory));
        this.mode = operatorConfig.getMode();
    }

    /** @return time of current re-dispatching iteration step
     * @throws NullPointerException
     *             if dispatching has not started yet */
    protected final double getTimeNow() {
        return private_now;
    }

    /** @return {@link List} of {@link RoboTaxi} */
    protected final List<RoboTaxi> getRoboTaxis() {
        if (roboTaxis.isEmpty())
            return Collections.emptyList();

        return roboTaxis.stream().filter(rt -> rt.getSchedule().getStatus().equals(ScheduleStatus.STARTED)).collect(Collectors.toList());
    }

    protected abstract void updateDivertableLocations();

    public final void addRoboTaxi(RoboTaxi roboTaxi, Event event) {
        roboTaxis.add(roboTaxi);
        eventsManager.processEvent(event);
    }

    @Override
    public abstract void addVehicle(DvrpVehicle vehicle);

    /** functions called at every MATSim timestep, dispatching action happens in <b> redispatch <b> */
    @Override
    public final void onNextTimestep(double now) {
        private_now = now; // <- time available to derived class via getTimeNow()
        updateInfoLine();
        notifySimulationSubscribers(Math.round(now), storageUtils);
        consistencyCheck();
        beforeStepTasks(); // <- if problems with RoboTaxi Status to Completed consider to set "simEndtimeInterpretation" to "null"
        // The Dropoff is before the pickup because:
        // a) A robotaxi which picks up a customer should not dropoff one at the same time step
        // b) but in the shared case the internal dropoff should be able to finish a dropoff which enables the pickups to be executed
        executeDropoffs();
        executePickups();
        executeRedirects();
        redispatch(now);
        redispatchInternal(now);
        afterStepTasks();
        consistencyCheck();
    }

    /** the info line is displayed in the console at every dispatching timestep and in the
     * AMoDeus viewer */
    protected final void updateInfoLine() {
        String infoLine = getInfoLine();
        this.infoLine.updateInfoLine(infoLine, getTimeNow());
    }

    /** derived classes should override this function to add details
     * 
     * @return String with infoLine content */
    protected String getInfoLine() {
        final String string = getClass().getSimpleName() + "        ";
        return String.format("%s@%6d V=(%4ds,%4dd)", //
                string.substring(0, 6), //
                (long) getTimeNow(), //
                roboTaxis.stream().filter(RoboTaxi::isInStayTask).count(), //
                roboTaxis.stream().map(RoboTaxi::getStatus).filter(RoboTaxiStatus::isDriving).count());
    }

    private void beforeStepTasks() {
        updateDivertableLocations();
        updateCurrentLocations();
    }

    /** {@link RoboTaxi} on a pickup ride which are sent to another location are
     * stopped, also taxis which have lost their pickup assignment */
    private void afterStepTasks() {
        stopAbortedPickupRoboTaxis();
        // flushLocationTraces();
    }

    private void consistencyCheck() {
        consistencySubCheck();
    }

    private void updateCurrentLocations() {
        @SuppressWarnings("unused")
        int failed = 0;
        for (RoboTaxi roboTaxi : roboTaxis) {
            Link link = roboTaxi.getDvrpVehicle().getStartLink();

            Schedule schedule = roboTaxi.getSchedule();

            if (schedule.getStatus().equals(ScheduleStatus.STARTED)) {
                Task currentTask = schedule.getCurrentTask();

                if (currentTask instanceof AmodeusStopTask) {
                    AmodeusStopTask stopTask = (AmodeusStopTask) currentTask;
                    link = stopTask.getLink();
                } else if (currentTask instanceof DrtDriveTask) {
                    DrtDriveTask driveTask = (DrtDriveTask) currentTask;

                    TaskTracker taskTracker = driveTask.getTaskTracker();
                    OnlineDriveTaskTracker onlineDriveTaskTracker = (OnlineDriveTaskTracker) taskTracker;
                    // there is a slim chance that function getDiversionPoint() returns null
                    LinkTimePair linkTimePair = onlineDriveTaskTracker.getDiversionPoint();
                    if (Objects.nonNull(linkTimePair))
                        link = linkTimePair.link;
                } else if (currentTask instanceof DrtStayTask) {
                    DrtStayTask stayTask = (DrtStayTask) currentTask;
                    link = stayTask.getLink();
                }
            }

            roboTaxi.setLastKnownLocation(link);
            updateLocationTrace(roboTaxi, link);
        }
    }

    /* package */ abstract void updateLocationTrace(RoboTaxi roboTaxi, Link lastKnownLoc);

    /* package */ abstract void executePickups();

    /* package */ abstract void executeDropoffs();

    /* package */ abstract void stopAbortedPickupRoboTaxis();

    /* package */ abstract void consistencySubCheck();

    /* package */ abstract void notifySimulationSubscribers(long round_now, StorageUtils storageUtils);

    /* package */ abstract void redispatchInternal(double now);

    /* package */ abstract void executeRedirects();

    @Override
    public final void onNextTaskStarted(DvrpVehicle task) {
        // intentionally empty
    }

    /** derived classes should override this function
     * 
     * @param now */
    protected abstract void redispatch(double now);

}