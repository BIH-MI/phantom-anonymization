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

import org.bihmi.anonymization.config.AttributeConfig;
import org.deidentifier.arx.DataHandle;
import org.deidentifier.arx.DataType;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ensemble feature
 */
public class FeatureEnsemble extends Feature {

    /** Feature */
    private final FeatureCorrelation correlation;
    /** Feature */
    private final FeatureHistogram histogram;
    /** Feature */
    private final FeatureNaive naive;

    /**
     * Creates a new instance
     * @param handle data to extract features from
     * @param attributesToConsider attributes to consider for feature extraction
     * @param attributeConfigs configuration of attributes in handle
     * @param dictionary dictionary with mappings for categorical attributes
     * @param dataTypes Map used to ensure consistency of data types
     * @param numBins Number of bins to for continuous variables for Histogram feature extraction
     */
    public FeatureEnsemble(DataHandle handle, Set<String> attributesToConsider, List<AttributeConfig> attributeConfigs, Dictionary dictionary, Map<String, DataType<?>> dataTypes, int numBins) {
        naive = new FeatureNaive(handle, attributesToConsider, attributeConfigs, dictionary, dataTypes);
        histogram = new FeatureHistogram(handle, attributesToConsider, attributeConfigs, dictionary, dataTypes, numBins);
        correlation = new FeatureCorrelation(handle, attributesToConsider, attributeConfigs, dictionary);
    }

    @Override
    public double[] compile() {
        double[] _naive = naive.compile();
        double[] _histogram = histogram.compile();
        double[] _correlation = correlation.compile();
        return getFlattenedArray(_naive, _histogram, _correlation);
    }
}

