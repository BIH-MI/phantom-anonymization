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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import lombok.Getter;

import org.deidentifier.arx.Data;
import org.deidentifier.arx.DataHandle;

import java.io.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Getter
public class ConfigWriter {

    protected final ObjectMapper mapper;

    public ConfigWriter() {
        YAMLFactory factory = new YAMLFactory();
        factory.disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID);
        factory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        factory.enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR);
        this.mapper = new ObjectMapper(factory);
    }

    public void writeDataConfig(String pathToPipelineConfig) throws IOException {
        mapper.writeValue(new File(pathToPipelineConfig), new DataConfig());
    }

    public void writeDataConfig(String pathToPipelineConfig, DataConfig dataConfig) throws IOException {
        mapper.writeValue(new File(pathToPipelineConfig), dataConfig);
    }

    public void writeSummaryConfig(String pathToSummaryConfig, SummaryConfig summaryConfig) throws IOException {
        mapper.writeValue(new File(pathToSummaryConfig), summaryConfig);
    }
    
    public void writeAnonymizationConfig(String pathToAnonymizationConfig, AnonymizationConfig anonymizationConfig) throws IOException {
        mapper.writeValue(new File(pathToAnonymizationConfig), anonymizationConfig);
    }

    public static List<AttributeConfig> createAttributeConfigFromData(Data data){
        DataHandle handle = data.getHandle();
        if (handle == null){
            throw new RuntimeException("Data Handle should not be null.");
        }

        List<AttributeConfig> attributeList = new ArrayList<>();

        for (int i = 0; i < handle.getNumColumns(); i++) {
            String attrName = handle.getAttributeName(i);
            String[] values = handle.getDistinctValues(i); // possibleEntries

            AttributeConfig attrConfig = new AttributeConfig();
            attrConfig.setName(attrName);
            attrConfig.setPossibleEntries(values);
            attrConfig.setMinLevelToUse(0);
            attrConfig.setMaxLevelToUse(-1);
            attrConfig.setType("QUASI_IDENTIFYING_ATTRIBUTE #SENSITIVE_ATTRIBUTE INSENSITIVE_ATTRIBUTE");
            attrConfig.setDataType("categorical #continuous");

            StringBuilder levelStr = new StringBuilder("[");
            for (String v:values) {
                levelStr.append(v).append(", ");
            }
            levelStr.append("]");

            // create hierarchy
            String finalLevelStr = levelStr.toString();
            LinkedHashMap<String, Object> level = new LinkedHashMap<String, Object>(){{
                put("*", finalLevelStr);
            }};
            attrConfig.setLevels(level);

            attributeList.add(attrConfig);
        }

        return attributeList;
    }
}


