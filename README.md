# phantom-anonymization
Phantom Anonymization is a framework for quantifying residual membership inference risks in anonymized datasets. The framework enables the evaluation of a wide range of privacy models, transformation methods, and attack scenarios (considering an adversary's background knowledge) in terms of both residual privacy risks and the remaining utility of the data.

## Requirements and Setup
### Maven
[Maven](https://maven.apache.org/install.html) needs to be installed!

Since ARX is currently included as jar file in the project, it needs to be manually install into the local Maven repository:

On Windows run following command in cmd.exe in the root folder of this project:

`mvn install:install-file -Dfile=lib/libarx-3.9.1.jar -DgroupId=org.deidentifier.arx -DartifactId=arx -Dversion=3.9.1 -Dpackaging=jar
`
### Lombok
We use lombok https://projectlombok.org/

Install with Eclipse:
https://projectlombok.org/setup/eclipse

Install with IntelliJ:
https://projectlombok.org/setup/intellij

### Building a Jar file
Navigate to the folder and run following commands:
1. If not already happened during Maven setup, run:
`mvn install:install-file -Dfile=lib/libarx-3.9.1.jar -DgroupId=org.deidentifier.arx -DartifactId=arx -Dversion=3.9.1 -Dpackaging=jar`
2. Run: `mvn install -DskipTests` within the project folder
3. Jar can now be found at `target/phantomanonymization-1.0-SNAPSHOT.jar`

## Using the project

### CLI parameters

```bash
java -jar [file].jar
    --riskAssessment         Risk assessment mode. If chosen, the
                             following options must be present as well:
                             riskAssessmentConfig, dataConfig,
                             anonymizationConfig, name
    --riskAssessmentSeries   Risk assessment series mode: If chosen, the
                             following options must be present as well:
                             seriesConfig
    --targetSelection        Target selection mode: If chosen, the
                             following options must be present as well:
                             dataConfig
```
Examples of how to use the cli are provded as bash scripts.

The result files are saved into the results/ folder.

### Configuration files
#### Specification of risk assessment configuration

| Parameter                 | Type              | Default   | Values                                   | Description                                                |
|---------------------------|-------------------|-----------|------------------------------------------|------------------------------------------------------------|
| `name`                    | `string`          | "Unnamed" |                                          | Name of the configuration.                                 |
| `attributesForAttack`     | `string`          |           | `ALL_QIS`, `ALL_ATTRIBUTES`              | Attributes used in the risk assessment.                    |
| `featureTypes`            | `list` of `string`|           | `CORRELATION`, `ENSEMBLE`, `HISTOGRAM`, `NAIVE` | Feature types to be considered.                     |
| `classifierType`          | `string`          |           | `KNN`, `LR`, `RF`                        | Classifier type to use.                                    |
| `targetCount`             | `integer`         |           |                                          | Number of targets.                                         |
| `targetType`              | `string`          |           | `RANDOM`, `OUTLIER`, `AVERAGE`, `IMPORT` | Type of targets used.                                      |
| `targetImportFile`        | `string`          |           |                                          | File path to import targets from.                          |
| `runCount`                | `integer`         |           |                                          | Number of runs used to evaluate risk assessment.           |
| `runTrainingCount`        | `integer`         |           |                                          | Number of trainings per run to train classifier.           |
| `runTestCount`            | `integer`         |           |                                          | Number of tests per run to test classifier.                |
| `sizeSampleTraining`      | `integer`         |           |                                          | Sample size to be used in trainings.                       |
| `sizeSampleTest`          | `integer`         |           |                                          | Sample size to be used in testing.                         |
| `sizeBackground`          | `integer`         |           |                                          | Background size to be used in risk assessment.             |
| `sizeCohort`              | `integer`         |           |                                          | Cohort size to be used in risk assessment.                 |
| `sizeBackgroundFraction`  | `double`          |           |                                          | Background size (relative to population).                  |
| `sizeCohortFraction`      | `double`          |           |                                          | Cohort size (relative to population).                      |
| `overlap`                 | `double`          |           |                                          | Fraction of records from the cohort in the background.     |
| `threadCount`             | `integer`         | 32        |                                          | Number of processing threads used in parallel.             |
| `useCheckpointData`       | `boolean`         | `false`   |                                          | When true, use stored checkpoint data to run experiments.  |
| `pathToCheckpointData`    | `string`          |           |                                          | Path to base folder of stored checkpoint data.             |

#### Specification of series configuration

| Parameter                   | Type                | Default          | Description                                 |
|-----------------------------|---------------------|------------------|---------------------------------------------|
| `name`                      | `string`            | "Unnamed-Series" | Name of the experiment series.              |
| `combinationConfig`         | `list` of `objects` |                  | List of combination run configurations.     |
|   ↳ `pathsToAnonymizationConfig` | `list` of `string` |                  | Paths to the anonymization configurations.  |
|   ↳ `pathsToDataConfig`           | `list` of `string` |                  | Paths to the data configurations.           |
|   ↳ `pathsToRiskAssessmentConfig` | `list` of `string` |                  | Paths to the risk assessment configurations.|

#### Specification of anonymization configuration

| Parameter                        | Type                  | Default                          | Values                                                                 | Description                                                   |
|----------------------------------|-----------------------|----------------------------------|------------------------------------------------------------------------|---------------------------------------------------------------|
| `name`                           | `string`              |                        |                                                                        | Name of the anonymization configuration.                      |
| `privacyModelList`               | `list` of `PrivacyModel` |                                | `k-Anonymity`, `DistinctLDiversity`, `EntropyLDiversity`, `HierarchicalDistanceTCloseness`, `EqualDistanceTCloseness`, `DDisclosurePrivacy`, `EnhancedBLikeness`, `EDDifferentialPrivacy`, `AverageReidentificationRisk`, `PopulationUniqueness` | List of privacy models used for anonymization. For parametrization of each model please see example configs.              |
| `suppressionLimit`               | `double`              | `1.0`                            |                                                                        | Suppression limit.                                            |
| `anonymizationAlgorithm`         | `string`              | `OPTIMAL`                        | `OPTIMAL`, `HEURISTIC`, `EXACT`                                       | Algorithm used for anonymization.                            |
| `heuristicSearchTimeLimit`       | `integer`             | `Integer.MAX_VALUE`              |                                                                        | Time limit for heuristic search (if applicable).             |
| `heuristicSearchStepLimit`       | `integer`             | `Integer.MAX_VALUE`              |                                                                        | Step limit for heuristic search (if applicable).             |
| `differentialPrivacySearchBudget` | `double`             | `0.1`                            |                                                                        | Search budget for differential privacy algorithms.           |
| `qualityModel`                   | `QualityModelConfig`  |                                    |                                                                        |       |
| ↳ `qualityModelType`         | `enum`            | `LOSS_METRIC`  |   `LOSS_METRIC`                                           | QualityModel chosen             |
| ↳ `gsFactor`         | `double`            | `0.5`  |                                          | Default is 0.5. A factor of 0 will favor suppression, and a factor of 1 will favor generalization.           |
| `localGeneralization`            | `boolean`             | `false`                           | `true`, `false`                                                       | Whether local generalization is used.                        |
| `localGeneralizationIterations`  | `integer`             | `100`                            |                                                                        | Number of iterations performed for local generalization.     |

#### Specification of dataset configuration

| Parameter             | Type              | Default       | Values                                       | Description                                              |
|-----------------------|------------------|---------------|----------------------------------------------|----------------------------------------------------------|
| `dataSetName`        | `string`          | "Unnamed"      |                                              | Name of the dataset configuration.                       |
| `dataCsvFile`        | `string`          |               |                                              | Path to the CSV file containing the dataset.             |
| `attributeConfigs`   | `list` of `attributeConfigs` |               |                                              | List of attribute configurations.                        |
| ↳ `name`               | `string`            |               |                                              | Name of the attribute.                                   |
| ↳ `dataType`           | `string`            |               | `categorical`, `continuous`      | Data type of the attribute.                             |
| ↳ `type`               | `string`            |               | `IDENTIFIER`, `QUASI_IDENTIFIER`, `SENSITIVE`, `INSENSITIVE` | Type of the attribute.                                  |
| ↳ `dateFormat`         | `string`            | `dd.MM.yyyy`  |                                              | Format for date attributes, if applicable.             |
| ↳ `possibleEntries`    | `list` of `string`  |               |                                              | List of possible values for categorical attributes.     |
| ↳ `include`            | `boolean`           | `true`        | `true`, `false`                               | Whether to include this attribute in processing.        |
| ↳ `min`               | `object`            |               |                                              | Minimum value of the attribute.                         |
| ↳ `max`               | `object`            |               |                                              | Maximum value of the attribute.                         |
| ↳ `minLevelToUse`      | `integer`           |               |                                              | Minimum level from hierarchy to use.                    |
| ↳ `maxLevelToUse`      | `integer`           |               |                                              | Maximum level from hierarchy to use.                    |
| ↳ `pathToHierarchy`    | `string`            |               |                                              | Path to a file containing the hierarchy.                |
| ↳ `useMicroAggregation`| `boolean`           | `false`       | `true`, `false`                               | Whether to use microaggregation for this attribute.     |
| ↳ `microAggregationFunction` | `string`      | `ARITHMETIC_MEAN` | `ARITHMETIC_MEAN`, `GEOMETRIC_MEAN`, `MEDIAN` | Function used for microaggregation.                   |
| ↳ `performClustering`  | `boolean`           | `true`        | `true`, `false`                               | Whether clustering is performed in microaggregation.   |
| ↳ `ignoreMissingData`  | `boolean`           | `true`        | `true`, `false`                               | Whether missing data should be ignored.                |

## Example configs

The example folder contains examples for each of the configs, i.e. anonymizationConfig(s), dataConfig(s), riskAssessmentConfig(s), seriesConfig(s)

## Experiment configs

The experiment_configs folder contains all the configurations used to perform the experiments described in the paper "Phantom Anonymization: Adversarial testing for membership inference risks in anonymized health data".

## Data
Contains the dataset and hierarchies used for the example as well as for the experiments.