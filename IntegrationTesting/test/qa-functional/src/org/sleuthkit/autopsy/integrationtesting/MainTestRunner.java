/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.integrationtesting;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import org.sleuthkit.autopsy.integrationtesting.config.IntegrationTestConfig;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import junit.framework.Test;
import junit.framework.TestCase;
import org.apache.cxf.common.util.CollectionUtils;
import org.netbeans.junit.NbModuleSuite;
import org.openide.util.Lookup;
import org.openide.util.io.NbObjectInputStream;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.Case.CaseType;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException;
import org.sleuthkit.autopsy.datasourceprocessors.DataSourceProcessorUtility;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestJobSettings.IngestType;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleTemplate;
import org.sleuthkit.autopsy.integrationtesting.config.CaseConfig;
import org.sleuthkit.autopsy.integrationtesting.config.IntegrationCaseType;
import org.sleuthkit.autopsy.integrationtesting.interfaces.IntegrationTest;
import org.sleuthkit.autopsy.integrationtesting.interfaces.IntegrationTests;
import org.sleuthkit.autopsy.python.FactoryClassNameNormalizer;
import org.sleuthkit.autopsy.testutils.CaseUtils;
import org.sleuthkit.autopsy.testutils.IngestUtils;
import org.sleuthkit.datamodel.TskCoreException;
import org.yaml.snakeyaml.Yaml;

/**
 * Main entry point for running integration tests. Handles processing
 * parameters, ingesting data sources for cases, and running items implementing
 * IntegrationTests.
 */
public class MainTestRunner extends TestCase {

    private static final Logger logger = Logger.getLogger(MainTestRunner.class.getName()); // DO NOT USE AUTOPSY LOGGER
    private static final String CONFIG_FILE_KEY = "integrationConfigFile";
    private static final IngestType DEFAULT_INGEST_TYPE = IngestType.ALL_MODULES;

    /**
     * Constructor required by JUnit
     */
    public MainTestRunner(String name) {
        super(name);
    }

    /**
     * Creates suite from particular test cases.
     */
    public static Test suite() {
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(MainTestRunner.class).
                clusters(".*").
                enableModules(".*");

        return NbModuleSuite.create(conf.addTest("runIntegrationTests"));
    }

    public void runIntegrationTests() {
        String configFile = System.getProperty(CONFIG_FILE_KEY);
        IntegrationTestConfig config;
        try {
            config = getConfigFromFile(configFile);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "There was an error processing integration test config at " + configFile, ex);
            return;
        }

        if (config == null) {
            logger.log(Level.WARNING, "No properly formatted config found at " + configFile);
        }

        if (!CollectionUtils.isEmpty(config.getCases())) {
            for (CaseConfig caseConfig : config.getCases()) {
                for (CaseType caseType : IntegrationCaseType.getCaseTypes(caseConfig.getCaseTypes())) {
                    Case autopsyCase = runIngest(caseConfig, caseType);
                    if (autopsyCase == null || autopsyCase != Case.getCurrentCase()) {
                        logger.log(Level.WARNING,
                                String.format("Case was not properly ingested or setup correctly for environment.  Case is %s and current case is %s.",
                                        autopsyCase, Case.getCurrentCase()));
                        return;
                    }

                    String caseName = autopsyCase.getName();

                    runIntegrationTests(config, caseConfig, caseType);

                    try {
                        Case.closeCurrentCase();
                    } catch (CaseActionException ex) {
                        logger.log(Level.WARNING, "There was an error while trying to close current case: {0}", caseName);
                        return;
                    }
                }
            }
        }
    }

    private Case runIngest(CaseConfig caseConfig, CaseType caseType) {
        Case openCase = null;
        switch (caseType) {
            case SINGLE_USER_CASE:
                openCase = CaseUtils.createAsCurrentCase(caseConfig.getCaseName());
                break;
            case MULTI_USER_CASE:
            // TODO
            default:
                throw new IllegalArgumentException("Unknown case type: " + caseType);
        }

        if (openCase == null) {
            logger.log(Level.WARNING, String.format("No case could be created for %s of type %s.", caseConfig.getCaseName(), caseType));
            return null;
        }

        addDataSourcesToCase(caseConfig.getDataSourceResources(), caseConfig.getCaseName());
        try {
            IngestJobSettings ingestJobSettings = getIngestSettings(caseConfig.getCaseName(),
                    DEFAULT_INGEST_TYPE,
                    caseConfig.getIngestModules(), caseConfig.getIngestModuleSettingsPath());

            IngestUtils.runIngestJob(openCase.getDataSources(), ingestJobSettings);
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, String.format("There was an error while ingesting datasources for case %s", caseConfig.getCaseName()), ex);
        }

        return openCase;
    }

    private IngestModuleFactory getIngestModuleFactory(String className) {
        if (className == null) {
            logger.log(Level.WARNING, "No class name provided.");
            return null;
        }

        Class<?> ingestModuleFactoryClass = null;
        try {
            ingestModuleFactoryClass = Class.forName(className);
        } catch (ClassNotFoundException ex) {
            logger.log(Level.WARNING, String.format("No class found matching canonical name in config of %s.", className), ex);
            return null;
        }

        Object factoryObject = null;
        try {
            factoryObject = ingestModuleFactoryClass.newInstance();
        } catch (InstantiationException | IllegalAccessException ex) {
            logger.log(Level.WARNING, String.format("Error during instantiation of %s.", className), ex);
            return null;
        }

        if (factoryObject instanceof IngestModuleFactory) {
            return (IngestModuleFactory) factoryObject;
        } else {
            logger.log(Level.WARNING, String.format("Could not properly instantiate class of: %s", className));
            return null;
        }
    }

    private IngestJobSettings getIngestSettings(String profileName, IngestType ingestType, List<String> enabledFactoryClasses, String pathToIngestModuleSettings) {
        Map<String, IngestModuleFactory> classToFactoryMap = enabledFactoryClasses.stream()
                .map(factoryName -> getIngestModuleFactory(factoryName))
                .filter(factory -> factory != null)
                .collect(Collectors.toMap(factory -> factory.getClass().getCanonicalName(), factory -> factory, (f1, f2) -> f1));

        List<IngestModuleTemplate> ingestModuleTemplates = enabledFactoryClasses.stream()
                .map(className -> {
                    IngestModuleFactory factory = classToFactoryMap.get(className);
                    if (factory == null) {
                        logger.log(Level.WARNING, "Could not find ingest module factory: " + className);
                    }
                    return factory;
                })
                .filter(factory -> factory != null)
                .map(factory -> getTemplate(pathToIngestModuleSettings, factory))
                .collect(Collectors.toList());

        return new IngestJobSettings(profileName, ingestType, ingestModuleTemplates);
    }

    private IngestModuleTemplate getTemplate(String pathToIngestModuleSettings, IngestModuleFactory factory) {
        String fileName = FactoryClassNameNormalizer.normalize(factory.getClass().getCanonicalName()) + ".settings";
        File settingsFile = Paths.get(pathToIngestModuleSettings, fileName).toFile();
        if (settingsFile.exists()) {
            try (NbObjectInputStream in = new NbObjectInputStream(new FileInputStream(settingsFile.getAbsolutePath()))) {
                IngestModuleIngestJobSettings settings = (IngestModuleIngestJobSettings) in.readObject();
                return new IngestModuleTemplate(factory, settings);
            } catch (IOException | ClassNotFoundException ex) {
                logger.log(Level.WARNING, String.format("Unable to open %s as IngestModuleIngestJobSettings", settingsFile), ex);
            }
        }

        return new IngestModuleTemplate(factory, factory.getDefaultIngestJobSettings());
    }

    private void addDataSourcesToCase(List<String> pathStrings, String caseName) {
        for (String strPath : pathStrings) {
            Path path = Paths.get(strPath);
            List<AutoIngestDataSourceProcessor> processors = null;
            try {
                processors = DataSourceProcessorUtility.getOrderedListOfDataSourceProcessors(path);
            } catch (AutoIngestDataSourceProcessorException ex) {
                logger.log(Level.WARNING, String.format("There was an error while adding data source: %s to case %s", strPath, caseName));
            }

            if (CollectionUtils.isEmpty(processors)) {
                continue;
            }

            IngestUtils.addDataSource(processors.get(0), path);
        }
    }

    private IntegrationTestConfig getConfigFromFile(String filePath) throws IOException {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(IntegrationTestConfig.class,
                 IntegrationTestConfig.DESERIALIZER);
        Gson gson = builder.create();
        return gson.fromJson(new FileReader(new File(filePath)), IntegrationTestConfig.class
        );
    }

    private void runIntegrationTests(IntegrationTestConfig config, CaseConfig caseConfig, CaseType caseType) {
        // this will capture output results
        OutputResults results = new OutputResults();

        // run through each ConsumerIntegrationTest
        for (IntegrationTests testGroup : Lookup.getDefault().lookupAll(IntegrationTests.class
        )) {

            // if test should not be included in results, skip it.
            if (!caseConfig.getTestConfig()
                    .hasIncludedTest(testGroup.getClass().getCanonicalName())) {
                continue;
            }

            List<Method> testMethods = getIntegrationTestMethods(testGroup);

            if (CollectionUtils.isEmpty(testMethods)) {
                continue;
            }

            testGroup.setupClass();
            for (Method testMethod : testMethods) {
                runIntegrationTestMethod(results, testGroup, testMethod);
            }

            testGroup.tearDownClass();
        }

// write the results for the case to a file
        serializeFile(results, config.getRootTestOutputPath(), caseConfig.getCaseName(), getCaseTypeId(caseType));
    }

    private String getCaseTypeId(CaseType caseType) {
        if (caseType == null) {
            return "";
        }

        switch (caseType) {
            case SINGLE_USER_CASE:
                return "singleUser";
            case MULTI_USER_CASE:
                return "multiUser";
            default:
                throw new IllegalArgumentException("Unknown case type: " + caseType);
        }
    }

    private void runIntegrationTestMethod(OutputResults results, IntegrationTests testGroup, Method testMethod) {
        testGroup.setup();

        // run the test method and get the results
        Object serializableResult = null;

        try {
            serializableResult = testMethod.invoke(testGroup);
        } catch (IllegalAccessException | IllegalArgumentException ex) {
            logger.log(Level.WARNING,
                    String.format("test method %s in %s could not be properly invoked",
                            testMethod.getName(), testGroup.getClass().getCanonicalName()),
                    ex);

            serializableResult = ex;
        } catch (InvocationTargetException ex) {
            serializableResult = ex.getCause();
        }

        testGroup.tearDown();

        // add the results and capture the package, class, 
        // and method of the test for easy location of failed tests
        results.addResult(
                testGroup.getClass().getPackage().getName(),
                testGroup.getClass().getSimpleName(),
                testMethod.getName(),
                serializableResult);
    }

    private List<Method> getIntegrationTestMethods(IntegrationTests testGroup) {
        return Stream.of(testGroup.getClass().getMethods())
                .filter((method) -> method.getAnnotation(IntegrationTest.class
        ) != null)
                .collect(Collectors.toList());
    }

    private void serializeFile(OutputResults results, String outputFolder, String caseName, String caseType) {
        String outputExtension = ".yml";
        Path outputPath = Paths.get(outputFolder, String.format("%s-%s%s", caseName, caseType, outputExtension));
        Yaml yaml = new Yaml();

        try {

            FileWriter writer = new FileWriter(outputPath.toFile());
            yaml.dump(results.getSerializableData(), writer);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "There was an error writing results to outputPath: " + outputPath, ex);
        }
    }
}
