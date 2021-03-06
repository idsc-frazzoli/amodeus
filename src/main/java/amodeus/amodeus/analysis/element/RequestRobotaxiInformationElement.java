/* amodeus - Copyright (c) 2018, ETH Zurich, Institute for Dynamic Systems and Control */
package amodeus.amodeus.analysis.element;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import amodeus.amodeus.analysis.report.TotalValueAppender;
import amodeus.amodeus.analysis.report.TotalValueIdentifier;
import amodeus.amodeus.analysis.report.TtlValIdent;
import amodeus.amodeus.net.SimulationObject;

public class RequestRobotaxiInformationElement implements AnalysisElement, TotalValueAppender {
    private final Set<Integer> requestIndices = new HashSet<>();
    private final Set<Integer> vehicleIndices = new HashSet<>();

    @Override
    public void register(SimulationObject simulationObject) {
        simulationObject.requests.forEach(r -> requestIndices.add(r.requestIndex));
        simulationObject.vehicles.forEach(v -> vehicleIndices.add(v.vehicleIndex));
    }

    public int vehicleSize() {
        return vehicleIndices.size();
    }

    public int reqsize() {
        return requestIndices.size();
    }

    @Override
    public Map<TotalValueIdentifier, String> getTotalValues() {
        Map<TotalValueIdentifier, String> map = new HashMap<>();
        map.put(TtlValIdent.TOTALREQUESTS, String.valueOf(reqsize()));
        map.put(TtlValIdent.TOTALVEHICLES, String.valueOf(vehicleSize()));

        return map;
    }

}
