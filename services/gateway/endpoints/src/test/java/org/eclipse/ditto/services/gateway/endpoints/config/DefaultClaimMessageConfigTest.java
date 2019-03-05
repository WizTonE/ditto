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
package org.eclipse.ditto.services.gateway.endpoints.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.time.Duration;

import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultClaimMessageConfig}.
 */
public final class DefaultClaimMessageConfigTest {

    private static Config claimMessageTestConfig;

    @BeforeClass
    public static void initTestFixture() {
        claimMessageTestConfig = ConfigFactory.load("claim-message-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultClaimMessageConfig.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultClaimMessageConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testSerializationAndDeserialization() throws IOException, ClassNotFoundException {
        final DefaultClaimMessageConfig underTest = DefaultClaimMessageConfig.of(claimMessageTestConfig);

        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final ObjectOutput objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
        objectOutputStream.writeObject(underTest);
        objectOutputStream.close();

        final byte[] underTestSerialized = byteArrayOutputStream.toByteArray();

        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(underTestSerialized);
        final ObjectInput objectInputStream = new ObjectInputStream(byteArrayInputStream);
        final Object underTestDeserialized = objectInputStream.readObject();

        assertThat(underTestDeserialized).isEqualTo(underTest);
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultClaimMessageConfig underTest = DefaultClaimMessageConfig.of(ConfigFactory.empty());

        assertThat(underTest.getDefaultTimeout())
                .as("defaultTimeout")
                .isEqualTo(Duration.ofSeconds(35L));
        assertThat(underTest.getMaxTimeout())
                .as("maxTimeout")
                .isEqualTo(Duration.ofSeconds(330L));
    }

    @Test
    public void underTestReturnsValuesOfBaseConfig() {
        final DefaultClaimMessageConfig underTest = DefaultClaimMessageConfig.of(claimMessageTestConfig);

        assertThat(underTest.getDefaultTimeout())
                .as("defaultTimeout")
                .isEqualTo(Duration.ofSeconds(42L));
        assertThat(underTest.getMaxTimeout())
                .as("maxTimeout")
                .isEqualTo(Duration.ofSeconds(23L));
    }

}