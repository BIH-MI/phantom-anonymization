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

package org.bihmi.phantomanonymization.io;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.bihmi.anonymization.config.AnonymizationConfig;
import org.bihmi.anonymization.config.DataConfig;
import org.bihmi.anonymization.data.DataLoader;
import org.bihmi.phantomanonymization.config.BaseConfig;
import org.bihmi.phantomanonymization.config.ConfigWriter;
import org.bihmi.phantomanonymization.config.RiskAssessmentConfig;
import org.bihmi.phantomanonymization.target.TargetSelection;
import org.deidentifier.arx.Data;

/**
 * Writer for reports
 */
public class ReportWriter {

    /** Log file */
    private final File logFile;
    /** Summary file*/
    private final File summaryFile;

    /**
     * Creates a new instance
     * @param directory
     * @param experimentName
     * @param riskAssessmentConfig
     * @param anonymizationConfig
     * @param dataConfig
     * @throws IOException
     */
    public ReportWriter(String directory, String experimentName, RiskAssessmentConfig riskAssessmentConfig, AnonymizationConfig anonymizationConfig, DataConfig dataConfig) throws IOException {
        
        // Create files
        logFile = new File(directory + experimentName + "_log.csv");
        summaryFile = new File(directory + experimentName + "_summary.txt");
        
        // Check if exist
        if (!logFile.createNewFile() || !summaryFile.createNewFile()) {
            
            // TODO: Bad practice to throw a runtime exception here... Why not IOException?
            throw new RuntimeException("Summary and/or log file(s) already exist(s)!");
        }
        
        // Write base configuration
        BaseConfig baseConfig = new BaseConfig(experimentName, riskAssessmentConfig, dataConfig, anonymizationConfig);
        new ConfigWriter().writeBaseConfig(directory + experimentName + "_cfgs.yml", baseConfig);
        
        // Writer header to log file
        writeToLogFile(Collections.singleton("TestRun;TargetId;Iteration;TrueLabel;PredictedLabel;PredictionProbability;Granularity;GranularityCategoricalAttributes;Entropy;Discernibility;MaximalEquivalenceClassSize;AverageEquivalenceClassSize;MinimalEquivalenceClassSize;NumberOfSuppressedRecords;LocationAndLimits;ClassificationAccuracy"));
    }

    /**
     * Write summary file
     * @param riskAssessmentConfig
     * @param dataConfig
     * @param trueGuesses
     * @throws IOException
     * @throws ParseException
     */
    public void writeSummaryFile(RiskAssessmentConfig riskAssessmentConfig, DataConfig dataConfig,  Map<Integer, AtomicInteger> trueGuesses) throws IOException, ParseException {
        
        // Create writer
        BufferedWriter writer = new BufferedWriter(new FileWriter(summaryFile, true));
        writer.append("TargetId;Distance;Accuracy");
        writer.newLine();
        
        // Create dataset and model to calculate distance
        Data dataset = DataLoader.getData(dataConfig);
        TargetSelection targetSelection = new TargetSelection(dataset);

        // Calculate number of tests
        int executedTestsPerTarget = riskAssessmentConfig.getRunCount() * riskAssessmentConfig.getRunTestCount() * 2;
        
        // Write result for each true guess
        for (Map.Entry<Integer, AtomicInteger> entry : trueGuesses.entrySet()) {
            
            // Get target
            int targetId = entry.getKey();
            
            // Get distance
            double distance = targetSelection.getNormalizedDistance(targetId);
            
            // Get accuracy
            double accuracy = (double) entry.getValue().get() / executedTestsPerTarget;
            
            // Write
            writer.append(targetId + ";" + distance + ";" + accuracy);
            writer.newLine();
        }
        
        // Done
        writer.close();
    }

    /**
     * Append lines to the log file
     * @param lines
     */
    public synchronized void writeToLogFile(Set<String> lines) {
        
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true));
            for (String line : lines) {
                writer.append(line);
                writer.newLine();
            }
            writer.close();
        } catch (IOException e) {
            // TODO: Bad practice: printStackTrace and not using an IOException here
            e.printStackTrace();
            throw new RuntimeException("Unable to write to log file");
        }
    }
}
