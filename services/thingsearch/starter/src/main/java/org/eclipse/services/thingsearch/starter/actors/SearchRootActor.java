/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.services.thingsearch.starter.actors;

import static akka.http.javadsl.server.Directives.logRequest;
import static akka.http.javadsl.server.Directives.logResult;

import java.net.ConnectException;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.services.utils.cluster.ClusterStatusSupplier;
import org.eclipse.ditto.services.utils.config.ConfigUtil;
import org.eclipse.ditto.services.utils.health.HealthCheckingActor;
import org.eclipse.ditto.services.utils.health.HealthCheckingActorOptions;
import org.eclipse.ditto.services.utils.health.routes.StatusRoute;
import org.eclipse.services.thingsearch.common.util.ConfigKeys;
import org.eclipse.services.thingsearch.persistence.MongoClientWrapper;
import org.eclipse.services.thingsearch.persistence.read.MongoThingsSearchPersistence;
import org.eclipse.services.thingsearch.persistence.read.ThingsSearchPersistence;
import org.eclipse.services.thingsearch.persistence.read.query.MongoAggregationBuilderFactory;
import org.eclipse.services.thingsearch.persistence.read.query.MongoQueryBuilderFactory;
import org.eclipse.services.thingsearch.query.actors.AggregationQueryActor;
import org.eclipse.services.thingsearch.query.actors.QueryActor;
import org.eclipse.services.thingsearch.querymodel.criteria.CriteriaFactory;
import org.eclipse.services.thingsearch.querymodel.criteria.CriteriaFactoryImpl;
import org.eclipse.services.thingsearch.querymodel.expression.ThingsFieldExpressionFactory;
import org.eclipse.services.thingsearch.querymodel.expression.ThingsFieldExpressionFactoryImpl;
import org.eclipse.services.thingsearch.querymodel.query.AggregationBuilderFactory;
import org.eclipse.services.thingsearch.querymodel.query.QueryBuilderFactory;

import com.typesafe.config.Config;

import akka.actor.AbstractActor;
import akka.actor.ActorKilledException;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.InvalidActorNameException;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.Status;
import akka.actor.SupervisorStrategy;
import akka.cluster.Cluster;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.server.Route;
import akka.japi.Creator;
import akka.japi.pf.DeciderBuilder;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.AskTimeoutException;
import akka.stream.ActorMaterializer;

/**
 * Our "Parent" Actor which takes care of supervision of all other Actors in our system.
 */
public final class SearchRootActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "thingsSearchRoot";

    private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    private final SupervisorStrategy strategy = new OneForOneStrategy(true, DeciderBuilder //
            .match(NullPointerException.class, e -> {
                log.error(e, "NullPointer in child actor: {}", e.getMessage());
                log.info("Restarting child...");
                return SupervisorStrategy.restart();
            }).match(IllegalArgumentException.class, e -> {
                log.warning("Illegal Argument in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(IllegalStateException.class, e -> {
                log.warning("Illegal State in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(NoSuchElementException.class, e -> {
                log.warning("NoSuchElement in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(AskTimeoutException.class, e -> {
                log.warning("AskTimeoutException in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(ConnectException.class, e -> {
                log.warning("ConnectException in child actor: {}", e.getMessage());
                log.info("Restarting child...");
                return SupervisorStrategy.restart();
            }).match(InvalidActorNameException.class, e -> {
                log.warning("InvalidActorNameException in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(ActorKilledException.class, e -> {
                log.error(e, "ActorKilledException in child actor: {}", e.message());
                log.info("Restarting child...");
                return SupervisorStrategy.restart();
            }).match(DittoRuntimeException.class, e -> {
                log.error(e,
                        "DittoRuntimeException '{}' should not be escalated to SearchRootActor. Simply resuming Actor.",
                        e.getErrorCode());
                return SupervisorStrategy.resume();
            }).match(Throwable.class, e -> {
                log.error(e, "Escalating above root actor!");
                return SupervisorStrategy.escalate();
            }).matchAny(e -> {
                log.error("Unknown message:'{}'! Escalating above root actor!", e);
                return SupervisorStrategy.escalate();
            }).build());

    private SearchRootActor(final Config config, final ActorRef pubSubMediator, final ActorMaterializer materializer) {
        final boolean healthCheckEnabled = config.getBoolean(ConfigKeys.HEALTH_CHECK_ENABLED);
        final Duration healthCheckInterval = config.getDuration(ConfigKeys.HEALTH_CHECK_INTERVAL);

        final HealthCheckingActorOptions.Builder hcBuilder =
                HealthCheckingActorOptions.getBuilder(healthCheckEnabled, healthCheckInterval);
        if (config.getBoolean(ConfigKeys.HEALTH_CHECK_PERSISTENCE_ENABLED)) {
            hcBuilder.enablePersistenceCheck();
        }

        final MongoClientWrapper mongoClientWrapper = new MongoClientWrapper(config);

        final ActorRef mongoHealthCheckActor = startChildActor(MongoReactiveHealthCheckActor.ACTOR_NAME,
                MongoReactiveHealthCheckActor.props(mongoClientWrapper));

        final HealthCheckingActorOptions healthCheckingActorOptions = hcBuilder.build();
        final ActorRef healthCheckingActor = startChildActor(HealthCheckingActor.ACTOR_NAME,
                HealthCheckingActor.props(healthCheckingActorOptions, mongoHealthCheckActor));

        final ThingsSearchPersistence searchPersistence =
                new MongoThingsSearchPersistence(mongoClientWrapper, getContext().system());

        searchPersistence.initIndexes();

        final CriteriaFactory criteriaFactory = new CriteriaFactoryImpl();
        final ThingsFieldExpressionFactory fieldExpressionFactory = new ThingsFieldExpressionFactoryImpl();
        final AggregationBuilderFactory aggregationBuilderFactory = new MongoAggregationBuilderFactory();
        final QueryBuilderFactory queryBuilderFactory = new MongoQueryBuilderFactory();
        final ActorRef aggregationQueryActor = startChildActor(AggregationQueryActor.ACTOR_NAME,
                AggregationQueryActor.props(criteriaFactory, fieldExpressionFactory, aggregationBuilderFactory));
        final ActorRef apiV1QueryActor = startChildActor(QueryActor.ACTOR_NAME,
                QueryActor.props(criteriaFactory, fieldExpressionFactory, queryBuilderFactory));

        final ActorRef searchActor = startChildActor(SearchActor.ACTOR_NAME,
                SearchActor.props(pubSubMediator, aggregationQueryActor, apiV1QueryActor, searchPersistence));

        pubSubMediator.tell(new DistributedPubSubMediator.Put(searchActor), getSelf());

        String hostname = config.getString(ConfigKeys.HTTP_HOSTNAME);
        if (hostname.isEmpty()) {
            hostname = ConfigUtil.getLocalHostAddress();
            log.info("No explicit hostname configured, using HTTP hostname: {}", hostname);
        }

        final CompletionStage<ServerBinding> binding = Http.get(getContext().system()) //
                .bindAndHandle(
                        createRoute(getContext().system(), healthCheckingActor).flow(getContext().system(),
                                materializer),
                        ConnectHttp.toHost(hostname, config.getInt(ConfigKeys.HTTP_PORT)), materializer);
        binding.exceptionally(failure -> {
            log.error(failure, "Something very bad happened: {}", failure.getMessage());
            getContext().system().terminate();
            return null;
        });
    }

    /**
     * Creates Akka configuration object Props for this SearchRootActor.
     *
     * @param config the configuration settings of the Search Service.
     * @param pubSubMediator the PubSub mediator Actor.
     * @param materializer the materializer for the akka actor system.
     * @return the Akka configuration Props object.
     */
    public static Props props(final Config config, final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {
        return Props.create(SearchRootActor.class, new Creator<SearchRootActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public SearchRootActor create() throws Exception {
                return new SearchRootActor(config, pubSubMediator, materializer);
            }
        });
    }

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return strategy;
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(Status.Failure.class, f -> log.error(f.cause(), "Got failure: {}", f))
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                })
                .build();
    }

    private ActorRef startChildActor(final String actorName, final Props props) {
        log.info("Starting child actor '{}'", actorName);
        return getContext().actorOf(props, actorName);
    }

    private static Route createRoute(final ActorSystem actorSystem, final ActorRef healthCheckingActor) {
        final StatusRoute statusRoute = new StatusRoute(new ClusterStatusSupplier(Cluster.get(actorSystem)),
                healthCheckingActor, actorSystem);

        return logRequest("http-request", () -> logResult("http-response", statusRoute::buildStatusRoute));
    }
}
