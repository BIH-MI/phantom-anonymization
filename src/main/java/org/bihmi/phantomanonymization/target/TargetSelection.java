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

package org.bihmi.phantomanonymization.target;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math3.ml.distance.EuclideanDistance;
import org.apache.commons.math3.util.Pair;
import org.deidentifier.arx.Data;
import org.deidentifier.arx.DataHandle;
import org.deidentifier.arx.DataType;
import org.deidentifier.arx.aggregates.StatisticsFrequencyDistribution;
import org.deidentifier.arx.aggregates.StatisticsSummary;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.util.*;

/**
 * Class for performing target selection
 */
public class TargetSelection {

    /** Maximum values*/
    protected final Map<String, Double>              maximum             = new HashMap<>();
    /** Minimum values*/
    protected final Map<String, Double>              minimum             = new HashMap<>();
    /** Data types */
    protected final Map<String, DataType<?>>         dataTypes           = new HashMap<>();
    /** Minimum distance*/
    protected double                                 minDistance;
    /** Maximum distance*/
    protected double                                 maxDistance;
    /** Mappings for categorical variables*/
    protected final Map<String, Map<String, Double>> categoricalMappings = new HashMap<>();
    /** Distances for each record*/
    protected double[]                               distances;
    /** Data to analyze*/
    protected final Data                             data;

    /**
     * Creates a new instance, using the QIs from the provided file
     * @param data
     * @throws ParseException
     */
    public TargetSelection(Data data) throws ParseException {
        
        // Prepare
        this.data = data;
        DataHandle handle = data.getHandle();
        Set<String> _attributes = handle.getDefinition().getQuasiIdentifyingAttributes();

        // Check
        if (_attributes.size() == 0){
            throw new IllegalArgumentException("Distances will only calculated on QIs but none were defined.");
        }

        // Collect indices and variable names
        String[] attributes = new String[_attributes.size()];
        int index = 0;
        for (int column = 0; column < handle.getNumColumns(); column++) {
            String attribute = handle.getAttributeName(column);
            if (_attributes.contains(attribute)) {
                attributes[index++] = attribute;
            }
        }
        
        // Perform analysis
        analyzeDataset(handle, attributes);
    }

    /**
     * Creates a new instance, using the given attributes
     * @param data
     * @param attributes
     * @throws ParseException
     */
    public TargetSelection(Data data, Set<String> attributes) throws ParseException {
        
        // Prepare
        this.data = data;
        DataHandle handle = data.getHandle();

        // Collect indices and variable names
        String[] _attributes = new String[attributes.size()];
        int index = 0;
        for (int column = 0; column < handle.getNumColumns(); column++) {
            String attribute = handle.getAttributeName(column);
            if (attributes.contains(attribute)) {
                _attributes[index++] = attribute;
            }
        }
        
        // Perform analysis
        analyzeDataset(handle, _attributes);
    }

    /**
     * Returns average targets
     * @param numTargets
     * @return
     */
    public Set<Integer> getAverageTargets(int numTargets) {
        return getTopTargets(numTargets, TargetType.AVERAGE);
    }

    /**
     * Imports target IDs from a file
     */
    public Set<Integer> getImportedTargets(String file){
        
        // Prepare
        Set<Integer> targetIds = new HashSet<>();
        BufferedReader reader;
        try {
            
            // Read
            reader = new BufferedReader(new FileReader(file));
            String line = reader.readLine();
            while(line != null) {
                targetIds.add(Integer.parseInt(line));
                line = reader.readLine();
            }
            reader.close();
        } catch (IOException e) {            
            // TODO: No runtime exceptions
            throw new RuntimeException("Could not read targets import file: " + file);
        } catch (NumberFormatException e) {
            // TODO: No runtime exceptions
            throw new NumberFormatException("Could not parse Target ID");
        }
        
        // Done
        return targetIds;
    }

    /**
     * Returns the normalized distance in [0, 1] for the record at the given row compared to min and max
     */
    public double getNormalizedDistance(int row) {
        return (distances[row] - minDistance) / (maxDistance - minDistance);
    }

    /**
     * Returns outlier targets
     * @param numTargets
     * @return
     */
    public Set<Integer> getOutlierTargets(int numTargets) {
        return getTopTargets(numTargets, TargetType.OUTLIER);
    }

    /**
     * Gets a set of random targets
     */
    public Set<Integer> getRandomTargets(int targets) {
        // Collect random numbers
        int size = this.data.getHandle().getNumRows();
        Random random = new Random();
        Set<Integer> samples = new HashSet<>();
        while (samples.size() < targets) {
            samples.add(random.nextInt(size));
        }
        return samples;
    }

    /**
     * Returns the specified targets
     * @param targetType
     * @param numberOfTargets
     * @param targetImportFile
     * @return
     * @throws ParseException
     */
    public Set<Integer> getTargets(TargetType targetType, int numberOfTargets, String targetImportFile) throws ParseException {
        switch (targetType) {
            case RANDOM:
                return getRandomTargets(numberOfTargets);
            case OUTLIER:
                return getOutlierTargets(numberOfTargets);
            case AVERAGE:
                return getAverageTargets(numberOfTargets);
            case IMPORT:
                return getImportedTargets(targetImportFile);
            default:
                throw new RuntimeException("Invalid target type");
        }
    }

    /**
     * Analyze the dataset
     * @param dataset
     * @param attributes
     * @throws ParseException
     */
    private void analyzeDataset(DataHandle dataset, String[] attributes) throws ParseException {
        
        // Calculate statistics
        Map<String, StatisticsSummary<?>> statistics = dataset.getStatistics().getSummaryStatistics(false);
        List<Integer> categoricalIndicies = new ArrayList<>();
        
        // For each attribute
        for (String attribute : attributes) {

            // Obtain statistics
            StatisticsSummary<?> summary = statistics.get(attribute);
            DataType<?> _type = dataset.getDefinition().getDataType(attribute);
            Class<?> _clazz = _type.getDescription().getWrappedClass();
            checkDataType(attribute, _type);

            // Calculate depending on data type
            if (_clazz.equals(Long.class)) {
                // Handle data type represented as long
                double min = (Long)summary.getMinAsValue();
                double max = (Long)summary.getMaxAsValue();
                minimum.put(attribute, min);
                maximum.put(attribute, max);
            } else if (_clazz.equals(Double.class)) {
                // Handle data type represented as double
                double min = (Double)summary.getMinAsValue();
                double max = (Double)summary.getMaxAsValue();
                minimum.put(attribute, min);
                maximum.put(attribute, max);
            } else if (_clazz.equals(Date.class)) {
                // Handle data type represented as date
                double min = ((Date)summary.getMinAsValue()).getTime();
                double max = ((Date)summary.getMaxAsValue()).getTime();
                minimum.put(attribute, min);
                maximum.put(attribute, max);
            } else {
                // Pre-encode categorical values considering the order
                int column = dataset.getColumnIndexOf(attribute);
                categoricalIndicies.add(ArrayUtils.indexOf(attributes, attribute));
                StatisticsFrequencyDistribution frequencyDistribution = dataset.getStatistics().getFrequencyDistribution(column);
                if (categoricalMappings.get(attribute) == null) {
                    categoricalMappings.put(attribute, new HashMap<>());
                }
                for (int i = 0; i < frequencyDistribution.values.length; i++) {
                    categoricalMappings.get(attribute).put(frequencyDistribution.values[i], frequencyDistribution.frequency[i]);
                }
            }
        }

        // Calculate the centroid
        double[] centroid = calculateCentroid(dataset, attributes, categoricalIndicies);

        // Calculate min and max-distance
        minDistance = Double.MAX_VALUE;
        maxDistance = -Double.MAX_VALUE;
        distances = new double[dataset.getNumRows()];
        for (int row = 0; row < dataset.getNumRows(); row++) {
            double[] vector = getVector(dataset, row, attributes);
            double distance = new EuclideanDistance().compute(centroid, vector);
            distances[row] = distance;
            minDistance = Math.min(minDistance, distance);
            maxDistance = Math.max(maxDistance, distance);
        }
    }

    /**
     * Calculates the controid
     * @param dataset
     * @param attributes
     * @param categoricalIndicies
     * @return
     * @throws ParseException
     */
    private double[] calculateCentroid(DataHandle dataset, String[] attributes, List<Integer> categoricalIndicies) throws ParseException {

        // Calculate centroid. Categorical values will get the max value for the categorical attribute.
        double[] centroid = new double[attributes.length];
        for (int row = 0; row < dataset.getNumRows(); row++) {
            double[] vector = getVector(dataset, row, attributes);
            for (int i = 0; i < attributes.length; i++) {
                if (categoricalIndicies.contains(i)) {
                    if (vector[i] > centroid[i]) {
                        centroid[i] = vector[i];
                    }
                } else {
                    centroid[i] += vector[i];
                }
            }
        }
        for (int i = 0; i < attributes.length; i++) {
            if (!categoricalIndicies.contains(i)) {
                centroid[i] /= dataset.getNumRows();
            }
        }
        return centroid;
    }

    /**
     * Sanity check to ensure consistency of data types
     */
    private void checkDataType(String attribute, DataType<?> type) {
        DataType<?> _type = dataTypes.get(attribute);
        if (_type == null) {
            dataTypes.put(attribute, type);
        } else if (!(_type.equals(type))) {
            throw new IllegalArgumentException("Inconsistent data type detected for attribute: " + attribute);
        }
    }

    /**
     * Creates a vector
     * @param handle
     * @param row
     * @param attributes
     * @return
     * @throws ParseException
     */
    private double[] getVector(DataHandle handle, int row, String[] attributes) throws ParseException {
        
        // Prepare
        double[] vector = new double[attributes.length];
        int index = 0;

        // For each attribute
        for (String attribute : attributes) {
            // Index
            int column = handle.getColumnIndexOf(attribute);
            // Obtain metadata
            DataType<?> _type = handle.getDefinition().getDataType(attribute);
            Class<?> _clazz = _type.getDescription().getWrappedClass();
            // Value
            double value;

            // Calculate depending on data type
            if (_clazz.equals(Long.class)) {
                // Handle data type represented as long
                Long _value = handle.getLong(row, column);
                value = _value != null ? _value : 0d; // TODO: how to handle null here
                value = (value + minimum.get(attribute)) / (minimum.get(attribute) + maximum.get(attribute));
            } else if (_clazz.equals(Double.class)) {
                // Handle data type represented as double
                Double _value = handle.getDouble(row, column);
                value = _value != null ? _value : 0d; // TODO: how to handle null here
                value = (value + minimum.get(attribute)) / (minimum.get(attribute) + maximum.get(attribute));
            } else if (_clazz.equals(Date.class)) {
                // Handle data type represented as date
                Date _value = handle.getDate(row, column);
                value = _value != null ? _value.getTime() : 0d; // TODO: how to handle null here
                value = (value + minimum.get(attribute)) / (minimum.get(attribute) + maximum.get(attribute));
            } else if (_clazz.equals(String.class)) {
                // Map via categoricalMappings, which include frequencies (range is 0 to 1)
                value = categoricalMappings.get(attribute).get(handle.getValue(row, column));
            } else {
                throw new IllegalStateException("Unknown data type");
            }
            // Store
            vector[index++] = value;
        }
        // Done
        return vector;
    }

    /**
     * 
     * @param numTargets
     * @param type
     * @return
     */
    protected Set<Integer> getTopTargets(int numTargets, TargetType type) {
        
        // Prepare
        Set<Integer> targetIds = new HashSet<>();
        List<Pair<Integer, Double>> distances = new ArrayList<>();

        // Get distances
        for (int i = 0; i < this.distances.length; i++) {
            distances.add(new Pair<>(i, getNormalizedDistance(i)));
        }

        // For each target type
        switch (type) {
            case OUTLIER:
                // Sort indices by distance in descending order
                distances.sort((p1, p2) -> {
                    //TODO 0 does not return equal
                    return p2.getSecond().compareTo(p1.getSecond());
                });
                break;
            case AVERAGE:
                // Sort indices by distance in ascending order
                distances.sort((p1, p2) -> {
                    return p1.getSecond().compareTo(p2.getSecond());
                });
                break;
            default:
                throw new IllegalArgumentException("Unknown target type");
        }

        // Copy to set
        for(int i = 0; i < numTargets; i++) {
            targetIds.add(distances.get(i).getFirst());
        }
        
        // Done
        return targetIds;
    }
}
