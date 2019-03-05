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
package org.eclipse.ditto.services.utils.protocol.config;

/**
 * This interface provides access to the Ditto protocol adaption config.
 */
public interface WithProtocolConfig {

    /**
     * Returns the configuration settings for Ditto protocol Adaption.
     *
     * @return the config.
     */
    ProtocolConfig getProtocolConfig();

}
