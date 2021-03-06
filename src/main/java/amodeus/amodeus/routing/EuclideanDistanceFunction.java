/* amodeus - Copyright (c) 2018, ETH Zurich, Institute for Dynamic Systems and Control */
package amodeus.amodeus.routing;

import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.dvrp.passenger.PassengerRequest;
import org.matsim.core.utils.geometry.CoordUtils;

import amodeus.amodeus.dispatcher.core.RoboTaxi;

public enum EuclideanDistanceFunction implements DistanceFunction {
    INSTANCE;

    @Override
    public double getDistance(RoboTaxi roboTaxi, PassengerRequest avrequest) {
        return CoordUtils.calcEuclideanDistance(roboTaxi.getDivertableLocation().getCoord(), avrequest.getFromLink().getCoord());
    }

    @Override
    public double getDistance(RoboTaxi roboTaxi, Link link) {
        return CoordUtils.calcEuclideanDistance(roboTaxi.getDivertableLocation().getCoord(), link.getCoord());
    }

    @Override
    public double getDistance(Link from, Link to) {
        return CoordUtils.calcEuclideanDistance(from.getCoord(), to.getCoord());
    }

}
