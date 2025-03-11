#!/bin/bash
java -jar -Xmx1024m target/phantomanonymization-1.0-SNAPSHOT.jar --riskAssessment --riskAssessmentConfig example_configs/riskAssessmentConfig/riskAssessment_example.yml --dataConfig example_configs/dataConfig/data_texas_RM_insensitive_generalization.yml --anonymizationConfig example_configs/anonymizationConfig/k-Anonymity-2_global.yml --name cliExample
$SHELL