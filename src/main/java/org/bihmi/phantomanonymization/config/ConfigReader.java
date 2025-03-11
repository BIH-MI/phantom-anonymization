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

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Class to read yaml config files needed for anonymization or performing risk assessment
 */
@Slf4j
public class ConfigReader extends org.bihmi.anonymization.config.ConfigReader {

    /**
     * Read experiment configuration
     * @param configPath
     * @return
     * @throws IOException
     */
    public SeriesConfig readExperimentConfig(String configPath) throws IOException {
        log.debug("Working Directory: " + System.getProperty("user.dir"));
        InputStream input = Files.newInputStream(Paths.get(configPath));
        return objectMapper.readValue(input, SeriesConfig.class);
    }

    /**
     * Read risk assessment configuration
     * @param configPath
     * @return
     * @throws IOException
     */
    public RiskAssessmentConfig readRiskAssessmentConfig(String configPath) throws IOException {
        log.debug("Working Directory: " + System.getProperty("user.dir"));
        InputStream input = Files.newInputStream(Paths.get(configPath));
        return objectMapper.readValue(input, RiskAssessmentConfig.class);
    }
    
    /**
     * Read statistics configuration
     * @param configPath
     * @return
     * @throws IOException
     */
    public StatisticsConfig readStatisticsConfig(String configPath) throws IOException {
        log.debug("Working Directory: " + System.getProperty("user.dir"));
        InputStream input = Files.newInputStream(Paths.get(configPath));
        return objectMapper.readValue(input, StatisticsConfig.class);
    }
}