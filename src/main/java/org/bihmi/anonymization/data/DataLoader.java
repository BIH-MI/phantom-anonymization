/*
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.;
 */

package org.bihmi.anonymization.data;

import org.bihmi.anonymization.config.AttributeConfig;
import org.bihmi.anonymization.config.DataConfig;
import org.bihmi.anonymization.data.DataLoader;
import org.deidentifier.arx.AttributeType;
import org.deidentifier.arx.AttributeType.Hierarchy;
import org.deidentifier.arx.Data;
import org.deidentifier.arx.DataType;
import org.deidentifier.arx.aggregates.StatisticsSummary;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;

/**
 * Helper class to load data.
 */
public class DataLoader {

    public static Map<String, Hierarchy> getAllHierarchies(DataConfig dataConfig) throws IOException {
        Map<String, Hierarchy> result = new HashMap<>();
        for (AttributeConfig attributeConfig : dataConfig.getAttributeConfigs()) {
            result.put(attributeConfig.getName(), attributeConfig.parseArxHierarchy());
        }
        return result;
    }

    /**
     * Reads data set from .csv file
     *
     * @param dataCSVFile path to .csv file
     * @return Data ARX data object
     */
    public static Data loadData(String dataCSVFile) throws IOException {
        return Data.create(dataCSVFile, Charset.defaultCharset(), ';');
    }


    /**
     * Returns the generalization hierarchy for the dataset and attribute from a .csv file. Expects a csv file with a
     * hierarchy at pathToHierarchy
     *
     * @param pathToHierarchy path to file with Hierarchy
     * @return hierarchy as described in the file
     */
    public static Hierarchy loadHierarchy(String pathToHierarchy) throws IOException {
        return Hierarchy.create(pathToHierarchy, Charset.defaultCharset(), ';');
    }

    /**
     * Configures and returns the dataset.
     * <p>
     * Loads dataset according to DataConfig configuration. Either uses dataConfig.getAttributeConfigs() or
     *  uses dataConfig.getAttributeConfigsFromFile() to load Attribute configurations.
     * Uses dataset configuration to define attribute types and loads hierarchies for each attribute.
     */
    public static Data getData(DataConfig dataConfig) throws IOException {
        List<AttributeConfig> attributeConfigs = dataConfig.getAttributeConfigs();
        Map<String, Hierarchy> allHierarchies = DataLoader.getAllHierarchies(dataConfig);
        Data data = DataLoader.loadData(dataConfig.getDataCsvFile());
        return configureDataWithAttributeConfigAndHierarchies(data, attributeConfigs, allHierarchies);
    }

    /**
     * Configures and returns the dataset.
     * @param data Dataset to be configured
     * @param attributes List of all attributes
     * @param allHierarchies Generalization hierarchies to be used
     * @return the dataset with updated configuration
     */
    public static Data configureDataWithAttributeConfigAndHierarchies(Data data, List<AttributeConfig> attributes, Map<String, Hierarchy> allHierarchies) {
        for (AttributeConfig attribute : attributes) {
            if (attribute.getInclude()) {
                String attributeName = attribute.getName();
                switch (attribute.getDataType()) {
                    case "categorical":
                        data.getDefinition().setDataType(attributeName, DataType.STRING);
                        break;
                    case "continuous":
                        data.getDefinition().setDataType(attributeName, DataType.createDecimal("#.#", Locale.US));
                        break;
                    case "date":
                        if (attribute.getDateFormat() == null) {
                            data.getDefinition().setDataType(attributeName, DataType.DATE);
                        } else {
                            data.getDefinition().setDataType(attributeName, DataType.createDate(attribute.getDateFormat()));
                        }
                        break;
                    case "ordinal":
                        throw new RuntimeException("Ordinal not supported here! Ordinal attribute " + attributeName);
                    default:
                        throw new RuntimeException("Invalid datatype");
                }
                data.getDefinition().setHierarchy(attributeName, allHierarchies.get(attributeName));
                data.getDefinition().setAttributeType(attributeName, attributeTypeFromString(attribute.getType()));
                if (attribute.getMaxLevelToUse() != null) {
                    data.getDefinition().setMaximumGeneralization(attributeName, attribute.getMaxLevelToUse());
                }
                if (attribute.getMinLevelToUse() != null) {
                    data.getDefinition().setMinimumGeneralization(attributeName, attribute.getMinLevelToUse());
                }
                if (attribute.isUseMicroAggregation()) {
                    setMicroAggregationFunction(data, attribute);
                }
            }
        }
        return data;
    }

    /**
     * Sets the MicroAggregationFunction for attribute in definition of data. Information about what function to use is
     * found in attribute
     * @param data Data with definition that will be changed
     * @param attribute AttributeConfig about attribute to be changed
     */
    private static void setMicroAggregationFunction(Data data, AttributeConfig attribute) {
        switch (attribute.getMicroAggregationFunction()) {
            case GEOMETRIC_MEAN:
                data.getDefinition().setMicroAggregationFunction(attribute.getName(), AttributeType.MicroAggregationFunction.createGeometricMean(attribute.isIgnoreMissingData()), attribute.isPerformClustering());
                break;
            case ARITHMETIC_MEAN:
                data.getDefinition().setMicroAggregationFunction(attribute.getName(), AttributeType.MicroAggregationFunction.createArithmeticMean(attribute.isIgnoreMissingData()), attribute.isPerformClustering());
                break;
            case MEDIAN:
                data.getDefinition().setMicroAggregationFunction(attribute.getName(), AttributeType.MicroAggregationFunction.createMedian(attribute.isIgnoreMissingData()), attribute.isPerformClustering());
                break;
            case INTERVAL:
                data.getDefinition().setMicroAggregationFunction(attribute.getName(), AttributeType.MicroAggregationFunction.createInterval(attribute.isIgnoreMissingData()), attribute.isPerformClustering());
                break;
            case SET:
                data.getDefinition().setMicroAggregationFunction(attribute.getName(), AttributeType.MicroAggregationFunction.createSet(attribute.isIgnoreMissingData()), attribute.isPerformClustering());
                break;
            case MODE:
                data.getDefinition().setMicroAggregationFunction(attribute.getName(), AttributeType.MicroAggregationFunction.createMode(attribute.isIgnoreMissingData()), attribute.isPerformClustering());
                break;
        }
    }

    private static AttributeType attributeTypeFromString(String type) {
        switch (type) {
            case "QUASI_IDENTIFYING_ATTRIBUTE":
                return AttributeType.QUASI_IDENTIFYING_ATTRIBUTE;
            case "SENSITIVE_ATTRIBUTE":
                return AttributeType.SENSITIVE_ATTRIBUTE;
            case "INSENSITIVE_ATTRIBUTE":
                return AttributeType.INSENSITIVE_ATTRIBUTE;
            case "IDENTIFYING_ATTRIBUTE":
                return AttributeType.IDENTIFYING_ATTRIBUTE;
            default:
                throw new RuntimeException("Invalid Attribute type defined");
        }
    }

    public static List<AttributeConfig> getEnrichedAttributeConfigs(DataConfig dataConfig) throws IOException {
        List<AttributeConfig> attributeConfigs = dataConfig.getAttributeConfigs();
        Data data = DataLoader.getData(dataConfig);
        checkIfContinuousVariablesContainEmptyValues(attributeConfigs, data);
        Map<String, StatisticsSummary<?>> summaryStatistics = data.getHandle().getStatistics().getSummaryStatistics(false);
        for (AttributeConfig attributeConfig : attributeConfigs) {
            String attribute = attributeConfig.getName();
            StatisticsSummary<?> summary = summaryStatistics.get(attribute);
            // TODO: We could add checks if for existing config, data and config match min and max values and possible categorical values
            if ("categorical".equalsIgnoreCase(attributeConfig.getDataType())) {
                if (attributeConfig.getPossibleEntries() == null) {
                    int column = data.getHandle().getColumnIndexOf(attribute);
                    String[] distinctValues = data.getHandle().getStatistics().getDistinctValues(column);
                    Set<String> uniqueValues = new HashSet<>(Arrays.asList(distinctValues));
                    String[][] hierarchy = data.getDefinition().getHierarchy(attribute);
                    if (hierarchy != null) {
                        for (String[] row : hierarchy) {
                            uniqueValues.addAll(Arrays.asList(row));
                        }
                    }
                    String[] uniqueArray = new String[uniqueValues.size()];
                    attributeConfig.setPossibleEntries(uniqueValues.toArray(uniqueArray));
                }
            } else if ("continuous".equalsIgnoreCase(attributeConfig.getDataType()) || "date".equalsIgnoreCase(attributeConfig.getDataType())) {
                if (attributeConfig.getMin() == null) {
                    attributeConfig.setMin(summary.getMinAsValue());
                }
                if (attributeConfig.getMax() == null) {
                    attributeConfig.setMax(summary.getMaxAsValue());
                }
            } else {
                throw new RuntimeException("Data type not supported.");
            }
        }
        return attributeConfigs;
    }

    private static void checkIfContinuousVariablesContainEmptyValues(List<AttributeConfig> attributeConfigs, Data data) {
        for (AttributeConfig attributeConfig : attributeConfigs) {
            String attribute = attributeConfig.getName();
            if ("continuous".equalsIgnoreCase(attributeConfig.getDataType())) {
                int column = data.getHandle().getColumnIndexOf(attribute);
                for (int i = 0; i < data.getHandle().getNumRows(); i++) {
                    String value = data.getHandle().getValue(i, column);
                    if (value.isEmpty()) {
                        throw new RuntimeException("Continuous variables can not have empty String. See row " + (i + 1) +
                                " for attribute " + attribute + ". Possible fix is to replace empty values with NULL.");
                    }
                }
            }
        }
    }
}
