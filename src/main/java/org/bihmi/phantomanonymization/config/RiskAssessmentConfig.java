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

package org.bihmi.phantomanonymization.config;

import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

import org.bihmi.phantomanonymization.features.FeatureType;
import org.bihmi.phantomanonymization.phantom.AttributesForAttackType;
import org.bihmi.phantomanonymization.phantom.ClassifierType;
import org.bihmi.phantomanonymization.target.TargetType;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class RiskAssessmentConfig {

    /** Name */
    private String                  name                = "Unnamed";

    /** Attributes used in the assessment */
    // TODO: This should go and be replaced by an explicit list of attributes to use
    private AttributesForAttackType attributesForAttack = AttributesForAttackType.ALL_QIS;

    /** Feature types */
    // TODO: Why can multiple feature types be specified, but only one classifier type?
    // TODO: Suggest to only support one feature type, if I'm not missing something
    private List<FeatureType>       featureTypes;

    /** Classifier type */
    private ClassifierType          classifierType;

    /** Number of targets */
    private int                     targetCount;

    /** Target type */
    private TargetType              targetType;

    /** File to import targets from */
    private String                  targetImportFile;

    /** Number of runs used to assess risks */
    private int                     runCount;

    /** Number of trainings per run to train the classifiers */
    private int                     runTrainingCount;

    /** Number of test per run to test the classifiers */
    private int                     runTestCount;

    /** Sample size (absolute) to be used for assessments */
    private int                     sizeSampleTraining;

    /** Sample size (absolute) to be used in assessments */
    private int                     sizeSampleTest;

    /** Background size (absolute) to be used in assessment. Can be overwritten by sizeBackgroundFraction. */
    private int                     sizeBackground;

    /** Cohort size (absolute) to be used for assessment. Can be overwritten by sizeCohortFraction. */
    private int                     sizeCohort;

    /** Background size (relative to population) to be used in assessments */
    private double                  sizeBackgroundFraction;

    /** Cohort size (relative to population) to be used for assessments */
    private double                  sizeCohortFraction;

    /** Fraction of records from the cohort contained in the background */
    private double                  overlap;

    /** Number of processing threads used in parallel to execute runs simultaneously */
    // TODO: Turn into CLI parameter?
    private int                     threadCount         = 32;

    /** When true use stored anonymized data at pathToExperiment to run experiments. If no data at path, generate experiment data */
    private Boolean                 useCheckpointData   = false;

    /** Path to base folder of stored checkpoint data for the current experiment */
    private String                  pathToCheckpointData;
    
    /** Paths to statistics config */
    private String                  pathToStatisticsConfig;
}

