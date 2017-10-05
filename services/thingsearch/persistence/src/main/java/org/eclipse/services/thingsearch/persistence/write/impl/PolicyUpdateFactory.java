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
package org.eclipse.services.thingsearch.persistence.write.impl;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.regex;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.EACH;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.EXISTS;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.FIELD_ACL;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.FIELD_ATTRIBUTE_PREFIX;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.FIELD_FEATURES;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.FIELD_FEATURE_PROPERTIES_PREFIX;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.FIELD_FEATURE_PROPERTIES_PREFIX_WITH_ENDING_SLASH;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.FIELD_GLOBAL_READS;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.FIELD_GRANTED;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.FIELD_ID;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.FIELD_RESOURCE;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.FIELD_REVOKED;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.PULL;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.PUSH;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.REGEX_FIELD_END;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.REGEX_START_THING_ID;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcer;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.models.policies.Permission;
import org.eclipse.services.thingsearch.persistence.ThingResourceKey;

/**
 * Factory to create mongodb updates on the polices collection.
 */
@Immutable
final class PolicyUpdateFactory {

    static final Bson PULL_GLOBAL_READS = new Document(PULL,
            new Document(FIELD_INTERNAL, new Document(FIELD_GLOBAL_READS, new Document(EXISTS, Boolean.TRUE))));

    static final Bson PULL_ACL = new Document(PULL,
            new Document(FIELD_INTERNAL, new Document(FIELD_ACL, new Document(EXISTS, Boolean.TRUE))));

    private PolicyUpdateFactory() {
        throw new AssertionError();
    }

    /**
     * Creates a policy update to update the policy of a thing in the search index.
     *
     * @param thing the thing for which the policy is updated.
     * @param policyEnforcer the enforcer which holds the current policy.
     * @return the created update.
     */
    static PolicyUpdate createPolicyIndexUpdate(final Thing thing, final PolicyEnforcer policyEnforcer) {
        final Collection<ResourcePermissions> resourcePermissions = new LinkedHashSet<>();

        resourcePermissions.addAll(thing.getAttributes()
                .map(attributes -> createEntriesForAttributes(attributes, policyEnforcer))
                .orElseGet(Collections::emptySet));
        resourcePermissions.addAll(thing.getFeatures()
                .map(features -> createEntriesForFeatures(features, policyEnforcer))
                .orElseGet(Collections::emptySet));

        final Set<Document> globalReads = getGlobalReadsDocuments(policyEnforcer);

        final Bson pushGlobalReads;
        if (globalReads.isEmpty()) {
            // don't create useless update if there are no global reads
            pushGlobalReads = null;
        } else {
            pushGlobalReads = new Document(PUSH, new Document(FIELD_INTERNAL, new Document(EACH, globalReads)));
        }

        final String thingId = thing.getId().orElseThrow(() -> new IllegalStateException("Thing does not have an ID!"));
        final Set<Document> policyEntries = createPolicyEntries(thingId, resourcePermissions);

        final Bson policiesFilter = createThingRemovalFilter(thingId);

        return new PolicyUpdate(policiesFilter, policyEntries, PULL_GLOBAL_READS, pushGlobalReads, PULL_ACL);
    }

    private static Set<Document> getGlobalReadsDocuments(final PolicyEnforcer policyEnforcer) {
        final Set<String> subjectIds =
                policyEnforcer.getSubjectIdsWithPartialPermission(ThingResourceKey.ROOT, Permission.READ);

        return subjectIds.stream()
                .map(subjectId -> new Document(FIELD_GLOBAL_READS, subjectId))
                .collect(Collectors.toSet());
    }

    /**
     * Creates a policy update for the case an attribute changes.
     *
     * @param thingId the ID of the thing.
     * @param attributePointer the pointer of the attribute.
     * @param attributeValue the attributeValue of the attribute.
     * @param policyEnforcer the enforcer to evaluate the policy.
     * @return the created update.
     */
    static PolicyUpdate createAttributePolicyIndexUpdate(
            final CharSequence thingId,
            final JsonPointer attributePointer,
            final JsonValue attributeValue,
            final PolicyEnforcer policyEnforcer) {

        final Bson removalFilter = createRemovalFilter(thingId, FIELD_ATTRIBUTE_PREFIX + attributePointer.toString());
        final Collection<ResourcePermissions> resourcePermissions =
                createEntriesForAttribute(attributePointer, attributeValue, policyEnforcer);
        final Set<Document> policyIndexInserts = createPolicyEntries(thingId, resourcePermissions);

        return new PolicyUpdate(removalFilter, policyIndexInserts);
    }

    /**
     * Creates a policy update for the case that all attributes changes.
     *
     * @param thingId the ID of the thing.
     * @param attributes the new attributes.
     * @param policyEnforcer the enforcer of the policy.
     * @return the created update.
     */
    static PolicyUpdate createAttributesUpdate(final CharSequence thingId, final Attributes attributes,
            final PolicyEnforcer policyEnforcer) {
        final Bson removalFilter = createRemovalFilter(thingId, FIELD_ATTRIBUTE_PREFIX);
        final Collection<ResourcePermissions> resourcePermissions =
                createEntriesForAttributes(attributes, policyEnforcer);
        final Set<Document> policyIndexInserts = createPolicyEntries(thingId, resourcePermissions);

        return new PolicyUpdate(removalFilter, policyIndexInserts);
    }

    /**
     * Creates a policy update for the case an attribute gets deleted.
     *
     * @param thingId the ID of the thing.
     * @param jsonPointer the pointer of the deleted attribute.
     * @return the created update.
     */
    static PolicyUpdate createAttributeDeletion(final CharSequence thingId, final JsonPointer jsonPointer) {
        final Bson removalFilter = createRemovalFilter(thingId, FIELD_ATTRIBUTE_PREFIX + jsonPointer);
        return new PolicyUpdate(removalFilter, new HashSet<>());
    }

    /**
     * Creates an update for the case a feature gets deleted.
     *
     * @param thingId the ID of the thing.
     * @param featureId the ID of the deleted feature.
     * @return the created update.
     */
    static PolicyUpdate createFeatureDeletion(final CharSequence thingId, final String featureId) {
        final Bson removalFilter = createRemovalFilter(thingId, featureId);
        return new PolicyUpdate(removalFilter, new HashSet<>());
    }

    /**
     * Creates a policy update for the case all features get deleted.
     *
     * @param thingId the ID of the thing.
     * @return the created update.
     */
    static PolicyUpdate createFeaturesDeletion(final CharSequence thingId) {
        final Bson removalFilter = createFeaturesRemovalFilter(thingId);
        return new PolicyUpdate(removalFilter, new HashSet<>());
    }

    /**
     * Creates a policy update for the case all attributes get deleted.
     *
     * @param thingId the ID of the thing.
     * @return the created update.
     */
    static PolicyUpdate createAttributesDeletion(final CharSequence thingId) {
        return new PolicyUpdate(createRemovalFilter(thingId, FIELD_ATTRIBUTE_PREFIX), new HashSet<>());
    }

    /**
     * Creates a policy update for the case a feature property gets deleted.
     *
     * @param thingId the ID of the thing.
     * @param featureId the ID of the feature.
     * @param jsonPointer the json pointer of the feature property.
     * @return the created update.
     */
    static PolicyUpdate createFeaturePropertyDeletion(final CharSequence thingId, final String featureId,
            final JsonPointer jsonPointer) {
        return new PolicyUpdate(createRemovalFilter(thingId,
                featureId + FIELD_FEATURE_PROPERTIES_PREFIX_WITH_ENDING_SLASH + jsonPointer.toString()),
                new HashSet<>());
    }

    /**
     * Creates a policy update for the case the feature properties get deleted.
     *
     * @param thingId the ID of the thing.
     * @param featureId the ID of the feature.
     * @return the created update.
     */
    static PolicyUpdate createFeaturePropertiesDeletion(final CharSequence thingId, final String featureId) {
        return new PolicyUpdate(createRemovalFilter(thingId, featureId + FIELD_FEATURE_PROPERTIES_PREFIX),
                new HashSet<>());
    }

    /**
     * Creates a policy update for the case a feature gets created.
     *
     * @param thingId the ID of the thing.
     * @param feature the feature.
     * @param policyEnforcer the enforcer to evaluate the current policy.
     * @return the created update.
     */
    static PolicyUpdate createFeatureUpdate(final CharSequence thingId, final Feature feature,
            final PolicyEnforcer policyEnforcer) {
        final Collection<ResourcePermissions> resourcePermissions = createEntriesForFeature(feature, policyEnforcer);
        final Set<Document> policyEntries = createPolicyEntries(thingId, resourcePermissions);
        final Bson removalFilter = createFeatureRemovalFilter(thingId, feature.getId());

        return new PolicyUpdate(removalFilter, policyEntries);
    }

    /**
     * Creates a policy update for the case all features get updated.
     *
     * @param thingId the ID of the thing.
     * @param features the features.
     * @param policyEnforcer the policy.
     * @return the created update.
     */
    static PolicyUpdate createFeaturesUpdate(final CharSequence thingId, final Features features,
            final PolicyEnforcer policyEnforcer) {
        final Collection<ResourcePermissions> resourcePermissions = createEntriesForFeatures(features, policyEnforcer);
        final Set<Document> policyEntries = createPolicyEntries(thingId, resourcePermissions);
        final Bson removalFilter = createFeaturesRemovalFilter(thingId);

        return new PolicyUpdate(removalFilter, policyEntries);
    }

    /**
     * Creates a policy update for the case a feature property gets updated.
     *
     * @param thingId the ID of the thing.
     * @param featureId the ID of the feature.
     * @param propertyPointer the pointer of the feature property.
     * @param propertyValue the value of the feature property.
     * @param policyEnforcer the enforcer to evaluate the current policy.
     * @return the created update.
     */
    static PolicyUpdate createFeaturePropertyUpdate(
            final CharSequence thingId,
            final CharSequence featureId,
            final JsonPointer propertyPointer,
            final JsonValue propertyValue,
            final PolicyEnforcer policyEnforcer) {

        final Collection<ResourcePermissions> resourcePermissions =
                createEntriesForFeatureProperty(featureId, propertyPointer, propertyValue, policyEnforcer);
        final Set<Document> policyEntries = createPolicyEntries(thingId, resourcePermissions);

        final Bson removalFilter = createRemovalFilter(thingId,
                featureId + FIELD_FEATURE_PROPERTIES_PREFIX + propertyPointer.toString());

        return new PolicyUpdate(removalFilter, policyEntries);
    }

    /**
     * Creates a policy update for te case feature properties get updated.
     *
     * @param thingId the ID of the thing.
     * @param featureId the ID of the feature.
     * @param properties the properties to be updated.
     * @param policyEnforcer the enforcer to evaluate the policy.
     * @return the created update.
     */
    static PolicyUpdate createFeaturePropertiesUpdate(
            final CharSequence thingId,
            final String featureId,
            final FeatureProperties properties,
            final PolicyEnforcer policyEnforcer) {

        final Bson removalFilter = createRemovalFilter(thingId, featureId + FIELD_FEATURE_PROPERTIES_PREFIX);
        final Collection<ResourcePermissions> resourcePermissions = new HashSet<>();
        properties.forEach(featureProperty -> resourcePermissions.addAll(
                createEntriesForFeatureProperty(featureId, JsonFactory.newPointer(featureProperty.getKey()),
                        featureProperty.getValue(), policyEnforcer)));
        final Set<Document> policyEntries = createPolicyEntries(thingId, resourcePermissions);

        return new PolicyUpdate(removalFilter, policyEntries);
    }

    /**
     * Creates a policy update for the case that a thing gets deleted.
     *
     * @param thingId the ID of the deleted thing.
     * @return the created update
     */
    static PolicyUpdate createDeleteThingUpdate(final CharSequence thingId) {
        final Bson removalFilter = createThingRemovalFilter(thingId);
        return new PolicyUpdate(removalFilter, new HashSet<>());
    }

    private static Bson createThingRemovalFilter(final CharSequence thingId) {
        return createFeatureRemovalFilter(thingId, "");
    }

    private static Bson createFeatureRemovalFilter(final CharSequence thingId, final String featureId) {
        return regex(FIELD_ID, REGEX_START_THING_ID + Pattern.quote(thingId + ":" + featureId));
    }

    private static Bson createRemovalFilter(final CharSequence thingId, final String pointer) {
        return regex(FIELD_ID, REGEX_START_THING_ID + thingId + ":" + pointer + REGEX_FIELD_END);
    }

    private static Bson createFeaturesRemovalFilter(final CharSequence thingId) {
        return and(regex(FIELD_ID, REGEX_START_THING_ID + Pattern.quote(thingId + ":")), regex(FIELD_RESOURCE,
                REGEX_START_THING_ID + Pattern.quote(FIELD_FEATURES)));
    }

    private static Set<Document> createPolicyEntries(final CharSequence thingId,
            final Collection<ResourcePermissions> resourcePermissions) {

        return resourcePermissions.stream()
                .map(rp -> createPolicyEntry(rp, thingId))
                .collect(Collectors.toSet());
    }

    private static Document createPolicyEntry(final ResourcePermissions resourcePermissions,
            final CharSequence thingId) {

        return new Document()
                .append(FIELD_ID, resourcePermissions.createPolicyEntryId(thingId))
                .append(FIELD_GRANTED, resourcePermissions.getReadGrantedSubjectIds())
                .append(FIELD_REVOKED, resourcePermissions.getReadRevokedSubjectIds())
                .append(FIELD_RESOURCE, resourcePermissions.getResource());
    }

    private static Collection<ResourcePermissions> createEntriesForAttributes(final Attributes attributes,
            final PolicyEnforcer policyEnforcer) {

        final Collection<ResourcePermissions> result = new HashSet<>();
        attributes.forEach(attribute -> result.addAll(
                createEntriesForAttribute(JsonFactory.newPointer(attribute.getKey()), attribute.getValue(),
                        policyEnforcer)));

        return result;
    }

    private static Collection<ResourcePermissions> createEntriesForAttribute(final JsonPointer attributePointer,
            final JsonValue attributeValue, final PolicyEnforcer policyEnforcer) {

        final Collection<ResourcePermissions> result = new HashSet<>(3);

        if (attributeValue.isObject() && !attributeValue.isNull()) {
            final JsonObject jsonObject = attributeValue.asObject();
            // Recursion!
            jsonObject.forEach(
                    subField -> result.addAll(createEntriesForAttribute(attributePointer.addLeaf(subField.getKey()),
                            subField.getValue(), policyEnforcer)));
        } else {
            result.add(AttributeResourcePermissions.getInstance(attributePointer, attributeValue, policyEnforcer));
        }

        return result;
    }

    private static Collection<ResourcePermissions> createEntriesForFeatures(final Features features,
            final PolicyEnforcer policyEnforcer) {

        final Collection<ResourcePermissions> result = new HashSet<>();
        features.forEach(feature -> result.addAll(createEntriesForFeature(feature, policyEnforcer)));
        return result;
    }

    private static Collection<ResourcePermissions> createEntriesForFeature(final Feature feature,
            final PolicyEnforcer policyEnforcer) {

        final Collection<ResourcePermissions> result = new HashSet<>();

        result.add(FeatureResourcePermissions.getInstance(feature, policyEnforcer));

        final FeatureProperties featureProperties = feature.getProperties().orElseGet(
                ThingsModelFactory::emptyFeatureProperties);
        featureProperties.forEach(featureProperty -> result.addAll(
                createEntriesForFeatureProperty(feature.getId(), JsonFactory.newPointer(featureProperty.getKey()),
                        featureProperty.getValue(), policyEnforcer)));

        return result;
    }

    private static Collection<ResourcePermissions> createEntriesForFeatureProperty(final CharSequence featureId,
            final JsonPointer propertyPointer, final JsonValue propertyValue, final PolicyEnforcer policyEnforcer) {

        final Collection<ResourcePermissions> result = new HashSet<>(3);

        if (propertyValue.isObject() && !propertyValue.isNull()) {
            final JsonObject propertyValueJsonObject = propertyValue.asObject();
            // Recursion!
            propertyValueJsonObject.forEach(subField -> result.addAll(
                    createEntriesForFeatureProperty(featureId, propertyPointer.addLeaf(subField.getKey()),
                            subField.getValue(), policyEnforcer)));
        } else {
            result.add(FeaturePropertyResourcePermissions.getInstance(featureId, propertyPointer, propertyValue,
                    policyEnforcer));
        }

        return result;
    }

}
