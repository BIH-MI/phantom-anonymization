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

package org.bihmi.phantomanonymization.utils;

import com.opencsv.exceptions.CsvException;
import lombok.extern.slf4j.Slf4j;
import org.bihmi.anonymization.config.DataConfig;
import org.bihmi.anonymization.data.DataLoader;

import org.deidentifier.arx.ARXAnonymizer;
import org.deidentifier.arx.ARXConfiguration;
import org.deidentifier.arx.ARXResult;
import org.deidentifier.arx.AttributeType;
import org.deidentifier.arx.AttributeType.Hierarchy;
import org.deidentifier.arx.AttributeType.Hierarchy.DefaultHierarchy;
import org.deidentifier.arx.AttributeType.MicroAggregationFunction;
import org.deidentifier.arx.Data;
import org.deidentifier.arx.DataHandle;
import org.deidentifier.arx.criteria.KAnonymity;

import java.io.IOException;
import java.util.*;

@Slf4j
public class HierarchyUtils {

    /**
     * Transforms a given hierarchy for a numeric or date column to a hierarchy consisting only of numeric or date values (using in-group medians)
     *
     * @param dataConfig             The data configuration for loading the dataset.
     * @param attributeName          The name of the numerical attribute.
     * @param outputHierarchyFilePath Path to save the generated median CSV hierarchy file.
     * @throws IOException  If file operations fail.
     * @throws CsvException If CSV parsing fails.
     */
    public static void generateMedianHierarchy(DataConfig dataConfig,
                                               String attributeName,
                                               String outputHierarchyFilePath) throws IOException, CsvException {
        
        Data originalData = DataLoader.getData(dataConfig);
        Hierarchy inputHierarchy = originalData.getDefinition().getHierarchyObject(attributeName);
        Hierarchy outputHierarchy = transformHierarchy(inputHierarchy, originalData, attributeName);
        outputHierarchy.save(outputHierarchyFilePath);
    }
    
    /**
     * Repeatedly applies “anonymization” to a single attribute using micro‐aggregation, enforcing different generalization levels to build a median-based hierarchy.
     *
     * @param hierarchy  the original hierarchy to be transformed
     * @param data       the dataset containing both definition and raw values
     * @param attribute  the name of the numeric attribute to aggregate
     * @return a new {@code DefaultHierarchy} containing median‐transformed values
     * @throws IOException              if an I/O error occurs when saving interim results
     * @throws IllegalArgumentException if the specified attribute is not found or not numeric/date
     */
    private static Hierarchy transformHierarchy(Hierarchy hierarchy, Data data, String attribute) throws IOException {
        
        // Check hierarchy
        if (hierarchy.getHierarchy() == null || hierarchy.getHierarchy().length == 0 ||
            hierarchy.getHierarchy()[0] == null || hierarchy.getHierarchy()[0].length <= 1) {
            return hierarchy;
        }

        // Configure transformation settings
        // Define all as insensitive
        for (int i = 0; i < data.getHandle().getNumColumns(); i++) {
            data.getDefinition().setAttributeType(data.getHandle().getAttributeName(i), AttributeType.INSENSITIVE_ATTRIBUTE);
        }
        data.getDefinition().setAttributeType(attribute, AttributeType.QUASI_IDENTIFYING_ATTRIBUTE);
        data.getDefinition().setMicroAggregationFunction(attribute, MicroAggregationFunction.createMedian(true), true);
        data.getDefinition().setHierarchy(attribute, hierarchy);

        // Checks
        int columnIndex = data.getHandle().getColumnIndexOf(attribute);
        if (columnIndex < 0) {
            throw new IllegalArgumentException("Unknown attribute");
        }
        if (data.getHandle().getDataType(attribute).getDescription().getWrappedClass() == String.class) {
            throw new IllegalArgumentException("Attribute is not numeric or date");
        }

        // Configure "anonymization" process
        ARXConfiguration config = ARXConfiguration.create();
        config.setAlgorithm(ARXConfiguration.AnonymizationAlgorithm.BEST_EFFORT_BOTTOM_UP);
        config.addPrivacyModel(new KAnonymity(1));
        config.setSuppressionLimit(0d);
        
        // Anonymize
        ARXResult result = new ARXAnonymizer().anonymize(data, config);
        
        // Prepare output
        Map<String, String[]> transformedValues = new TreeMap<String, String[]>();
        for (String[] row : hierarchy.getHierarchy()) {
            transformedValues.put(row[0], row);
        }
        
        // For each generalization level
        for (int level = 1; level < hierarchy.getHierarchy()[0].length; level++) {
            
            // Get transformed output
            DataHandle transformedData = result.getOutput(result.getLattice().getNode(new int[] {level}));
            
            // For each value
            for (int row = 0; row < data.getHandle().getNumRows(); row++) {

                // Get input and output value
                String input = data.getHandle().getValue(row, columnIndex);
                String output = transformedData.getValue(row, columnIndex);
                
                // Store in map
                transformedValues.get(input)[level] = output;
            }
        }
        
        // Convert map to hierarchy
        DefaultHierarchy transformedHierarchy = new DefaultHierarchy();
        for (String[] values : transformedValues.values()) {
            transformedHierarchy.add(values);
        }
        
        return transformedHierarchy;
    }
 
}