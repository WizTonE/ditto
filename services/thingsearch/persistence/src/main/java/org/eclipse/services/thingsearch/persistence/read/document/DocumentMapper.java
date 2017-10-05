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
package org.eclipse.services.thingsearch.persistence.read.document;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.things.Thing;

import org.eclipse.services.thingsearch.common.util.KeyEscapeUtil;

/**
 * A mapper for {@link Document} instances.
 */
public final class DocumentMapper {

    private DocumentMapper() {
        throw new AssertionError();
    }

    /**
     * Returns a new {@code Document}.
     *
     * @return the Document.
     */
    static Document newDocument() {
        return new Document();
    }

    public static List<Document> toList(final Document document, final String name) {
        @SuppressWarnings("unchecked") List<Document> entries = (List<Document>) document.get(name);

        if (entries == null) {
            entries = new ArrayList<>();
            document.append(name, entries);
        }

        return entries;
    }

    /**
     * Maps a {@link Thing} to a document.
     *
     * @param thing the thing to map
     * @return the mapped document
     */
    public static Document toDocument(final Thing thing) {
        final ThingDocumentBuilder builder = ThingDocumentBuilder.create(thing.getId().get(), thing.getPolicyId()
                .orElse(null));

        thing.getAccessControlList().ifPresent(builder::acl);
        thing.getAttributes().ifPresent(builder::attributes);
        thing.getFeatures().ifPresent(builder::features);

        return builder.build();
    }

    /**
     * Maps the given {@code jsonValue} to it's equivalent Java type. A {@code JsonObject} will be mapped to {@code
     * Document}.
     *
     * @param jsonValue the JSON value.
     * @return the value.
     */
    public static Object toValue(final JsonValue jsonValue) {
        final Object value;

        if (jsonValue.isNull()) {
            value = null;
        } else if (jsonValue.isString()) {
            value = jsonValue.asString();
        } else if (jsonValue.isBoolean()) {
            value = jsonValue.asBoolean();
        } else if (jsonValue.isNumber()) {
            value = handleNumberAttribute(jsonValue);
        } else if (jsonValue.isObject()) {
            value = objectToDocument(jsonValue.asObject());
        } else {
            value = null;
        }

        return value;
    }

    private static Document objectToDocument(final JsonObject jsonObject) {
        final Document subDocument = new Document();
        jsonObject.forEach((field) -> //
        {
            final String key = KeyEscapeUtil.escape(field.getKeyName());
            final Object value = toValue(field.getValue());
            subDocument.append(key, value);
        });
        return subDocument;
    }

    private static Object handleNumberAttribute(final JsonValue jsonValue) {
        try {
            return jsonValue.asLong();
        } catch (final NumberFormatException e) {
            return jsonValue.asDouble();
        }
    }

}
