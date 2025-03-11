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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.bihmi.anonymization.config.experiment.AnonymizationExperimentConfig;
import org.bihmi.anonymization.data.DataLoader;

/**
 * Class to read yaml config files needed for anonymization or performing shadow model attacks.
 */
@Slf4j
public class ConfigReader {

    protected final ObjectMapper objectMapper;

    public ConfigReader() {
        objectMapper = new ObjectMapper(new YAMLFactory());
        objectMapper.findAndRegisterModules();
        objectMapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public AnonymizationConfig readAnonymizationConfig(String pathToAnonymizationConfig) throws IOException {
        log.debug("Working Directory = " + System.getProperty("user.dir"));
        InputStream input = Files.newInputStream(Paths.get(pathToAnonymizationConfig));
        AnonymizationConfig anonymizationConfig = objectMapper.readValue(input, AnonymizationConfig.class);
        return anonymizationConfig;
    }

    public DataConfig readDataConfig(String pathToDataConfig) throws IOException {
        log.debug("Working Directory = " + System.getProperty("user.dir"));
        InputStream input = Files.newInputStream(Paths.get(pathToDataConfig));
        DataConfig dataConfig = objectMapper.readValue(input, DataConfig.class);
        List<AttributeConfig> enrichedAttributeConfigs;
        try {
            enrichedAttributeConfigs = DataLoader.getEnrichedAttributeConfigs(dataConfig);
        } catch (IOException e) {
            throw new RuntimeException("Problem while using data config, to autofill attribute configs");
        }
        dataConfig.setAttributeConfigs(enrichedAttributeConfigs);
        return dataConfig;
    }

    public AnonymizationExperimentConfig readAnonymizationExperimentConfig(String experimentConfigPath) throws IOException {
        log.debug("Working Directory = " + System.getProperty("user.dir"));
        InputStream input = Files.newInputStream(Paths.get(experimentConfigPath));
        AnonymizationExperimentConfig anonymizationExperimentConfig = objectMapper.readValue(input, AnonymizationExperimentConfig.class);
        return anonymizationExperimentConfig;
    }

    public ReportConfig readReportConfig(String reportConfigPath) throws IOException {
        log.debug("Working Directory = " + System.getProperty("user.dir"));
        InputStream input = Files.newInputStream(Paths.get(reportConfigPath));
        return objectMapper.readValue(input, ReportConfig.class);
    }
}


