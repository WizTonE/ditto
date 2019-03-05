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

import java.io.Serializable;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;
import org.eclipse.ditto.services.utils.config.WithConfigPath;

import com.typesafe.config.Config;

/**
 * This interface is the default implementation of the Gateway authentication config.
 */
@Immutable
public final class DefaultAuthenticationConfig implements AuthenticationConfig, Serializable, WithConfigPath {

    private static final String CONFIG_PATH = "authentication";

    private static final long serialVersionUID = -3286469359754235360L;

    private final boolean dummyAuthEnabled;
    private final HttpProxyConfig httpProxyConfig;
    private final DevOpsConfig devOpsConfig;

    private DefaultAuthenticationConfig(final ScopedConfig scopedConfig) {
        dummyAuthEnabled = scopedConfig.getBoolean(AuthenticationConfigValue.DUMMY_AUTH_ENABLED.getConfigPath());
        httpProxyConfig = DefaultHttpProxyConfig.of(scopedConfig);
        devOpsConfig = DefaultDevOpsConfig.of(scopedConfig);
    }

    /**
     * Returns an instance of {@code DefaultAuthenticationConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the authentication config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultAuthenticationConfig of(final Config config) {
        return new DefaultAuthenticationConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, AuthenticationConfigValue.values()));
    }

    @Override
    public boolean isDummyAuthenticationEnabled() {
        return dummyAuthEnabled;
    }

    @Override
    public DevOpsConfig getDevOpsConfig() {
        return devOpsConfig;
    }

    @Override
    public HttpProxyConfig getHttpProxyConfig() {
        return httpProxyConfig;
    }

    /**
     * @return always {@value CONFIG_PATH}.
     */
    @Override
    public String getConfigPath() {
        return CONFIG_PATH;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultAuthenticationConfig that = (DefaultAuthenticationConfig) o;
        return dummyAuthEnabled == that.dummyAuthEnabled &&
                httpProxyConfig.equals(that.httpProxyConfig) &&
                devOpsConfig.equals(that.devOpsConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dummyAuthEnabled, httpProxyConfig, devOpsConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "dummyAuthEnabled=" + dummyAuthEnabled +
                ", httpProxyConfig=" + httpProxyConfig +
                ", devOpsConfig=" + devOpsConfig +
                "]";
    }

}
