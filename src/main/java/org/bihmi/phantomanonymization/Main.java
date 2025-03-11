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

package org.bihmi.phantomanonymization;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.bihmi.anonymization.config.AnonymizationConfig;
import org.bihmi.anonymization.config.DataConfig;
import org.bihmi.anonymization.data.DataLoader;
import org.bihmi.phantomanonymization.config.CombinationConfig;
import org.bihmi.phantomanonymization.config.ConfigReader;
import org.bihmi.phantomanonymization.config.RiskAssessmentConfig;
import org.bihmi.phantomanonymization.config.SeriesConfig;
import org.bihmi.phantomanonymization.config.StatisticsConfig;
import org.bihmi.phantomanonymization.phantom.PhantomAnonymization;
import org.bihmi.phantomanonymization.target.TargetSelection;
import org.deidentifier.arx.Data;

import lombok.extern.slf4j.Slf4j;

/**
 * CLI Interface and main entry points
 */
@Slf4j
public class Main {
    
    /** Mode*/
    private static final Option MODE_RISK_ASSESSMENT = Option.builder().longOpt("riskAssessment")
            .desc("Risk assessment mode. If chosen, the following options must be present as well: riskAssessmentConfig, dataConfig, anonymizationConfig, name")
            .hasArg(false)
            .required(false)
            .build();
    /** Mode*/
    private static final Option MODE_RISK_ASSESSMENT_SERIES = Option.builder().longOpt("riskAssessmentSeries")
            .desc("Risk assessment series mode: If chosen, the following options must be present as well: seriesConfig")
            .hasArg(false)
            .required(false)
            .build();
    /** Mode*/
    private static final Option MODE_TARGET_SELECTION = Option.builder().longOpt("targetSelection")
            .desc("Target selection mode: If chosen, the following options must be present as well: dataConfig")
            .hasArg(false)
            .required(false)
            .build();
    
    /** Parameter */
    private static final Option PARAMETER_RISK_ASSESSMENT_CONFIG = Option.builder().longOpt("riskAssessmentConfig")
            .desc("Path to risk assessment configuration")
            .hasArg(true)
            .required(true)
            .build();
    /** Parameter */
    private static final Option PARAMETER_DATA_CONFIG = Option.builder().longOpt("dataConfig")
            .desc("Path to data configuration")
            .hasArg(true)
            .required(true)
            .build();
    /** Parameter */
    private static final Option PARAMETER_ANONYMIZATION_CONFIG = Option.builder().longOpt("anonymizationConfig")
            .desc("Path to anonymization configuration")
            .hasArg(true)
            .required(true)
            .build();
    /** Parameter */
    private static final Option PARAMETER_SERIES_CONFIG = Option.builder().longOpt("seriesConfig")
            .desc("Path to series configuration")
            .hasArg(true)
            .required(true)
            .build();
    /** Parameter */
    private static final Option PARAMETER_NAME = Option.builder().longOpt("name")
            .desc("Experiment name")
            .hasArg(true)
            .required(true)
            .build();
    
    /**
     * Calls Main chosen by MainOption and passes all other parameters to that Main.
     * 
     * @param args [MainOption, ...]
     * @throws IOException
     * @throws ParseException
     * @throws InterruptedException
     */
    public static void main(String[] args) throws IOException, ParseException, InterruptedException, URISyntaxException {

        // Prepare options
        Options options = new Options();
        options.addOption(MODE_RISK_ASSESSMENT);
        options.addOption(MODE_RISK_ASSESSMENT_SERIES);
        options.addOption(MODE_TARGET_SELECTION);

        // Check args
        if (args == null || args.length == 0) {
            help(options, "No parameters provided");
            return;
        }

        // Prepare parsing
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        
        // Parse
        try {
            cmd = parser.parse(options, args, true);
        } catch (Exception e) {
            help(options, e.getMessage());
            return;
        }

        // Run individual experiment
        if (cmd.hasOption(MODE_RISK_ASSESSMENT)) {
            
            // Parse again with specific options
            options = new Options();
            options.addOption(MODE_RISK_ASSESSMENT);
            options.addOption(PARAMETER_RISK_ASSESSMENT_CONFIG);
            options.addOption(PARAMETER_DATA_CONFIG);
            options.addOption(PARAMETER_ANONYMIZATION_CONFIG);
            options.addOption(PARAMETER_NAME);
            try {
                cmd = parser.parse(options, args, false);
            } catch (Exception e) {
                help(options, e.getMessage());
                return;
            }
            
            // Extract options
            String riskAssessmentConfigPath = cmd.getOptionValue(PARAMETER_RISK_ASSESSMENT_CONFIG);
            String dataConfigPath = cmd.getOptionValue(PARAMETER_DATA_CONFIG);
            String anonymizationConfigPath = cmd.getOptionValue(PARAMETER_ANONYMIZATION_CONFIG);
            String name = cmd.getOptionValue(PARAMETER_NAME);

            // Run risk assessment
            log.info("Starting risk assessment...");
            runRiskAssessment(riskAssessmentConfigPath, dataConfigPath, anonymizationConfigPath, name);
            
        // Run a risk assessment series
        } else if (cmd.hasOption(MODE_RISK_ASSESSMENT_SERIES)) {

            // Parse again with specific options
            options = new Options();
            options.addOption(MODE_RISK_ASSESSMENT_SERIES);
            options.addOption(PARAMETER_SERIES_CONFIG);
            try {
                cmd = parser.parse(options, args, false);
            } catch (Exception e) {
                help(options, e.getMessage());
                return;
            }
            
            // Extract options
            String experimentConfigPath = cmd.getOptionValue(PARAMETER_SERIES_CONFIG);
            
            // Run risk assessment series
            ConfigReader configReader = new ConfigReader();
            SeriesConfig seriesConfig = configReader.readExperimentConfig(experimentConfigPath);
            log.info("Running risk assessment series: '" + seriesConfig.getName() + "' (" + experimentConfigPath + ")");
            int numberOfAssessments = calculateSeriesLength(seriesConfig);
            int currentAssessment = 1;
            
            // Start all combinations one after another
            if (seriesConfig.getCombinationConfig() != null) {
                for (CombinationConfig combinationRunConfig : seriesConfig.getCombinationConfig()) {
                    for (String pathToPipelineConfig : combinationRunConfig.getPathsToRiskAssessmentConfig()) {
                        for (String pathToDataConfig : combinationRunConfig.getPathsToDataConfig()) {
                            for (String pathToAnonymizationConfig : combinationRunConfig.getPathsToAnonymizationConfig()) {
                                log.info("Running risk assessment (" + currentAssessment + "/" + numberOfAssessments + "):");
                                runRiskAssessment(pathToPipelineConfig, pathToDataConfig, pathToAnonymizationConfig, seriesConfig.getName());
                                currentAssessment++;
                            }
                        }
                    }
                }
            }
            
        // Target selection
        } else if (cmd.hasOption(MODE_TARGET_SELECTION)) {

            // Parse again with specific options
            options = new Options();
            options.addOption(MODE_TARGET_SELECTION);
            options.addOption(PARAMETER_DATA_CONFIG);
            try {
                cmd = parser.parse(options, args, false);
            } catch (Exception e) {
                help(options, e.getMessage());
                return;
            }
            
            // Extract options
            String dataConfigPath = cmd.getOptionValue(PARAMETER_DATA_CONFIG);
            
            // Run risk assessment
            log.info("Starting target selection...");
            
            // Perform target selection
            ConfigReader configReader = new ConfigReader();
            DataConfig dataConfig = configReader.readDataConfig(dataConfigPath);
            Data data = DataLoader.getData(dataConfig);
            TargetSelection targetSelection = new TargetSelection(data);
            Set<Integer> outlierTargets = targetSelection.getOutlierTargets(data.getHandle().getNumRows());

            // Write to file
            // TODO: Hard-coded output paths should be avoided
            PrintStream fileStream = new PrintStream(new File("targets_" + dataConfig.getDataSetName() + ".csv"));
            for (Integer target : outlierTargets) {
                fileStream.println(target + "; " + targetSelection.getNormalizedDistance(target) + ";");
            }
            fileStream.flush();
            fileStream.close();
            log.info("Target selection finished");
            
        } else {
            
            // No valid option
            help(options, "No known option provided");
            return;
        }
    }

    /**
     * Returns the number of assessments to run in this series
     * @param seriesConfig
     * @return
     */
    private static int calculateSeriesLength(SeriesConfig seriesConfig) {
        int numberOfExperiments = 0;
        if (seriesConfig.getCombinationConfig() != null) {
            for (CombinationConfig combinationRunConfig : seriesConfig.getCombinationConfig()) {
                numberOfExperiments += combinationRunConfig.getPathsToAnonymizationConfig().size() * combinationRunConfig.getPathsToRiskAssessmentConfig().size() * combinationRunConfig.getPathsToDataConfig().size();
            }
        }
        return numberOfExperiments;
    }

    /**
     * Print help
     * @param options
     * @param message
     */
    private static void help(Options options, String message) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("java -jar [file].jar", message, options, "");
    }
    
    /**
     * Run a certain risk assessment
     * @param riskAssessmentConfigPath
     * @param dataConfigPath
     * @param anonymizationConfigPath
     * @param name
     * @param threads
     * @throws IOException
     * @throws ParseException
     * @throws InterruptedException
     */
    private static void runRiskAssessment(String riskAssessmentConfigPath, String dataConfigPath, String anonymizationConfigPath, String name) throws IOException, ParseException, InterruptedException {
        ConfigReader configReader = new ConfigReader();
        AnonymizationConfig anonymizationConfig = configReader.readAnonymizationConfig(anonymizationConfigPath);
        RiskAssessmentConfig riskAssessmentConfig = configReader.readRiskAssessmentConfig(riskAssessmentConfigPath);
        DataConfig dataConfig = configReader.readDataConfig(dataConfigPath);
        StatisticsConfig statisticsConfig = null;
        if (riskAssessmentConfig.getPathToStatisticsConfig() != null) {
            statisticsConfig = configReader.readStatisticsConfig(riskAssessmentConfig.getPathToStatisticsConfig());
        } 
        // Logging
        log.info("Running phantom anonymization assessment");
        log.info(" * Risk assessment config: " + riskAssessmentConfig.getName());
        log.info(" * Data config: " + dataConfig.getDataSetName());
        log.info(" * Anonymization config: " + anonymizationConfig.getName());
        
        // Run
        PhantomAnonymization assessment = new PhantomAnonymization(riskAssessmentConfig, dataConfig, anonymizationConfig, statisticsConfig, name);
        assessment.runRiskAssessment();
        
    }
}


