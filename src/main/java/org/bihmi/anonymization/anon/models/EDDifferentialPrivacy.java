package org.bihmi.anonymization.anon.models;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import org.deidentifier.arx.Data;
import org.deidentifier.arx.criteria.PrivacyCriterion;

import java.util.Collection;
import java.util.Collections;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class EDDifferentialPrivacy extends PrivacyModel{

    private double epsilon;

    private double delta;

    boolean deterministic = false;
    @Override
    public Collection<PrivacyCriterion> getPrivacyCriterion(Data data) {
        return Collections.singletonList(new org.deidentifier.arx.criteria.EDDifferentialPrivacy(epsilon, delta, null, deterministic));
    }
}
