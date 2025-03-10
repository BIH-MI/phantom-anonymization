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
import org.deidentifier.arx.aggregates.StatisticsFrequencyDistribution;

import java.util.*;

/**
 * Histogram feature
 */
public class FeatureHistogram extends Feature {

    /** Features, LinkedHashMap so order from attributeConfig is preserved */
    private final LinkedHashMap<String, StatisticsFrequencyDistribution> categorical = new LinkedHashMap<>();
    /** Features, LinkedHashMap so order from attributeConfig is preserved */
    private final LinkedHashMap<String, double[]>                        numeric     = new LinkedHashMap<>();
    /** Dictionary */
    private final Dictionary                                             dictionary;

    /**
     * Creates a new cinstance.
     * For categorical attributes this simply refers to the counts of distinct values.
     * For continuous and ordinal attributes, the domain of the values is separated into 10 bins.
     */
    public FeatureHistogram(DataHandle handle, Set<String> attributesToConsider, List<AttributeConfig> attributeConfigs, Dictionary dictionary, Map<String, DataType<?>> dataTypes, int numBins) {
        this.dictionary = dictionary;
        // For each attribute
        for (AttributeConfig attributeConfig : attributeConfigs) {
            if (!attributeConfig.getInclude() || !attributesToConsider.contains(attributeConfig.getName())) {
                continue;
            }

            String attribute = attributeConfig.getName();
            // Obtain attribute details
            int column = handle.getColumnIndexOf(attribute);
            DataType<?> _type = handle.getDefinition().getDataType(attribute);
            Class<?> _clazz = _type.getDescription().getWrappedClass();
            checkDataType(attribute, _type, dataTypes);

            // Put numerical attributes into bins
            if (_clazz.equals(Long.class) || _clazz.equals(Double.class) || _clazz.equals(Date.class)) {
                double min;
                double max;
                if (_clazz.equals(Date.class)) {
                    min = ((Date) attributeConfig.getMin()).getTime();
                    max = ((Date) attributeConfig.getMax()).getTime();
                } else {
                    min = ((Number) attributeConfig.getMin()).doubleValue();
                    max = ((Number) attributeConfig.getMax()).doubleValue();
                }
                if (Math.abs(max - min) < 0.000001) {
                    throw new RuntimeException("Max and min equal");
                }
                
                double binSize = (max - min) / numBins;
                double[] freqs = new double[numBins];

                // For each value
                for (int row = 0; row < handle.getNumRows(); row++) {

                    // Check is row suppressed
                    if (!handle.isSuppressed(row)) {

                        // Parse value
                        double value = getDouble(handle, row, column, _clazz);

                        // Calculate bin
                        int bin = (int) ((value - min) / binSize);

                        // Can happen if hierarchy contains values smaller than the values contained in the dataset itself
                        if (bin < 0) {
                            bin = 0;
                        }
                        
                        // Can happen if hierarchy contains values bigger than the values contained in the dataset itself
                        if (bin >= numBins) {
                            bin = numBins - 1;
                        }

                        // Increment frequency of bin
                        freqs[bin] += 1d;
                    }
                }

                // Store
                numeric.put(attribute, freqs);

                // Frequency distribution for categorical attributes
            } else if (_clazz.equals(String.class)) {
                StatisticsFrequencyDistribution frequencyDistribution = handle.getStatistics().getFrequencyDistribution(column);
                categorical.put(attribute, frequencyDistribution);
                for (String value : frequencyDistribution.values) {
                    dictionary.probe(attribute, value);
                }
            }
        }
    }

    @Override
    public double[] compile() {
        // Prepare
        List<double[]> features = new ArrayList<>();

        for (String attribute : numeric.keySet()) {
            features.add(numeric.get(attribute));
        }
        for (String attribute : categorical.keySet()) {
            StatisticsFrequencyDistribution distribution = categorical.get(attribute);
            double[] feature = new double[dictionary.size(attribute)];
            for (int i = 0; i < distribution.values.length; i++) {
                String value = distribution.values[i];
                double count = distribution.frequency[i] * distribution.count;
                int code = dictionary.probe(attribute, value);
                feature[code] = count;
            }
            features.add(feature);
        }
        // Done
        return getFlattenedArray(features.toArray(new double[features.size()][]));
    }
}