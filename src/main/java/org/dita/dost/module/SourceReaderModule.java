/*
 * This file is part of the DITA Open Toolkit project.
 *
 * Copyright 2017 Jarno Elovirta
 *
 *  See the accompanying LICENSE file for applicable license.
 */
package org.dita.dost.module;

import org.apache.xerces.xni.grammars.XMLGrammarPool;
import org.dita.dost.reader.GrammarPoolManager;
import org.dita.dost.util.CatalogUtils;
import org.dita.dost.util.XMLUtils;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.File;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static org.dita.dost.util.Configuration.parserFeatures;
import static org.dita.dost.util.Configuration.parserMap;
import static org.dita.dost.util.Constants.*;


/**
 * Common functionality for modules that read source documents.
 */
abstract class SourceReaderModule extends AbstractPipelineModuleImpl {

    private static final String FEATURE_GRAMMAR_POOL = "http://apache.org/xml/properties/internal/grammar-pool";

    /**
     * XMLReader instance for parsing dita file
     */
    XMLReader reader;
    /**
     * Validate source documents
     */
    boolean validate;
    /**
     * Use grammar pool cache
     */
    boolean gramcache = true;
    /**
     * Absolute DITA-OT base path.
     */
    File ditaDir;

    /**
     * Get reader for input format
     * @param format input document format
     * @return reader for given format
     * @throws SAXException if creating reader failed
     */
    XMLReader getXmlReader(final String format) throws SAXException {
        if (format == null || format.equals(ATTR_FORMAT_VALUE_DITA)) {
            return reader;
        }
        for (final Map.Entry<String, String> e : parserMap.entrySet()) {
            if (format.equals(e.getKey())) {
                try {
                    // XMLReaderFactory.createXMLReader cannot be used
                    final XMLReader r = (XMLReader) Class.forName(e.getValue()).newInstance();
                    final Map<String, Boolean> features = parserFeatures.getOrDefault(e.getKey(), emptyMap());
                    for (final Map.Entry<String, Boolean> feature : features.entrySet()) {
                        try {
                            r.setFeature(feature.getKey(), feature.getValue());
                        } catch (final SAXNotRecognizedException ex) {
                            // Not Xerces, ignore exception
                        }
                    }
                    return r;
                } catch (final InstantiationException | ClassNotFoundException | IllegalAccessException ex) {
                    throw new SAXException(ex);
                }
            }
        }
        return reader;
    }

    /**
     * XML reader used for pipeline parsing DITA documents.
     *
     * @throws SAXException if parser configuration failed
     */
    void initXmlReader() throws SAXException {
        if (parserMap.containsKey(ATTR_FORMAT_VALUE_DITA)) {
            reader = XMLReaderFactory.createXMLReader(parserMap.get(ATTR_FORMAT_VALUE_DITA));
            final Map<String, Boolean> features = parserFeatures.getOrDefault(ATTR_FORMAT_VALUE_DITA, emptyMap());
            for (final Map.Entry<String, Boolean> feature : features.entrySet()) {
                try {
                    reader.setFeature(feature.getKey(), feature.getValue());
                } catch (final SAXNotRecognizedException e) {
                    // Not Xerces, ignore exception
                }
            }
        } else {
            reader = XMLUtils.getXMLReader();
        }

        reader.setFeature(FEATURE_NAMESPACE_PREFIX, true);
        if (validate) {
            reader.setFeature(FEATURE_VALIDATION, true);
            try {
                reader.setFeature(FEATURE_VALIDATION_SCHEMA, true);
            } catch (final SAXNotRecognizedException e) {
                // Not Xerces, ignore exception
            }
        }
        if (gramcache) {
            final XMLGrammarPool grammarPool = GrammarPoolManager.getGrammarPool();
            try {
                reader.setProperty(FEATURE_GRAMMAR_POOL, grammarPool);
                logger.info("Using Xerces grammar pool for DTD and schema caching.");
            } catch (final NoClassDefFoundError e) {
                logger.debug("Xerces not available, not using grammar caching");
            } catch (final SAXNotRecognizedException | SAXNotSupportedException e) {
                logger.warn("Failed to set Xerces grammar pool for parser: " + e.getMessage());
            }
        }

        CatalogUtils.setDitaDir(ditaDir);
        reader.setEntityResolver(CatalogUtils.getCatalogResolver());
    }

}
