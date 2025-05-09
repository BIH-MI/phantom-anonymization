#!/bin/bash
java -jar target/phantomanonymization-1.0-SNAPSHOT.jar --generateMedianHierarchy --dataConfig example_configs/dataConfig/data_texas_RM_insensitive_generalization.yml --attributeName LENGTH_OF_STAY --outputHierarchyFile data/texas/hierarchies_numeric/texas_hierarchy_LENGTH_OF_STAY_median.csv
$SHELL