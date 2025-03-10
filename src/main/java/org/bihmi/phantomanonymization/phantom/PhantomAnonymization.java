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

package org.bihmi.phantomanonymization.phantom;

import lombok.extern.slf4j.Slf4j;

import org.bihmi.phantomanonymization.config.RiskAssessmentConfig;
import org.bihmi.phantomanonymization.config.StatisticsConfig;
import org.bihmi.phantomanonymization.features.FeatureType;
import org.bihmi.privacy.mgmt.anonymization.config.AnonymizationConfig;
import org.bihmi.privacy.mgmt.anonymization.config.DataConfig;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Class for running risk assessments
 */
@Slf4j
public class PhantomAnonymization {

    /** Directory for storing files */
    // TODO: Hard-coded output paths should be avoided
    private final static String        RESULT_DIRECTORY = "results/";

    /** Number of threads to use */
    private final int                  threadCount;

    /** Config*/
    private final RiskAssessmentConfig riskAssessmentConfig;
    
    /** Config*/
    private final DataConfig           dataConfig;

    /** Config*/
    private final AnonymizationConfig  anonymizationConfig;
    
    /** Config*/
    private final StatisticsConfig     statisticsConfig;

    /** Name*/
    private final String               experimentSeriesName;

    /** File*/
    private final File                 experimentSeriesSummaryFile;

    /**
     * Creates a new instance
     * @param riskAssessmentConfig
     * @param dataConfig
     * @param anonymizationConfig
     * @param statisticsConfig
     * @param seriesName
     * @throws IOException
     */
    public PhantomAnonymization(RiskAssessmentConfig riskAssessmentConfig, DataConfig dataConfig, AnonymizationConfig anonymizationConfig, StatisticsConfig statisticsConfig, String seriesName) throws IOException {
        this.riskAssessmentConfig = riskAssessmentConfig;
        this.dataConfig = dataConfig;
        this.anonymizationConfig = anonymizationConfig;
        this.statisticsConfig = statisticsConfig;
        this.threadCount = riskAssessmentConfig.getThreadCount();
        this.experimentSeriesName = seriesName;
        File resultFolder = new File(RESULT_DIRECTORY);
        if (resultFolder.exists()) {
            resultFolder.mkdir();
        }
        this.experimentSeriesSummaryFile = new File(RESULT_DIRECTORY + seriesName + ".csv");
        if (!experimentSeriesSummaryFile.exists()) {
            experimentSeriesSummaryFile.createNewFile();
            writeToExperimentSeriesSummaryFile("ExperimentName;StartTime;EndTime;SMConfig;DataConfig;AnonymizationConfig;FeartureType;LogFile;SummaryFile;CgfsFile");
        }
    }
    
    /**
     * Main entry point
     */
    public void runRiskAssessment() throws IOException, ParseException, InterruptedException {
        
        // For each feature type
        for (FeatureType featureType : riskAssessmentConfig.getFeatureTypes()) {
            
            // Logging and preparation
            log.info("Starting risk assessment with " + anonymizationConfig.getName() + " and " + featureType);
            String startTime = generateTimeStamp();
            String assessmentName = generateAssessmentName(startTime, experimentSeriesName, riskAssessmentConfig.getName(), dataConfig.getDataSetName(), anonymizationConfig.getName(), featureType);
            
            // Perform actual assessment
            PhantomAnonymizationAssessment assessment = new PhantomAnonymizationAssessment(threadCount, RESULT_DIRECTORY, assessmentName, riskAssessmentConfig, anonymizationConfig, dataConfig, statisticsConfig, featureType);
            assessment.execute();
            
            // Write to experiments summary
            writeToAssessmentSummaryFile(assessmentName, startTime, riskAssessmentConfig.getName(), dataConfig.getDataSetName(), anonymizationConfig.getName(), featureType.toString());
        }  
    }
    
    /**
     * Method to generate experiment name.
     * @param startTime
     * @param seriesName
     * @param riskAssessmentConfigName
     * @param datasetConfigName
     * @param anonymizationConfigName
     * @param featureType
     * @return
     */
    private String generateAssessmentName(String startTime, String seriesName, String riskAssessmentConfigName, String datasetConfigName, String anonymizationConfigName, FeatureType featureType) {
        return startTime + "_" + seriesName + "_" + riskAssessmentConfigName + "_" + datasetConfigName+ "_" + anonymizationConfigName + "_" + featureType;
    }

    /**
     * Method which return date and time (yyyyMMdd_HHmm) as string.
     * @return
     */
    private String generateTimeStamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date());
    }
    
    /**
     * Method to write a single line (provided as log-worthy parameters) to the summary file
     * @param experimentName
     * @param startTime
     * @param riskAssessmentConfigName
     * @param datasetConfigName
     * @param anonConfigName
     * @param featureName
     * @throws IOException
     */
    private void writeToAssessmentSummaryFile(String experimentName,
                                              String startTime,
                                              String riskAssessmentConfigName,
                                              String datasetConfigName,
                                              String anonConfigName,
                                              String featureName) throws IOException {
        String line = experimentName + ";";
        line += startTime + ";";
        line += generateTimeStamp() + ";"; // End time
        line += riskAssessmentConfigName + ";";
        line += datasetConfigName + ";";
        line += anonConfigName + ";";
        line += featureName + ";";
        line += experimentName + "_log.csv" + ";";
        line += experimentName + "_summary.txt" + ";";
        line += experimentName + "_cfgs.yml";
        writeToExperimentSeriesSummaryFile(line);
    }

    /**
     * Method to write a single line (provided as string) to the Experiments Summary file
     * @param line
     * @throws IOException
     */
    private void writeToExperimentSeriesSummaryFile(String line) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(experimentSeriesSummaryFile, true));
        writer.append(line);
        writer.newLine();
        writer.close();
    }
}
