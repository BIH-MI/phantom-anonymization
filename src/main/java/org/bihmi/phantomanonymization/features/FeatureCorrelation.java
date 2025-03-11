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

import org.apache.commons.math3.linear.OpenMapRealMatrix;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.bihmi.anonymization.config.AttributeConfig;
import org.deidentifier.arx.DataHandle;
import org.deidentifier.arx.DataType;

import java.util.*;

/**
 * Correlation feature
 */
public class FeatureCorrelation extends Feature {

    /** Features */
    private final Map<String, OpenMapRealMatrix> categorical = new HashMap<>();
    /** Features */
    private final Map<String, double[]>          numeric     = new HashMap<>();
    /** Rows */
    private final int                            rows;
    /** Dictionary*/
    private final Dictionary                     dictionary;
    /** Attribute configs*/
    private final List<AttributeConfig>          attributeConfigs;
    /** Attributes to consider*/
    private final Set<String>                    attributesToConsider;


    /**
     * Calculates correlation features using Pearson's product-moment correlation.
     * All columns of continuous attributes are directly used for correlation calculation.
     * Categorical and ordinal attributes are transferred to a sparse representation
     * were each value becomes an own column and whether (or not) the value applies to a row
     * is indicated by the value 1d (or 0d). NaNs are replaced by 0D in the final result.
     */
    public FeatureCorrelation(DataHandle handle, Set<String> attributesToConsider, List<AttributeConfig> attributeConfigs, Dictionary dictionary) {
        
        // Prepare
        this.attributeConfigs = attributeConfigs;
        this.dictionary = dictionary;
        this.attributesToConsider = attributesToConsider;
        
        // Check which rows are not suppressed
        boolean[] rowLive = new boolean[handle.getNumRows()];
        int numLiveRows = 0;
        for(int row = 0; row < handle.getNumRows(); row++) {
            if(!handle.isSuppressed(row)) {
                rowLive[row] = true;
                numLiveRows++;
            }
        }

        // Prepare
        this.rows = numLiveRows;

        // For each attribute
        for (AttributeConfig attributeConfig : attributeConfigs) {
            if (!attributeConfig.getInclude() || !attributesToConsider.contains(attributeConfig.getName())) {
                continue;
            }

            String attribute = attributeConfig.getName();
            // Obtain attribute details
            int column = handle.getColumnIndexOf(attribute);
            String attributeName = handle.getAttributeName(column);
            DataType<?> _type = handle.getDefinition().getDataType(attributeName);
            Class<?> _clazz = _type.getDescription().getWrappedClass();

            // Just store numeric values as is
            if (_clazz.equals(Long.class) || _clazz.equals(Double.class) || _clazz.equals(Date.class)) {

                // Create array
                double[] values = new double[numLiveRows];

                // Copy values as double
                int pos = 0;
                for (int row = 0; row < values.length; row++) {
                    if (rowLive[row]) {
                        values[pos++] = getDouble(handle, row, column, _clazz);
                    }
                }

                // Store
                numeric.put(attribute, values);

            } else if (_clazz.equals(String.class)) {

                // Probe all values in advance
                // (For ensuring the matrix is initialized with the required dimensions)
                int[] _values = new int[numLiveRows];
                int pos = 0;
                for (int row = 0; row < handle.getNumRows(); row++) {
                    if (rowLive[row]) {
                        _values[pos++] = dictionary.probe(attribute, handle.getValue(row, column));
                    }
                }

                // Create matrix
                OpenMapRealMatrix matrix = new OpenMapRealMatrix(numLiveRows, dictionary.size(attribute));

                // Store values
                for (int row = 0; row < numLiveRows; row++) {
                    matrix.setEntry(row, _values[row], 1);
                }

                // Store
                categorical.put(attribute, matrix);
            }
        }
    }

    @Override
    public double[] compile() {

        // Count columns
        int columns = 0;

        // For each attribute
        for (AttributeConfig attributeConfig : attributeConfigs) {
            if (!attributeConfig.getInclude() || !attributesToConsider.contains(attributeConfig.getName())) {
                continue;
            }
            String attribute = attributeConfig.getName();
            if (numeric.containsKey(attribute)) {
                columns++;
            } else {
                columns+=dictionary.size(attribute);
            }
        }

        // Prepare matrix
        OpenMapRealMatrix matrix = new OpenMapRealMatrix(rows, columns);
        int column = 0;
        for (AttributeConfig attributeConfig : attributeConfigs) {
            if (!attributeConfig.getInclude() || !attributesToConsider.contains(attributeConfig.getName())) {
                continue;
            }
            String attribute = attributeConfig.getName();
            // Copy numeric data
            if (numeric.containsKey(attribute)) {
                double[] values = numeric.get(attribute);
                for (int row = 0; row < rows; row++) {
                    matrix.setEntry(row, column, values[row]);
                }
                column++;

                // Copy categorical data
            } else {
                OpenMapRealMatrix _matrix = categorical.get(attribute);
                for (int _column = 0; _column < dictionary.size(attribute); _column++) {
                    if (_column < _matrix.getColumnDimension()) {
                        for (int row = 0; row < rows; row++) {
                            matrix.setEntry(row, column, _matrix.getEntry(row, _column));
                        }
                    }
                    column++;
                }
            }
        }

        // Calculate
        double[][] result = new PearsonsCorrelation().computeCorrelationMatrix(matrix).getData();
        // Done
        return getUpperTriangleNoNaNs(result);
    }


    /**
     * Removes the lower triangle of the matrix, since the values of lower and upper triangle are the same.
     * Replaces NaN values with 0.
     * @param matrix representation of a correlation matrix
     * @return
     */
    double[] getUpperTriangleNoNaNs(double[][] matrix) {
        double[] result = new double[(matrix.length * matrix.length - matrix.length)/2];
        int k = 0;
        for (int i=0; i<matrix.length-1; i++) {
            for (int j=0; j<matrix.length - i - 1; j++) {
                if (Double.isNaN(matrix[i][j])) {
                    result[k] = 0d;
                } else {
                    result[k] = matrix[i][j];
                }
                k++;
            }
        }
        return result;
    }
}