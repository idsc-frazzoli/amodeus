/* amodeus - Copyright (c) 2018, ETH Zurich, Institute for Dynamic Systems and Control */
package ch.ethz.idsc.amodeus.matsim;

import org.matsim.amodeus.framework.AmodeusUtils;
import org.matsim.core.controler.AbstractModule;

import ch.ethz.idsc.amodeus.routing.DefaultAStarLMRouter;

public class RouterModule extends AbstractModule {
    @Override
    public void install() {

        /** here addtional standard routers can be included, e.g., as in commented, not functional
         * sample code below:
         * 
         * 
         * bind(MyOwnRouter.Factory.class);
         * AVUtils.bindRouterFactory(binder(), MyOwnRouter.class.getSimpleName()).to(MyOwnRouter.Factory.class); */
        bind(DefaultAStarLMRouter.Factory.class);
        AmodeusUtils.bindRouterFactory(binder(), DefaultAStarLMRouter.class.getSimpleName()).to(DefaultAStarLMRouter.Factory.class);
    }
}