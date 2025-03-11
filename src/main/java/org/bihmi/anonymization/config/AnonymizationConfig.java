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

package org.bihmi.anonymization.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import org.bihmi.anonymization.anon.models.PrivacyModel;
import org.deidentifier.arx.ARXConfiguration;

import java.util.Collection;

/**
 * Anonymization Config class to store a configuration of an ARX anonymization.
 */
@Getter
@Setter
@ToString
public class AnonymizationConfig {

    /** Name for the anonymization defined in the config */
    private String name;

    /** List of privacy models to be used for anonymization */
    private Collection<PrivacyModel> privacyModelList;

    /** Suppression limit */
    private double suppressionLimit = 1d;

    /** Which  ARXConfiguration.AnonymizationAlgorithm will be used for anonymization*/
    private ARXConfiguration.AnonymizationAlgorithm anonymizationAlgorithm = ARXConfiguration.AnonymizationAlgorithm.OPTIMAL;
    
    /** Parameter for heuristic algorithms in ARX. Only used for heuristic algorithms. */
    private Integer heuristicSearchTimeLimit = Integer.MAX_VALUE;

    /** Parameter for heuristic algorithms in ARX. Only used for heuristic algorithms. */
    private Integer heuristicSearchStepLimit = Integer.MAX_VALUE;

    /** Parameter for differential privacy algorithms in ARX */
    private Double differentialPrivacySearchBudget = 0.1;

    /** Config containing LossMetric and corresponding parameters */
    private QualityModelConfig qualityModel;

    /** True if local generalization is used */
    private boolean localGeneralization = false;
    
    /** Iteration performed for local generalization */
    private int localGeneralizationIterations = 100;
    
    
}