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

package org.bihmi.phantomanonymization.features;

import org.bihmi.privacy.mgmt.anonymization.config.AttributeConfig;
import org.deidentifier.arx.Data;

import java.util.*;

/**
 * Simple dictionary class
 */
public class Dictionary {

	/** Map for values*/
    private final Map<String, Map<String, Integer>> map;
	
    /**
     * Default constructor for the Dictionary class.
     * Initializes an empty dictionary with an empty internal HashMap for attribute-value mappings.
     */
    public Dictionary() {
    	this.map = new HashMap<>();
    }
    
    /**
     * Private constructor used for creating a deep copy of the Dictionary class.
     * @param map 
     */
    private Dictionary(Map<String, Map<String, Integer>> map) {
    	this.map = map;
    }
	
    /**
     * Adds all hierarchy values from the defined hierarchies in rRef to the dictionary.
     * @param attributeConfigs
     * @param referenceDataset
     */
    public void addAllPossibleHierarchyValues(List<AttributeConfig> attributeConfigs, Data referenceDataset) {
        for (AttributeConfig attributeConfig : attributeConfigs) {
            String[][] hierarchy = referenceDataset.getDefinition().getHierarchy(attributeConfig.getName());
            HashSet<String> uniqueValues = new HashSet<>();
            if (hierarchy != null) {
                for (String[] strings : hierarchy) {
                    // Loop through the columns of the array
                    Collections.addAll(uniqueValues, strings);
                }
                this.probeAll(attributeConfig.getName(), uniqueValues);
            }
        }
    }

    /** 
     * Probe the dictionary returns integer coding of attribute value. If no coding exists it is created
     */
    public int probe(String attribute, String value) {

        // Get map
        Map<String, Integer> values = map.get(attribute);
        if (values == null) {
            values = new HashMap<>();
            map.put(attribute, values);
        }

        // Probe
        Integer code = values.get(value);
        if (code == null) {
            code = values.size();
            values.put(value, code);
        }

        // Done
        return code;
    }

    /**
     * Probes for all values provided
     * @param attribute
     * @param uniqueValues
     */
    public void probeAll(String attribute, HashSet<String> uniqueValues) {
        for (String value : uniqueValues) {
            this.probe(attribute, value);
        }
    }

    /**
     * Returns the size for the given dimension
     */
    public int size(String attribute) {
        if (attribute != null && this.map.get(attribute) != null) {
            return this.map.get(attribute).size();
        } else {
            return 0;
        }
    }
    
    /**
     * Custom clone method which also deep clones the Hashmap.
     */
    @Override
    public Dictionary clone() {
        
        Map<String, Map<String, Integer>> clonedMap = new HashMap<>();
        for (Map.Entry<String, Map<String, Integer>> entry : this.map.entrySet()) {
            String outerKey = entry.getKey();
            Map<String, Integer> innerMap = entry.getValue();
            Map<String, Integer> clonedInnerMap = new HashMap<>(innerMap);
            clonedMap.put(outerKey, clonedInnerMap);
        }
        
        return new Dictionary(clonedMap);
    }
    

}