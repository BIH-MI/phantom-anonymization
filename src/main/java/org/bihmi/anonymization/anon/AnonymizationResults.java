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

package org.bihmi.anonymization.anon;

import lombok.Getter;
import org.deidentifier.arx.DataHandle;

import java.util.*;

/**
 * Class that retrieves and stores statistics of the anonymization.
 */
@Getter
public class AnonymizationResults {

    HashMap<String, Double> datasetPrivacyMetrics;
    // HashMap<String, Float> datasetUtilityMetrics;

    HashMap<String, HashMap<String, Double>> attributePrivacyMetrics; // TODO (KO): derzeitige annahme dass alle attribute die gleichen metriken haben
    // HashMap<String, HashMap<String, Float>> attributeUtilityMetrics;


    // Set<String> attributes;

    public AnonymizationResults(DataHandle handle){
        this.datasetPrivacyMetrics = fillDatasetPrivacyMetrics(handle);
        this.attributePrivacyMetrics = fillAttributePrivacyMetrics(handle);
    }

    /**
     * Retrieves attribute statistics from handle and saves them.
     * @param handle anonymized data
     */
    private HashMap<String, HashMap<String, Double>> fillAttributePrivacyMetrics(DataHandle handle) {
        attributePrivacyMetrics = new HashMap<>();

        Set<String> qids = handle.getDefinition().getQuasiIdentifyingAttributes();
        for (String qid : qids) {
            HashMap<String, Double> singleAttributePrivacyMetrics = new HashMap<>();

            singleAttributePrivacyMetrics.put("Granularity",
                    handle.getStatistics().getQualityStatistics().getGranularity().getValue(qid));
            singleAttributePrivacyMetrics.put("NonUniformEntropy",
                    handle.getStatistics().getQualityStatistics().getNonUniformEntropy().getValue(qid));

            attributePrivacyMetrics.put(qid, singleAttributePrivacyMetrics);
        }
        return attributePrivacyMetrics;
    }

    /**
     * Retrieves dataset statistics from handle and saves them.
     * @param handle anonymized data
     */
    private HashMap<String, Double> fillDatasetPrivacyMetrics(DataHandle handle) {
        datasetPrivacyMetrics = new HashMap<>();

        datasetPrivacyMetrics.put("JournalistRisk",
                handle.getRiskEstimator().getSampleBasedReidentificationRisk().getEstimatedJournalistRisk());

        datasetPrivacyMetrics.put("MinimumRisk",
                handle.getRiskEstimator().getSampleBasedReidentificationRisk().getLowestRisk());

        datasetPrivacyMetrics.put("RecordsAffectedByLowestRisk",
                handle.getRiskEstimator().getSampleBasedReidentificationRisk().getNumRecordsAffectedByLowestRisk());

        datasetPrivacyMetrics.put("HighestRisk",
                handle.getRiskEstimator().getSampleBasedReidentificationRisk().getHighestRisk());

        datasetPrivacyMetrics.put("RecordsAffectedByHighestRisk",
                handle.getRiskEstimator().getSampleBasedReidentificationRisk().getNumRecordsAffectedByHighestRisk());

        datasetPrivacyMetrics.put("AverageRisk",
                handle.getRiskEstimator().getSampleBasedReidentificationRisk().getAverageRisk());

        datasetPrivacyMetrics.put("MarketerRisk",
                handle.getRiskEstimator().getSampleBasedReidentificationRisk().getEstimatedMarketerRisk());

        datasetPrivacyMetrics.put("MaxClassSize",
                (double) handle.getStatistics().getEquivalenceClassStatistics().getMaximalEquivalenceClassSize());

        datasetPrivacyMetrics.put("AverageClassSize",
                handle.getStatistics().getEquivalenceClassStatistics().getAverageEquivalenceClassSize());

        datasetPrivacyMetrics.put("MinClassSize",
                (double) handle.getStatistics().getEquivalenceClassStatistics().getMinimalEquivalenceClassSize());

        datasetPrivacyMetrics.put("SuppressedRecords",
                (double) handle.getStatistics().getEquivalenceClassStatistics().getNumberOfSuppressedRecords());

        datasetPrivacyMetrics.put("Granularity",
                handle.getStatistics().getQualityStatistics().getGranularity().getArithmeticMean());

        datasetPrivacyMetrics.put("Discernibility",
                handle.getStatistics().getQualityStatistics().getDiscernibility().getValue());

        datasetPrivacyMetrics.put("Entropy",
                handle.getStatistics().getQualityStatistics().getNonUniformEntropy().getArithmeticMean());
        return datasetPrivacyMetrics;
    }

    public List<String> getDatasetPrivacyHeader(){
        return new ArrayList<>(datasetPrivacyMetrics.keySet());
    }

    public List<Double> getDatasetPrivacyMetricValues(){
        return new ArrayList<>(datasetPrivacyMetrics.values());
    }

    public List<Double> getAttributePrivacyMetricValues(String attributeName, List<String> metricNames){
        List<Double> returnValue = new ArrayList<>();
        for (String key: metricNames) {
            returnValue.add(attributePrivacyMetrics.get(attributeName).get(key));
        }
        return returnValue;
    }

    public List<Double> getDatasetPrivacyMetricValues(List<String> metricNames){
        List<Double> returnValue = new ArrayList<>();
        for (String key: metricNames) {
            returnValue.add(datasetPrivacyMetrics.get(key));
        }
        return returnValue;
    }

    // Note: derzeitige annahme dass alle attribute die gleichen metriken haben
    public List<String> getAttributePrivacyHeader() {
        HashMap<String, Double> attributeMetric = attributePrivacyMetrics.values().stream().findAny().get();
        return new ArrayList<>(attributeMetric.keySet());
    }
}
