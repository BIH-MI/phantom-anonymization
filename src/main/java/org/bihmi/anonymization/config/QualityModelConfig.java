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

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.deidentifier.arx.metric.Metric;

@Getter
@Setter
@NoArgsConstructor
public class QualityModelConfig {

    /**
     * QualityModel chosen out of {@link QualityModelType}
     */
    private QualityModelType qualityModelType;

    /**
     * Parameter for LossMetric. Default is 0.5. A factor of 0 will favor suppression, and a factor of 1 will favor generalization.
     */
    private double gsFactor = 0.5;

    /**
     * Optional parameter for aggregate function
     */
    private Metric.AggregateFunction aggregateFunction;

    public enum QualityModelType {
        LOSS_METRIC
    }
}
