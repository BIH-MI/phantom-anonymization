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

import org.bihmi.anonymization.anon.models.PrivacyModel;
import org.bihmi.anonymization.config.AnonymizationConfig;
import org.bihmi.anonymization.config.QualityModelConfig;
import org.deidentifier.arx.*;
import org.deidentifier.arx.criteria.*;
import org.deidentifier.arx.exceptions.RollbackRequiredException;
import org.deidentifier.arx.metric.Metric;
import org.deidentifier.arx.metric.Metric.AggregateFunction;

import java.io.IOException;

/**
 * Contains anonymization methods.
 *
 * @author Fabian Prasser
 * @author Thierry Meurers
 */
public class AnonymizationMethods {

    /**
     * Interface for anonymization methods
     *
     * @author Fabian Prasser
     */
    public interface AnonymizationMethod {

        DataHandle anonymize(Data handle);
    }

    /***
     * Anonymous class to perform an anonymization that applies the anonymization defined in anonymizationConfig.
     */
    public static AnonymizationMethod CONFIG_ANONYMIZATION(AnonymizationConfig anonymizationConfig) {
        return new AnonymizationMethod() {

            /**
             * Weak anonymization with default suppression limit of 1d and k-anonymity with k=1.
             *
             * @param data raw data that should be anonymized.
             * @return data handle for the anonymized dataset.
             */
            @Override
            public DataHandle anonymize(Data data) {

                // Prepare
                ARXConfiguration config = ARXConfiguration.create();

                for (PrivacyModel privacyModel : anonymizationConfig.getPrivacyModelList()) {
                    for (PrivacyCriterion privacyCriterion : privacyModel.getPrivacyCriterion(data)) {
                        config.addPrivacyModel(privacyCriterion);
                    }
                }
                config.setAlgorithm(anonymizationConfig.getAnonymizationAlgorithm());
                config.setSuppressionLimit(anonymizationConfig.getSuppressionLimit());
                if (anonymizationConfig.getDifferentialPrivacySearchBudget() != null) {
                    config.setDPSearchBudget(anonymizationConfig.getDifferentialPrivacySearchBudget());
                }
                if (anonymizationConfig.getHeuristicSearchStepLimit() != null) {
                    config.setHeuristicSearchStepLimit(anonymizationConfig.getHeuristicSearchStepLimit());
                }
                if (anonymizationConfig.getHeuristicSearchTimeLimit() != null) {
                    config.setHeuristicSearchTimeLimit(anonymizationConfig.getHeuristicSearchTimeLimit());
                }
                setQualityModel(config, anonymizationConfig);

                // Anonymize
                ARXAnonymizer anonymizer = new ARXAnonymizer();
                try {
                    ARXResult result = anonymizer.anonymize(data, config);
                    DataHandle output = result.getOutput();
                    if (anonymizationConfig.isLocalGeneralization() && result.isResultAvailable()) {
                        try {
                            // Define relative number of records to be generalized in each iteration
                            double oMin = 1d/ (double) anonymizationConfig.getLocalGeneralizationIterations();
                            result.optimizeIterativeFast(output, oMin);
                        } catch (RollbackRequiredException e) {
                            e.printStackTrace();
                            throw new RuntimeException(e);
                        }
                    }
                    
                    return output;
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }

            /**
             * @return name of the anonymization method as defined in anonymizationConfig
             */
            @Override
            public String toString() {
                return anonymizationConfig.getName();
            }
        };

    }

    private static void setQualityModel(ARXConfiguration config, AnonymizationConfig anonymizationConfig) {
        QualityModelConfig qualityModel = anonymizationConfig.getQualityModel();
        if (qualityModel != null) {
            Double gsFactor = qualityModel.getGsFactor();
            Metric.AggregateFunction aggregateFunction = qualityModel.getAggregateFunction();
            QualityModelConfig.QualityModelType qualityModelType = qualityModel.getQualityModelType();
            if (aggregateFunction != null) {
                setQualityModel(config, qualityModelType, gsFactor, aggregateFunction);
            } else {
                setQualityModel(config, qualityModelType, gsFactor, AggregateFunction.GEOMETRIC_MEAN);
            }
        }
    }

    private static void setQualityModel(ARXConfiguration config, QualityModelConfig.QualityModelType qualityModelType, Double gsFactor, Metric.AggregateFunction aggregateFunction) {
        switch (qualityModelType) {
            case LOSS_METRIC:
                config.setQualityModel(Metric.createLossMetric(gsFactor, aggregateFunction));
                break;
            default:
                throw new RuntimeException("QualityModel not specified");
        }
    }
}
