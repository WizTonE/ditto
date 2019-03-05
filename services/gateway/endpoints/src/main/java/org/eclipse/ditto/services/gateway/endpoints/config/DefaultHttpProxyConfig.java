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

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the HTTP proxy config.
 */
@Immutable
public final class DefaultHttpProxyConfig implements AuthenticationConfig.HttpProxyConfig, Serializable {

    private static final String CONFIG_PATH = "http.proxy";

    private static final long serialVersionUID = 772633239915104623L;

    private final boolean enabled;
    private final String hostName;
    private final int port;
    private final String userName;
    private final String password;

    private DefaultHttpProxyConfig(final ScopedConfig scopedConfig) {
        enabled = scopedConfig.getBoolean(HttpProxyConfigValue.ENABLED.getConfigPath());
        hostName = scopedConfig.getString(HttpProxyConfigValue.HOST_NAME.getConfigPath());
        port = scopedConfig.getInt(HttpProxyConfigValue.PORT.getConfigPath());
        userName = scopedConfig.getString(HttpProxyConfigValue.USER_NAME.getConfigPath());
        password = scopedConfig.getString(HttpProxyConfigValue.PASSWORD.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultHttpProxyConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the HTTP proxy config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultHttpProxyConfig of(final Config config) {
        return new DefaultHttpProxyConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, HttpProxyConfigValue.values()));
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getHostname() {
        return hostName;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public String getUsername() {
        return userName;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultHttpProxyConfig that = (DefaultHttpProxyConfig) o;
        return enabled == that.enabled &&
                port == that.port &&
                Objects.equals(hostName, that.hostName) &&
                Objects.equals(userName, that.userName) &&
                Objects.equals(password, that.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, hostName, port, userName, password);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "enabled=" + enabled +
                ", hostName=" + hostName +
                ", port=" + port +
                ", userName=" + userName +
                ", password=*****" +
                "]";
    }

}
