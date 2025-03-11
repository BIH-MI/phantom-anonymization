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

package org.bihmi.anonymization.anon.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.deidentifier.arx.Data;
import org.deidentifier.arx.criteria.PrivacyCriterion;

import java.util.Collection;

/**
 * Abstract class of a privacy model, which is used for parsing a list of PrivacyModels with the Jackson library.
 * Annotations tell the Jackson library into which subtypes to parse the entries of the config file.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = KAnonymity.class, name = "k-Anonymity"),
        @JsonSubTypes.Type(value = DistinctLDiversity.class, name = "DistinctLDiversity"),
        @JsonSubTypes.Type(value = EntropyLDiversity.class, name = "EntropyLDiversity"),
        @JsonSubTypes.Type(value = HierarchicalDistanceTCloseness.class, name = "HierarchicalDistanceTCloseness"),
        @JsonSubTypes.Type(value = EqualDistanceTCloseness.class, name = "EqualDistanceTCloseness"),
        @JsonSubTypes.Type(value = DDisclosurePrivacy.class, name = "DDisclosurePrivacy"),
        @JsonSubTypes.Type(value = EnhancedBLikeness.class, name = "EnhancedBLikeness"),
        @JsonSubTypes.Type(value = EDDifferentialPrivacy.class, name = "EDDifferentialPrivacy"),
        @JsonSubTypes.Type(value = AverageReidentificationRisk.class, name = "AverageReidentificationRisk"),
        @JsonSubTypes.Type(value = PopulationUniqueness.class, name = "PopulationUniqueness")
})
public abstract class PrivacyModel {

    /**  Returns a list of all privacy criteria for the Privacy model. */
    public abstract Collection<PrivacyCriterion> getPrivacyCriterion(Data data);
}
