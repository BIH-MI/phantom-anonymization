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

import org.bihmi.anonymization.anon.AnonymizationMethods;
import org.bihmi.anonymization.config.AnonymizationConfig;
import org.bihmi.anonymization.config.DataConfig;
import org.bihmi.anonymization.data.DataLoader;
import org.deidentifier.arx.Data;
import org.deidentifier.arx.DataHandle;

import java.io.IOException;

@Slf4j
public class AnonymizationExperiment {

  public static DataHandle anonymize(AnonymizationConfig anonymizationConfig, DataConfig dataConfig) throws IOException {
      log.info("Anonymizing " + dataConfig.getDataSetName() + " with " + anonymizationConfig.getName());
      long startTime = System.currentTimeMillis();
      Data data = DataLoader.getData(dataConfig);
      AnonymizationMethods.AnonymizationMethod anonymizationMethod = AnonymizationMethods.CONFIG_ANONYMIZATION(anonymizationConfig);
      DataHandle output = anonymizationMethod.anonymize(data);
      long endTime = System.currentTimeMillis();
      log.info("Anonymization done in " + (endTime-startTime) + "ms");
      return output;
  }
}
