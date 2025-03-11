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

import lombok.Getter;

import java.io.*;


/**
 * Class to write yaml config files needed for anonymization or performing risk assessments.
 */
@Getter
public class ConfigWriter extends org.bihmi.anonymization.config.ConfigWriter {

    /**
     * Write configuration
     * @param configPath
     * @param config
     * @throws IOException
     */
    public void writeBaseConfig(String configPath, BaseConfig config) throws IOException {
        mapper.writeValue(new File(configPath), config);
    }

    /**
     * Write configuration
     * @param configPath
     * @throws IOException
     */
    public void writePipelineConfig(String configPath) throws IOException {
        mapper.writeValue(new File(configPath), new RiskAssessmentConfig());
    }
 
    /**
     * Write configuration
     * @param configPath
     * @param config
     * @throws IOException
     */
    public void writePipelineConfig(String configPath, RiskAssessmentConfig config) throws IOException {
        mapper.writeValue(new File(configPath), config);
    }
}