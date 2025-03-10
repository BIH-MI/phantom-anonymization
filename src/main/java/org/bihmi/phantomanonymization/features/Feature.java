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

import org.apache.commons.lang3.time.DateUtils;
import org.deidentifier.arx.DataHandle;
import org.deidentifier.arx.DataType;

import java.text.ParseException;
import java.util.Date;
import java.util.Map;

/**
 * Base for features
 */
public abstract class Feature {

    /** Compile feature data*/
    public abstract double[] compile();


    /**
     * Sanity check to ensure consistency of data types
     */
    void checkDataType(String attribute, DataType<?> type, Map<String, DataType<?>> dataTypes ) {
        DataType<?> _type = dataTypes.get(attribute);
        if (_type == null) {
            dataTypes.put(attribute, type);
        } else if (!(_type.equals(type))) {
            throw new IllegalArgumentException("Inconsistent data type detected for attribute: " + attribute);
        }
    }

    /**
     * Return double for numerical values
     */
    double getDouble(DataHandle handle, int row, int column, Class<?> _clazz) {
        try {
            // Calculate depending on data type
            if (_clazz.equals(Long.class)) {
                return handle.getLong(row, column);
            } else if (_clazz.equals(Double.class)) {
                String value = handle.getValue(row, column);
                if (value != null && !value.equals("*")) {
                    return Double.valueOf(handle.getValue(row, column));
                }
                return Double.NaN;
            } else if (_clazz.equals(Date.class)) {
                DataType.ARXDate dataType = (DataType.ARXDate) handle.getDefinition().getDataType(handle.getAttributeName(column));
                String value = handle.getValue(row, column);
                return DateUtils.parseDate(value, dataType.getLocale(), dataType.getFormat()).getTime();
            } else {
                throw new IllegalStateException("Attribute is not numeric");
            }
        } catch (ParseException e) {
            // TODO Why caused by short heuristic searches?
            throw new IllegalStateException(e);
        }
    }

    /**
     * Transforms array of arrays to flatten array
     */
    double[] getFlattenedArray(double[]... input) {
        
        // Calculate size of flatten array
        int outputLength = 0;
        for(double[] part : input) {
            outputLength += part.length;
        }

        // Copy into flatten array
        double[] output = new double[outputLength];
        int posOutput = 0;
        for(double[] part : input) {
            for(double value : part) {
                output[posOutput++] = value;
            }
        }
        return output;
    }
}