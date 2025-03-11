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

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import org.deidentifier.arx.AttributeType;
import org.deidentifier.arx.Data;
import org.deidentifier.arx.criteria.PrivacyCriterion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Privacy model for HierarchicalDistanceTCloseness.
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
public class DDisclosurePrivacy extends PrivacyModel {

    /** Parameter for the privacy model */
    private double d;

    /**
     * Optional list of attributes to which DDisclosurePrivacy will be applied. If left empty all sensitive
     * attributes will be used.
     */
    private List<String> attributes;

    @Override
    public Collection<PrivacyCriterion> getPrivacyCriterion(Data data) {
        List<String> sensitiveAttributes;
        // If attributes are explicitly given use those, otherwise use all sensitive attributes
        if (attributes != null && !attributes.isEmpty()) {
            sensitiveAttributes = attributes;
        } else {
            sensitiveAttributes = new ArrayList<>();
            for (int i = 0; i < data.getHandle().getNumColumns(); i++) {
                String attributeName = data.getHandle().getAttributeName(i);
                if (AttributeType.SENSITIVE_ATTRIBUTE.equals(data.getDefinition().getAttributeType(attributeName))) {
                    sensitiveAttributes.add(attributeName);
                }
            }
        }
        List<PrivacyCriterion> privacyCriterionList = new ArrayList<>();
        // Prepare config
        for (String sensitiveAttribute : sensitiveAttributes) {
            privacyCriterionList.add(new org.deidentifier.arx.criteria.DDisclosurePrivacy(
                    sensitiveAttribute,
                    d));
        }
        return privacyCriterionList;
    }
}
