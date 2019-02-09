/* amodeus - Copyright (c) 2019, ETH Zurich, Institute for Dynamic Systems and Control */
package ch.ethz.idsc.subare.plot;

import org.jfree.chart.JFreeChart;

public enum CompositionStack {
    ;

    public static JFreeChart of(VisualSet visualSet) {
        return Histogram.of(visualSet, true);
    }

}