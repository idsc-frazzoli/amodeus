/* amodeus - Copyright (c) 2018, ETH Zurich, Institute for Dynamic Systems and Control */
package ch.ethz.idsc.amodeus.gfx;

import javax.swing.JToggleButton;

import java.util.Objects;

import ch.ethz.idsc.amodeus.net.SimulationObject;
import ch.ethz.idsc.amodeus.net.SimulationServer;
import ch.ethz.idsc.amodeus.util.net.ObjectClient;
import ch.ethz.idsc.amodeus.util.net.ObjectHandler;

/* package */ class AmodeusToggleButton extends JToggleButton {

    private ObjectClient client = null;

    public AmodeusToggleButton(AmodeusComponent amodeusComponent) {
        super("connect...");

        addActionListener(event -> {
            if (Objects.nonNull(client)) {
                client.close();
                client = null;
            }
            if (isSelected()) {
                try {
                    client = new ObjectClient("localhost", SimulationServer.OBJECT_SERVER_PORT, new ObjectHandler() {
                        @Override
                        public void handle(Object object) {
                            amodeusComponent.setSimulationObject((SimulationObject) object);
                        }
                    });
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
            }
        });
    }
}
