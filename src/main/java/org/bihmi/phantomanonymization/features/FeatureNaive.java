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

package org.bihmi.phantomanonymization.features;

import org.bihmi.privacy.mgmt.anonymization.config.AttributeConfig;
import org.deidentifier.arx.DataHandle;
import org.deidentifier.arx.DataType;
import org.deidentifier.arx.aggregates.StatisticsSummary;

import java.util.*;

/**
 * Naive feature
 */
public class FeatureNaive extends Feature {

    // Features
    private final double[] features;

    /**
     * Creates a new instance
     * Creates a flattened vector with mean, median and var for each numerical attribute and least frequent element, most
     * frequent element and number of unique elements for each categorical attributes.
     * Categorical attribute String values are mapped to integer codes based on mapping in Dictionary. Dictionary can be
     * empty and codes will then be generated on the fly.
     * @param handle data to extract features from
     * @param attributesToConsider attributes to consider for feature extraction
     * @param attributeConfigs configuration of attributes in handle
     * @param dictionary dictionary with mappings for categorical attributes
     * @param dataTypes Map used to ensure consistency of data types
     */
    @SuppressWarnings("unchecked")
    public FeatureNaive(DataHandle handle, Set<String> attributesToConsider, List<AttributeConfig> attributeConfigs, Dictionary dictionary, Map<String, DataType<?>> dataTypes) {

        // Prepare
        int countAttributesToConsider = 0;

        // Calculate statistics
        Map<String, StatisticsSummary<?>> statistics = handle.getStatistics().getSummaryStatistics(false);

        Set<String> attributesIncludes = new HashSet<>();
        for (AttributeConfig attributeConfig: attributeConfigs) {
            if (attributeConfig.getInclude()) {
                attributesIncludes.add(attributeConfig.getName());
                if (attributesToConsider.contains(attributeConfig.getName())) {
                    countAttributesToConsider++;
                }
            }
        }

        features = new double[countAttributesToConsider * 3];

        // For each attribute
        int index = 0;
        String[] attributes = attributesToConsider.toArray(new String[0]);
        Arrays.sort(attributes);
        for (String attribute : attributes) {
            
            // Check
            if (!attributesIncludes.contains(attribute)) {
                continue;
            }
            
            // Index
            int column = handle.getColumnIndexOf(attribute);

            // Obtain statistics
            StatisticsSummary<?> summary = statistics.get(attribute);
            DataType<?> _type = handle.getDefinition().getDataType(attribute);
            Class<?> _clazz = _type.getDescription().getWrappedClass();
            checkDataType(attribute, _type, dataTypes);

            // Parameters to calculate
            Double mostFreq = null;
            Double leastFreq = null;
            Double uniqueElements = null;
            Double mean = null;
            Double median = null;
            Double var = null;

            // Calculate depending on data type
            if (_clazz.equals(Long.class)) {

                // Handle data type represented as long
                DataType<Long> type = (DataType<Long>)_type;
                mean = summary.getArithmeticMeanAsDouble();
                var = summary.getSampleVarianceAsDouble();
                Long _median = type.parse(summary.getMedianAsString());
                median = _median != null ? _median.doubleValue() : 0d; // TODO: how to handle null here

            } else if (_clazz.equals(Double.class)) {

                // Handle data type represented as double
                DataType<Double> type = (DataType<Double>)_type;
                mean = summary.getArithmeticMeanAsDouble();
                var = summary.getSampleVarianceAsDouble();
                Double _median = type.parse(summary.getMedianAsString());
                median = _median != null ? _median : 0d; // TODO: how to handle null here

            } else if (_clazz.equals(Date.class)) {

                // Handle data type represented as date
                DataType<Date> type = (DataType<Date>)_type;
                mean = summary.getArithmeticMeanAsDouble();
                var = summary.getSampleVarianceAsDouble();
                Date _median = type.parse(summary.getMedianAsString());
                median = _median != null ? _median.getTime() : 0d; // TODO: how to handle null here

            } else if (_clazz.equals(String.class)) {

                // Count frequencies of values
                Map<String, Integer> map = new HashMap<>();
                for (int row = 0; row < handle.getNumRows(); row++) {
                    String value = handle.getValue(row, column);
                    Integer count = map.get(value);
                    if (count == null) {
                        count = 1;
                    } else {
                        count++;
                    }
                    map.put(value, count);
                }

                // Determine codes with highest and lowest frequencies
                int minFreq = Integer.MAX_VALUE;
                int maxFreq = Integer.MIN_VALUE;

                // Find most and least frequent
                for (Map.Entry<String, Integer> entry : map.entrySet()) {
                    String value = entry.getKey();
                    Integer count = entry.getValue();
                    double code = dictionary.probe(attribute, value);
                    if (count < minFreq) {
                        minFreq = count;
                        leastFreq = code;
                    }
                    if (count > maxFreq) {
                        maxFreq = count;
                        mostFreq = code;
                    }
                }

                // Get number of assigned keys
                uniqueElements = (double) map.size();


            } else {
                throw new IllegalStateException("Unknown data type");
            }

            // Switch feature type
            if (mean != null && var != null && median != null) {
                features[index] = mean;
                features[index + 1] = median;
                features[index + 2] = var;
            } else if (mostFreq != null && leastFreq != null && uniqueElements != null) {
                features[index] = leastFreq;
                features[index + 1] = mostFreq;
                features[index + 2] = uniqueElements;
            } else {
                throw new IllegalStateException("Features unavailable");
            }
            // Increment feature index
            index += 3;
        }
    }

    @Override
    public double[] compile() {
        return features;
    }
}
