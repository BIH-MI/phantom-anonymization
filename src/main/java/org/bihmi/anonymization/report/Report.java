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

package org.bihmi.anonymization.report;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.bihmi.anonymization.anon.AnonymizationResults;
import org.bihmi.anonymization.config.AnonymizationConfig;
import org.bihmi.anonymization.config.ConfigWriter;
import org.bihmi.anonymization.config.DataConfig;
import org.bihmi.anonymization.config.ReportConfig;
import org.bihmi.anonymization.config.SummaryConfig;

/**
 * Class to generate and write anonymization report.
 */
public class Report {

    final ConfigWriter configWriter;

    ReportWriter reportWriter;

    final ReportConfig reportConfig;

    public Report(ReportConfig reportConfig) {

        Path path = Paths.get(reportConfig.getFilePathToReport(), "configs");
        if (!path.toFile().exists()) {
            path.toFile().mkdirs();
        }

        this.reportConfig = reportConfig;
        this.configWriter = new ConfigWriter();
    }

    /**
     * Handles the closing of the reportWriter
     * @throws IOException
     */
    public void closeReport() throws IOException {
        if (this.reportWriter != null){
            this.reportWriter.closeReport();
        }
    }


    /**
     * Checks if the reportwriter was already initialised and writes the file headers
     * @param reportFolder path where the report should be created
     * @param results object containing the anonymization metrics
     * @throws IOException
     */
    public void checkAndInitReport(String reportFolder, AnonymizationResults results) throws IOException {
        if (reportWriter == null){
            // TODO: Maybe extract to separate method
            List<String> headerStart = new ArrayList<>();
            headerStart.add("AnonName");
            headerStart.add("DatasetName");

            List<String> attributeHeader = results.getAttributePrivacyHeader(); // anonName, datasetName, AttributeName, ... configPath
            List<String> datasetHeader = results.getDatasetPrivacyHeader(); // anonName, datasetName, ... configPath

            attributeHeader.add(0, "AttributeName");
            attributeHeader.addAll(0, headerStart);
            attributeHeader.add("ConfigPath");

            datasetHeader.addAll(0, headerStart);
            datasetHeader.add("ConfigPath");

            this.reportWriter = new ReportWriter(reportFolder,
                    attributeHeader.toArray(new String[0]),
                    datasetHeader.toArray(new String[0]));
        }
    }

    /**
     * Writes a single experiment result to the attribute and dataset metric files
     * @param results
     * @param anonymizationConfig
     * @param dataConfig
     * @throws IOException
     */
    public void writeExperimentToReport(AnonymizationResults results, AnonymizationConfig anonymizationConfig, DataConfig dataConfig) throws IOException {

        String anonymizationName = anonymizationConfig.getName();
        String datasetName = dataConfig.getDataSetName();

        //TODO: this name needs to be unique or we overwriter other configs...
        String pathSummaryConfig = Paths.get(reportConfig.getFilePathToReport(), "configs", anonymizationName + "_" + datasetName + ".yaml").toString();
        configWriter.writeSummaryConfig(pathSummaryConfig, new SummaryConfig(anonymizationConfig, dataConfig));

        String[] datasetMetricsForOneAnonymization = getDatasetMetrics(results.getDatasetPrivacyMetricValues(), anonymizationName, datasetName, pathSummaryConfig);
        List<String[]> attributeMetricsForOneAnonymization = getAttributeMetrics(results.getAttributePrivacyMetrics(), anonymizationName, datasetName, pathSummaryConfig);

        checkAndInitReport(reportConfig.getFilePathToReport(), results);
        reportWriter.writeToReport(datasetMetricsForOneAnonymization);
        reportWriter.writeToReport(attributeMetricsForOneAnonymization);
    }

    /**
     * Aggregates dataset metrics, anonymization name, dataset name and configuration path into a single string array.
     * @param metrics dataset metrics
     * @param anonName anonymization name
     * @param datasetName dataset name
     * @param configPath configuration path
     * @return string array for the reportWriter
     */
    // TODO (KO): suboptimal dass manchmal die hashmap reingegeben wird, und manchmal die liste. Überlegen wo geflitert werden soll.
    private static String[] getDatasetMetrics(List<Double> metrics, String anonName, String datasetName, String configPath) {
        List<String> returnValue = new ArrayList<>();
        returnValue.add(anonName);
        returnValue.add(datasetName);
        for(Double d : metrics){
            returnValue.add(d.toString());
        }
        returnValue.add(configPath);
        return returnValue.toArray(new String[0]);
    }

    /**
     * Aggregates attribute metrics, anonymization name, dataset name and configuration path into a list of string arrays.
     * @param metrics dataset metrics
     * @param anonName anonymization name
     * @param datasetName dataset name
     * @param configPath configuration path
     * @return list of string arrays for the reportWriter
     */
    private List<String[]> getAttributeMetrics(HashMap<String, HashMap<String, Double>> metrics, String anonName, String datasetName, String configPath) {
        ArrayList<String[]> result = new ArrayList<>();

        Set<String> qids = metrics.keySet();
        for (String qid : qids) {
            List<String> returnValue = new ArrayList<>();
            returnValue.add(anonName);
            returnValue.add(datasetName);
            returnValue.add(qid);

            metrics.get(qid).forEach((metricName, value) -> {
                returnValue.add(value.toString());
            });

            // Path to config path
            returnValue.add(configPath);
            result.add(returnValue.toArray(new String[0]));
        }
        return result;
    }

    private static double round(double value) {
        return round(value, 100);
    }

    private static double round(double value, double factor) {
        value = value * factor;
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(1, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

}
