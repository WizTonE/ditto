/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.gateway.proxy.actors;

import org.eclipse.ditto.services.gateway.endpoints.config.HttpConfig;
import org.eclipse.ditto.signals.commands.base.Command;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.Creator;

/**
 * Actor which delegates {@link Command}s to the appropriate receivers in the cluster.
 */
public final class ProxyActor extends AbstractThingProxyActor {

    private ProxyActor(final ActorRef pubSubMediator,
            final ActorRef devOpsCommandsActor,
            final ActorRef conciergeForwarder,
            final HttpConfig httpConfig) {

        super(pubSubMediator, devOpsCommandsActor, conciergeForwarder, httpConfig);
    }

    /**
     * Creates Akka configuration object Props for this ProxyActor.
     *
     * @param pubSubMediator the Pub/Sub mediator to use for subscribing for events.
     * @param devOpsCommandsActor the Actor ref to the local DevOpsCommandsActor.
     * @param httpConfig the configuration settings of the Gateway service's HTTP endpoint.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator,
            final ActorRef devOpsCommandsActor,
            final ActorRef conciergeForwarder,
            final HttpConfig httpConfig) {

        return Props.create(ProxyActor.class, new Creator<ProxyActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public ProxyActor create() {
                return new ProxyActor(pubSubMediator, devOpsCommandsActor, conciergeForwarder, httpConfig);
            }
        });
    }

}
