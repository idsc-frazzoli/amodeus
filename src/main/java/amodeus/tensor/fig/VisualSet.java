/* amodeus - Copyright (c) 2019, ETH Zurich, Institute for Dynamic Systems and Control */
package amodeus.tensor.fig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.jfree.chart.ChartFactory;

import amodeus.amodeus.analysis.plot.ChartTheme;
import ch.ethz.idsc.tensor.Tensor;
import ch.ethz.idsc.tensor.Tensors;
import ch.ethz.idsc.tensor.alg.MatrixQ;
import ch.ethz.idsc.tensor.alg.Transpose;
import ch.ethz.idsc.tensor.img.ColorDataIndexed;
import ch.ethz.idsc.tensor.img.ColorDataLists;

public class VisualSet {
    static {
        ChartFactory.setChartTheme(ChartTheme.STANDARD);
        // BarRenderer.setDefaultBarPainter(new StandardBarPainter());
        // BarRenderer.setDefaultShadowsVisible(false);
    }
    // ---
    private final List<VisualRow> visualRows = new ArrayList<>();
    private final ColorDataIndexed colorDataIndexed;
    private String plotLabel = "";
    private String axesLabelX = "";
    private String axesLabelY = "";

    public VisualSet(ColorDataIndexed colorDataIndexed) {
        this.colorDataIndexed = Objects.requireNonNull(colorDataIndexed);
    }

    /** uses Mathematica default color scheme */
    public VisualSet() {
        this(ColorDataLists._097.cyclic());
    }

    /** @param points of the form {{x1, y1}, {x2, y2}, ..., {xn, yn}}
     * @return */
    public VisualRow add(Tensor points) {
        final int index = visualRows.size();
        VisualRow visualRow = new VisualRow(MatrixQ.require(points), index);
        visualRow.setColor(colorDataIndexed.getColor(index));
        visualRows.add(visualRow);
        return visualRow;
    }

    /** @param domain {x1, x2, ..., xn}
     * @param values {y1, y2, ..., yn}
     * @return */
    public VisualRow add(Tensor domain, Tensor values) {
        return add(Transpose.of(Tensors.of(domain, values)));
    }

    public List<VisualRow> visualRows() {
        return Collections.unmodifiableList(visualRows);
    }

    public VisualRow getVisualRow(int index) {
        return visualRows.get(index);
    }

    public String getPlotLabel() {
        return plotLabel;
    }

    public String getAxesLabelX() {
        return axesLabelX;
    }

    /** @return name of codomain/target set */
    public String getAxesLabelY() {
        return axesLabelY;
    }

    public boolean hasLegend() {
        return visualRows.stream() //
                .map(VisualRow::getLabelString) //
                .anyMatch(string -> !string.isEmpty());
    }

    public void setPlotLabel(String string) {
        plotLabel = string;
    }

    public void setAxesLabelX(String string) {
        axesLabelX = string;
    }

    public void setAxesLabelY(String string) {
        axesLabelY = string;
    }
    // TODO @datahaki resolve or instruct someone to resolve
    // is there a way to make better use of similarity?
}
