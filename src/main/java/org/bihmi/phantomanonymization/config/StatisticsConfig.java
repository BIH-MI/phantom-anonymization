package org.bihmi.phantomanonymization.config;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Configuration class to define run configs for running a series of assessments.
 */
@Getter
@Setter
@NoArgsConstructor
public class StatisticsConfig {

    /** List of attributes used as features for linear regression task */
    String[] featureAttributes;
    
    /** Attribute which class will be predicted  */
    String targetAttribute;
}
