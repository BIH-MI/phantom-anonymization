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

package org.bihmi.anonymization;


import lombok.extern.slf4j.Slf4j;

import org.bihmi.anonymization.anon.AnonymizationResults;
import org.bihmi.anonymization.config.AnonymizationConfig;
import org.bihmi.anonymization.config.ConfigReader;
import org.bihmi.anonymization.config.DataConfig;
import org.bihmi.anonymization.config.ReportConfig;
import org.bihmi.anonymization.config.experiment.AnonymizationExperimentConfig;
import org.bihmi.anonymization.config.experiment.CombinationRunConfig;
import org.bihmi.anonymization.report.Report;
import org.deidentifier.arx.DataHandle;

import java.io.IOException;
import java.util.List;

@Slf4j
public class AnonymizationExperimentMain {

    static Report report;

    /**
     * Running anonymization experiments with experiment config.
     * @param args can include [pathToExperimentConfig, pathToReportConfig]
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        String experimentConfigPath;
        String reportConfigPath;
        if (args.length == 2) {
            experimentConfigPath = args[0];
            reportConfigPath = args[1];
        } else {
            experimentConfigPath = "configs/texasFromAttributeConfig/experiment_config.yml";
            reportConfigPath = "configs/texasFromAttributeConfig/report_config.yml";
        }
        log.info("Running with: experimentConfigPath = " + experimentConfigPath + "; reportConfigPath = " + reportConfigPath);

        ConfigReader configReader = new ConfigReader();
        AnonymizationExperimentConfig experimentConfig = configReader.readAnonymizationExperimentConfig(experimentConfigPath);

        ReportConfig reportConfig = configReader.readReportConfig(reportConfigPath);
        report = new Report(reportConfig);

        // Start all combinations one after another
        if (experimentConfig.getCombinationRunConfigs() != null) {
            runCombinationConfigExperiments(experimentConfig.getCombinationRunConfigs());
        }

        report.closeReport();
    }

    /**
     * Runs each experiment in configs separately.
     * @param configs combinationRunConfigs
     * @throws IOException
     */
    protected static void runCombinationConfigExperiments(List<CombinationRunConfig> configs) throws IOException {
        for (CombinationRunConfig combinationRunConfig : configs) {
            for (String pathToDataConfig : combinationRunConfig.getPathsToDataConfig()) {
                for (String pathToAnonymizationConfig : combinationRunConfig.getPathsToAnonymizationConfig()) {
                    runExperiment(pathToAnonymizationConfig, pathToDataConfig);
                }
            }
        }
    }

    /**
     * Starts a single anonymization experiment.
     * @param pathToAnonymizationConfig
     * @param pathToDataConfig
     * @throws IOException
     */
    private static void runExperiment(String pathToAnonymizationConfig, String pathToDataConfig) throws IOException {
        ConfigReader configReader = new ConfigReader();
        DataConfig dataConfig = configReader.readDataConfig(pathToDataConfig);
        AnonymizationConfig anonymizationConfig = configReader.readAnonymizationConfig(pathToAnonymizationConfig);
        DataHandle output = AnonymizationExperiment.anonymize(anonymizationConfig, dataConfig);
        AnonymizationResults results = new AnonymizationResults(output);
        report.writeExperimentToReport(results, anonymizationConfig, dataConfig);
    }
}
