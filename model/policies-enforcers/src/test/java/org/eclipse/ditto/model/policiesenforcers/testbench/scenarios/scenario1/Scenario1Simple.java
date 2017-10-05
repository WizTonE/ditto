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
package org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.scenario1;

import java.util.function.Function;

import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.policies.SubjectType;
import org.eclipse.ditto.model.policiesenforcers.testbench.algorithms.PolicyAlgorithm;
import org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.Scenario;


public interface Scenario1Simple extends Scenario {

    String SCENARIO_GROUP_NAME = Scenario1Simple1.class.getSimpleName();

    String SUBJECT_ALL_GRANTED = "sid_all";
    String SUBJECT_NONE_GRANTED = "sid_none";
    String SUBJECT_WRITE_REVOKED = "sid_write_revoke";

    Policy POLICY = PoliciesModelFactory //
            .newPolicyBuilder("benchmark:" + Scenario1Simple1.class.getSimpleName()) //
            .forLabel("all") //
            .setSubject(SubjectIssuer.GOOGLE_URL, SUBJECT_ALL_GRANTED, SubjectType.JWT) //
            .setSubject(SubjectIssuer.GOOGLE_URL, SUBJECT_WRITE_REVOKED, SubjectType.JWT) //
            .setGrantedPermissions(PoliciesResourceType.thingResource("/"), "READ", "WRITE") //
            .forLabel("revokeWrite") //
            .setSubject(SubjectIssuer.GOOGLE_URL, SUBJECT_WRITE_REVOKED, SubjectType.JWT) //
            .setRevokedPermissions(PoliciesResourceType.thingResource("/"), "WRITE") //
            .build();

    default Policy getPolicy() {
        return POLICY;
    }

    @Override
    default String getScenarioGroup() {
        return SCENARIO_GROUP_NAME;
    }

    @Override
    default Function<PolicyAlgorithm, Boolean> getApplyAlgorithmFunction() {
        return algorithm -> algorithm.hasPermissionsOnResource(getSetup());
    }
}
