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

import com.opencsv.CSVWriter;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class ReportWriter {

    private final CSVWriter attributeContentsWriter;

    private final CSVWriter datasetContentsWriter;

    /**
     * Creates a writer object for attribute metrics and dataset metrics
     * already writes file headers for both reports
     * @param filepath folderPath and fileBase name where reports should be created
     * @param headerAttribute names of metrics for attribute report
     * @param headerDataset names of metrics for dataset report
     * @throws IOException in case that the filepath is faulty
     */
    public ReportWriter(String filepath,String[] headerAttribute, String[] headerDataset) throws IOException {

        attributeContentsWriter = new CSVWriter(new FileWriter(Paths.get(filepath, "_attribute.csv").toFile()));
        attributeContentsWriter.writeNext(headerAttribute);
        attributeContentsWriter.flush();

        datasetContentsWriter = new CSVWriter(new FileWriter(Paths.get(filepath, "_dataset.csv").toFile()));
        datasetContentsWriter.writeNext(headerDataset);
        datasetContentsWriter.flush();

    }

    /**
     * Writes a single line of dataset metrics
     * @param datasetMetricsForOneAnonymization dataset metrics
     * @throws IOException if string can not be written to file
     */
    public void writeToReport(String[] datasetMetricsForOneAnonymization) throws IOException {
        datasetContentsWriter.writeNext(datasetMetricsForOneAnonymization);
        datasetContentsWriter.flush();
    }

    /**
     * Writes a several line of attribute metrics
     * @param attributeMetricsForOneAnonymization list of attribute metrics
     * @throws IOException if string can not be written to file
     */
    public void writeToReport(List<String[]> attributeMetricsForOneAnonymization) throws IOException {
        attributeContentsWriter.writeAll(attributeMetricsForOneAnonymization);
        attributeContentsWriter.flush();
    }

    /**
     * Flushes both report writers and closes file handling
     * @throws IOException in case something went wrong with the file handles for the attribute or the dataset writers
     */
    public void closeReport() throws IOException {
        attributeContentsWriter.flush();
        attributeContentsWriter.close();

        datasetContentsWriter.flush();
        datasetContentsWriter.close();
    }

}
