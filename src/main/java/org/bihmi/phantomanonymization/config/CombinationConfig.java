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
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Config class to store paths to configs for assessment series.
 * All combinations of paths to anonymization, data and risk assessment configurations will be used.
 */
@Getter
@Setter
@NoArgsConstructor
public class CombinationConfig {

    /** List of paths to anonymization configs */
    private List<String> pathsToAnonymizationConfig;

    /** List of paths to data configs */
    private List<String> pathsToDataConfig;

    /** List of paths to risk assessment configs */
    private List<String> pathsToRiskAssessmentConfig;
}
