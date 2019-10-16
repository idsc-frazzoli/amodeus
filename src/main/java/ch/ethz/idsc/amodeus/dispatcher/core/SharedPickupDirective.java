/* amodeus - Copyright (c) 2018, ETH Zurich, Institute for Dynamic Systems and Control */
package ch.ethz.idsc.amodeus.dispatcher.core;

import org.matsim.contrib.dvrp.schedule.Schedule;
import org.matsim.contrib.dvrp.schedule.Schedules;

import ch.ethz.idsc.amodeus.util.math.GlobalAssert;
import ch.ethz.matsim.av.passenger.AVRequest;
import ch.ethz.matsim.av.schedule.AVPickupTask;
import ch.ethz.matsim.av.schedule.AVStayTask;

/** for vehicles that are in dropoff or pickup task and new request is assigned.
 * 1) finish pickup or dropoff task 2) append drive task 3) append new stay task */
/* package */ final class SharedPickupDirective extends SharedFixedLocationDirective {

    public SharedPickupDirective(RoboTaxi robotaxi, AVRequest avRequest, double getTimeNow, double durationOfTask) {
        super(robotaxi, avRequest, getTimeNow, durationOfTask);
    }

    @Override
    public void execute() {
        final Schedule schedule = robotaxi.getSchedule();
        final AVStayTask avStayTask = (AVStayTask) Schedules.getLastTask(schedule);
        final double scheduleEndTime = avStayTask.getEndTime();
        final double endTaskTime = getTimeNow + durationOfTask;
        GlobalAssert.that(scheduleEndTime == schedule.getEndTime());

        if (endTaskTime < scheduleEndTime) {
            avStayTask.setEndTime(getTimeNow); // finish the last task now

            AVPickupTask pickupTask = new AVPickupTask( //
                    getTimeNow, // start of pickup
                    endTaskTime, // end of pickup
                    avRequest.getFromLink(), // location of driving start
                    0.0);
            pickupTask.addRequest(avRequest); // serving only one request at a time
            schedule.addTask(pickupTask);

            ScheduleUtils.makeWhole(robotaxi, endTaskTime, scheduleEndTime, avRequest.getFromLink());

            // jan: following computation is mandatory for the internal scoring
            // function
            // final double distance = VrpPathUtils.getDistance(vrpPathWithTravelData);
            // nextRequest.getRoute().setDistance(distance);
        }
    }

}