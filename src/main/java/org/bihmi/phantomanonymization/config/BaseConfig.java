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

import org.bihmi.anonymization.config.AnonymizationConfig;
import org.bihmi.anonymization.config.DataConfig;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Base configuration file
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
public class BaseConfig {

    /** Name of the experiment */
    String               experimentName;

    /** Configuration for the risk assessment process */
    RiskAssessmentConfig riskAssessmentConfig;

    /** Configuration for the data */
    DataConfig           dataConfig;

    /** Configuration for the anonymization process */
    AnonymizationConfig  anonymizationConfig;
}
