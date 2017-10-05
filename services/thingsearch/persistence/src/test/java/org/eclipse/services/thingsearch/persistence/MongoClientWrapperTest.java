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
package org.eclipse.services.thingsearch.persistence;

import java.util.Arrays;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.services.utils.config.MongoConfig;
import org.junit.Test;

import com.mongodb.ConnectionString;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.async.client.MongoClientSettings;
import com.mongodb.connection.ConnectionPoolSettings;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

/**
 * Tests {@link MongoClientWrapper}.
 */
public class MongoClientWrapperTest {

    private static final String KNOWN_SERVER_ADDRESS = "xy.example.org:34850";
    private static final String KNOWN_DB_NAME = "someGeneratedName";
    private static final String KNOWN_USER = "theUser";
    private static final String KNOWN_PASSWORD = "thePassword";
    private static final String KNOWN_HOST = "knownHost";
    private static final int KNOWN_PORT = 27777;
    private static final Config CONFIG = ConfigFactory.load("test");

    private static void assertConnectionPoolSettings(final MongoClientWrapper wrapper,
            final ConnectionPoolSettings expectedConnectionPoolSettings) {
        final MongoClient mongoClient = wrapper.getMongoClient();
        Assertions.assertThat(mongoClient).isNotNull();

        final MongoClientSettings mongoClientSettings = mongoClient.getSettings();
        Assertions.assertThat(mongoClientSettings).isNotNull();
        final ConnectionPoolSettings connectionPoolSettings = mongoClientSettings.getConnectionPoolSettings();
        Assertions.assertThat(connectionPoolSettings).isNotNull().isEqualTo(expectedConnectionPoolSettings);
    }

    private static String createUri(final boolean sslEnabled) {
        final ConnectionString connectionString = new ConnectionString(
                "mongodb://" + KNOWN_USER + ":" + KNOWN_PASSWORD + "@" + KNOWN_SERVER_ADDRESS + "/" + KNOWN_DB_NAME +
                        "?ssl="
                        + sslEnabled);
        return connectionString.getURI();
    }

    private static void assertCreatedByUri(final boolean sslEnabled, final MongoClientWrapper wrapper) {
        final MongoClient mongoClient = wrapper.getMongoClient();
        Assertions.assertThat(mongoClient).isNotNull();

        final MongoClientSettings mongoClientSettings = mongoClient.getSettings();
        Assertions.assertThat(mongoClientSettings.getClusterSettings().getHosts())
                .isEqualTo(Arrays.asList(new ServerAddress(KNOWN_SERVER_ADDRESS)));
        Assertions.assertThat(mongoClientSettings.getCredentialList()).isEqualTo(
                Arrays.asList(
                        MongoCredential.createCredential(KNOWN_USER, KNOWN_DB_NAME, KNOWN_PASSWORD.toCharArray())));
        Assertions.assertThat(mongoClientSettings.getSslSettings().isEnabled()).isEqualTo(sslEnabled);
        final MongoDatabase mongoDatabase = wrapper.getDatabase();
        Assertions.assertThat(mongoDatabase).isNotNull();
        Assertions.assertThat(mongoDatabase.getName()).isEqualTo(KNOWN_DB_NAME);
    }

    private static void assertCreatedByHostAndPort(final MongoClientWrapper wrapper) {
        final MongoClient mongoClient = wrapper.getMongoClient();
        Assertions.assertThat(mongoClient).isNotNull();
        Assertions.assertThat(mongoClient.getSettings().getClusterSettings().getHosts())
                .isEqualTo(Arrays.asList(new ServerAddress(KNOWN_HOST, KNOWN_PORT)));
        final MongoDatabase mongoDatabase = wrapper.getDatabase();
        Assertions.assertThat(mongoDatabase).isNotNull();
        Assertions.assertThat(mongoDatabase.getName()).isEqualTo(KNOWN_DB_NAME);
    }

    private static void assertCreatedByAll(final MongoClientWrapper wrapper) {
        final MongoClient mongoClient = wrapper.getMongoClient();
        Assertions.assertThat(mongoClient).isNotNull();

        final MongoClientSettings mongoClientSettings = mongoClient.getSettings();
        Assertions.assertThat(mongoClientSettings.getClusterSettings().getHosts())
                .isEqualTo(Arrays.asList(new ServerAddress(KNOWN_HOST, KNOWN_PORT)));
        Assertions.assertThat(mongoClientSettings.getCredentialList()).isEqualTo(
                Arrays.asList(
                        MongoCredential.createCredential(KNOWN_USER, KNOWN_DB_NAME, KNOWN_PASSWORD.toCharArray())));
        final MongoDatabase mongoDatabase = wrapper.getDatabase();
        Assertions.assertThat(mongoDatabase).isNotNull();
        Assertions.assertThat(mongoDatabase.getName()).isEqualTo(KNOWN_DB_NAME);
    }

    private static void assertCreatedWithDatabaseName(final MongoClientWrapper wrapper) {
        final MongoClient mongoClient = wrapper.getMongoClient();
        Assertions.assertThat(mongoClient).isNotNull();

        final MongoClientSettings mongoClientSettings = mongoClient.getSettings();
        Assertions.assertThat(mongoClientSettings.getClusterSettings().getHosts())
                .isEqualTo(Arrays.asList(new ServerAddress(KNOWN_HOST, KNOWN_PORT)));
        final MongoDatabase mongoDatabase = wrapper.getDatabase();
        Assertions.assertThat(mongoDatabase).isNotNull();
        Assertions.assertThat(mongoDatabase.getName()).isEqualTo(KNOWN_DB_NAME);
    }

    /** */
    @Test
    public void createByUriWithSslDisabled() {
        // prepare
        final boolean sslEnabled = false;
        final String uri = createUri(sslEnabled);

        final Config config = CONFIG.withValue(MongoConfig.URI, ConfigValueFactory.fromAnyRef(uri));


        // test
        final MongoClientWrapper wrapper = new MongoClientWrapper(config);

        // verify
        assertCreatedByUri(sslEnabled, wrapper);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void createByUriWithSslEnabled() {
        // prepare
        final String uriWithSslEnabled = createUri(true);

        final Config config = CONFIG.withValue(MongoConfig.URI, ConfigValueFactory.fromAnyRef(uriWithSslEnabled));

        // test
        final MongoClientWrapper wrapper = new MongoClientWrapper(config);

        // verify
        assertCreatedByUri(false, wrapper);
    }

    /** */
    @Test
    public void createByHostAndPort() {
        // test
        final MongoClientWrapper wrapper = new MongoClientWrapper(KNOWN_HOST, KNOWN_PORT, KNOWN_DB_NAME, CONFIG);

        // verify
        assertCreatedByHostAndPort(wrapper);
    }

    /** */
    @Test
    public void createByAll() {
        // test
        final MongoClientWrapper wrapper =
                new MongoClientWrapper(KNOWN_HOST, KNOWN_PORT, KNOWN_USER, KNOWN_PASSWORD, KNOWN_DB_NAME, CONFIG);

        // verify
        assertCreatedByAll(wrapper);
    }

    /** */
    @Test
    public void createWithDatabaseName() {
        // test
        final MongoClientWrapper wrapper = new MongoClientWrapper(KNOWN_HOST, KNOWN_PORT, null, null, KNOWN_DB_NAME,
                CONFIG);

        // verify
        assertCreatedWithDatabaseName(wrapper);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void createWithoutPassword() {
        // test
        new MongoClientWrapper(KNOWN_HOST, KNOWN_PORT, KNOWN_USER, null, KNOWN_DB_NAME, CONFIG);
    }

}
