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

@Getter
@Setter
@NoArgsConstructor
@ToString
public class DistinctLDiversity extends PrivacyModel {

    /** Parameter for the privacy model */
    private int l;

    /**
     * Optional list of attributes to which DistinctLDiversity will be applied. If left empty all sensitive
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
            privacyCriterionList.add(new org.deidentifier.arx.criteria.DistinctLDiversity(sensitiveAttribute, l));
        }
        return privacyCriterionList;
    }
}
