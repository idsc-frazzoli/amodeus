/* amodeus - Copyright (c) 2018, ETH Zurich, Institute for Dynamic Systems and Control */
package amodeus.amodeus.gfx;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.swing.JCheckBox;

import amodeus.amodeus.dispatcher.core.RoboTaxiStatus;
import amodeus.amodeus.net.OsmLink;
import amodeus.amodeus.net.SimulationObject;
import amodeus.amodeus.net.VehicleContainer;
import amodeus.amodeus.net.VehicleContainerUtils;
import amodeus.amodeus.util.gui.RowPanel;
import amodeus.amodeus.util.gui.SpinnerLabel;

public class VehiclesLayer extends ViewerLayer {

    private final BitSet bits = new BitSet();

    // during development standard colors are a better default
    public RoboTaxiStatusColors statusColors;
    public boolean showLocation;

    public VehiclesLayer(AmodeusComponent amodeusComponent) {
        super(amodeusComponent);
        loadBitSet(amodeusComponent.viewerConfig.settings);
    }

    @Override
    protected void paint(Graphics2D graphics, SimulationObject ref) {
        if (Objects.isNull(ref) || !showLocation)
            return;

        int zoom = amodeusComponent.getZoom();
        int carwidth = (int) Math.max(zoom <= 12 ? 2 : 3, Math.round(5 / amodeusComponent.getMeterPerPixel()));
        int car_half = carwidth / 2;
        Map<Integer, List<VehicleContainer>> map = //
                ref.vehicles.stream().collect(Collectors.groupingBy(this::indexFrom));
        for (Entry<Integer, List<VehicleContainer>> entry : map.entrySet()) {
            int size = entry.getValue().size();
            OsmLink osmLink = amodeusComponent.db.getOsmLink(entry.getKey());
            Point p1test = amodeusComponent.getMapPosition(osmLink.getAt(0.5));
            if (Objects.nonNull(p1test)) {
                double ofs = 0.5 / size;
                double delta = 2 * ofs;
                for (VehicleContainer vc : entry.getValue()) {
                    Point p1 = amodeusComponent.getMapPosition(osmLink.getAt(ofs));
                    if (Objects.nonNull(p1)) {
                        RoboTaxiStatus roboTaxiStatus = VehicleContainerUtils.finalStatus(vc);
                        if (showLocation) {
                            Color color = statusColors.of(roboTaxiStatus);
                            graphics.setColor(color);
                            graphics.fillRect(p1.x - car_half, p1.y - car_half, carwidth, carwidth);
                        }
                        if (bits.get(roboTaxiStatus.ordinal())) {
                            OsmLink toOsmLink = amodeusComponent.db.getOsmLink(vc.destinationLinkIndex);
                            Point p2 = amodeusComponent.getMapPositionAlways(toOsmLink.getAt(0.5));
                            Color col = statusColors.ofDest(roboTaxiStatus);
                            graphics.setColor(col);
                            graphics.drawLine(p1.x, p1.y, p2.x, p2.y);
                        }
                    }
                    ofs += delta;
                }
            }
        }
    }

    @Override
    protected void hud(Graphics2D graphics, SimulationObject ref) {
        int[] count = new int[RoboTaxiStatus.values().length];
        if (Objects.nonNull(ref)) {
            ref.vehicles.forEach(v -> ++count[VehicleContainerUtils.finalStatus(v).ordinal()]);

            for (RoboTaxiStatus avStatus : RoboTaxiStatus.values()) {
                InfoString infoString = new InfoString(String.format("%5d %s", count[avStatus.ordinal()], avStatus.description()));
                infoString.color = statusColors.of(avStatus);
                amodeusComponent.append(infoString);
            }
            InfoString infoString = new InfoString(String.format("%5d %s", ref.vehicles.size(), "total"));
            infoString.color = Color.BLACK;
            amodeusComponent.append(infoString);
            amodeusComponent.appendSeparator();
        }
    }

    @Override
    protected void createPanel(RowPanel rowPanel) {
        {
            final JCheckBox jCheckBox = new JCheckBox("location");
            jCheckBox.setToolTipText("vehicle are small rectangles");
            jCheckBox.setSelected(showLocation);
            jCheckBox.addActionListener(event -> {
                showLocation = jCheckBox.isSelected();
                amodeusComponent.repaint();
            });
            rowPanel.add(jCheckBox);
        }
        {
            SpinnerLabel<RoboTaxiStatusColors> spinner = new SpinnerLabel<>();
            spinner.setToolTipText("color scheme for vehicle rectangles");
            spinner.setArray(RoboTaxiStatusColors.values());
            spinner.setValue(statusColors);
            spinner.addSpinnerListener(cs -> {
                statusColors = cs;
                amodeusComponent.repaint();
            });
            spinner.getLabelComponent().setPreferredSize(new Dimension(100, DEFAULT_HEIGHT));
            rowPanel.add(spinner.getLabelComponent());
        }

        for (RoboTaxiStatus status : RoboTaxiStatus.values())
            if (status.isDriving()) {
                final JCheckBox jCheckBox = new JCheckBox(status.description());
                jCheckBox.setToolTipText("show vehicles in mode: " + status.description());
                jCheckBox.setSelected(bits.get(status.ordinal()));
                jCheckBox.addActionListener(e -> setDrawDestinations(status, jCheckBox.isSelected()));
                rowPanel.add(jCheckBox);
            }
    }

    public void setDrawDestinations(RoboTaxiStatus status, boolean selected) {
        bits.set(status.ordinal(), selected);
        amodeusComponent.repaint();
    }

    private void bitSet(RoboTaxiStatus roboTaxiStatus) {
        if (roboTaxiStatus.isDriving())
            bits.set(roboTaxiStatus.ordinal());
        else
            System.err.println("cannot visualize dest link");
    }

    @Override
    public void updateSettings(ViewerSettings settings) {
        settings.bits = bits;
        settings.statusColors = statusColors;
        settings.showLocation = showLocation;
    }

    @Override
    public void loadSettings(ViewerSettings settings) {
        try {
            loadBitSet(settings);
        } catch (NullPointerException e) {
            // ---
        }
        statusColors = settings.statusColors;
        showLocation = settings.showLocation;
    }

    private void loadBitSet(ViewerSettings settings) {
        if (Objects.isNull(settings.bits)) {
            bitSet(RoboTaxiStatus.DRIVETOCUSTOMER);
            bitSet(RoboTaxiStatus.REBALANCEDRIVE);
            settings.bits = bits;
        } else {
            bits.clear();
            settings.bits.stream().forEach(bits::set);
        }
    }

    private int indexFrom(VehicleContainer vc) {
        return vc.linkTrace[vc.linkTrace.length - 1];
    }
}
