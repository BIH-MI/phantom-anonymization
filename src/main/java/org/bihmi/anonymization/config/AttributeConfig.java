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

import lombok.*;

import org.bihmi.anonymization.data.DataLoader;
import org.bihmi.anonymization.data.MicroAggregationFunction;
import org.deidentifier.arx.AttributeType.Hierarchy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * Attribute Config. Also contains code to convert levels to hierarchies.
 *
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttributeConfig {

    private String name;
    private String dataType;
    private String type;
    private String dateFormat; //if attribute is a Date, define format here, if null ARX Default is used "dd.MM.yyyy"
    private String[] possibleEntries; // if categorical, each possible value
    private Boolean is_nullable; //TODO: what is the function of this? -> could contain NULL values, not in use currently
    private Boolean include = true; // if false, attribute is excluded for almost everything
    private Object min; // min of all possible values (may exceed data range from sample)
    private Object max; // max of all possible values (may exceed data range from sample)
    private Integer minLevelToUse; // min level from hierarchy to use
    private Integer maxLevelToUse; // max level from hierarchy to use
    // TODO: using Map<String, Object> is very uncomfortable for further usage. Maybe new data type needed (looks like a tree)
    private Map<String, Object> levels;
    private String pathToHierarchy; // if levels is null, this is used to load hierarchy from file
    private boolean useMicroAggregation = false; // if true, use micro aggregation for this attribute
    private MicroAggregationFunction microAggregationFunction = MicroAggregationFunction.ARITHMETIC_MEAN; // parameter for micro aggregation
    private boolean performClustering = true; // parameter for micro aggregation
    private boolean ignoreMissingData = true; // parameter for micro aggregation

    private static Hierarchy getHierarchyFromLevelData(List<String[]> list) {
        return Hierarchy.create(list);
    }

    private List<String[]> getLevelDataAsList(Map<String, Object> levels) {
        return getLevelDataAsList((Map) levels, 0);
    }

    private List<String[]> getLevelDataAsList(Map<Object, Object> levels, int levelDepth) {
        List<String[]> result = new ArrayList<>();
        for (Map.Entry<Object, Object> level : levels.entrySet()) {
            if (level.getValue() instanceof LinkedHashMap) {
                Object levelKey = level.getKey();
                Map<Object, Object> levelMap = (Map<Object, Object>) level.getValue();
                List<String[]> levelData = getLevelDataAsList(levelMap, levelDepth + 1);
                for (String[] l : levelData) {
                    String[] lNew = new String[l.length + 1];
                    lNew[l.length] = (String) levelKey;
                    System.arraycopy(l, 0, lNew, 0, l.length);
                    result.add(lNew);
                }
            } else if (level.getValue() instanceof List) {
                Object levelKey = level.getKey();
                List<String> levelList = (List) level.getValue();
                for (String value : levelList) {
                    String[] l = new String[2];
                    l[1] = (String) levelKey;
                    l[0] = value;
                    result.add(l);
                }
            }
        }
        return result;
    }

    public Hierarchy parseArxHierarchy() throws IOException {
        if (this.levels != null) {
            return getHierarchyFromLevelData(getLevelDataAsList(this.levels));
        } else if (this.pathToHierarchy != null) {
            return DataLoader.loadHierarchy(pathToHierarchy);
        }
        return null;
    }

    @Override
    public String toString() {
        return "AttributeConfig{" +
                "name='" + name + '\'' +
                ", dataType='" + dataType + '\'' +
                ", type='" + type + '\'' +
                ", dateFormat='" + dateFormat + '\'' +
                ", is_nullable=" + is_nullable +
                ", include=" + include +
                ", min=" + min +
                ", max=" + max +
                ", minLevelToUse=" + minLevelToUse +
                ", maxLevelToUse=" + maxLevelToUse +
                ", levels=" + levels +
                ", pathToHierarchy='" + pathToHierarchy + '\'' +
                ", useMicroAggregation=" + useMicroAggregation +
                ", microAggregationFunction=" + microAggregationFunction +
                ", performClustering=" + performClustering +
                ", ignoreMissingData=" + ignoreMissingData +
                '}';
    }
}

