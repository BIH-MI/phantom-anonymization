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

package org.bihmi.phantomanonymization.io;

import org.deidentifier.arx.Data;
import org.deidentifier.arx.DataHandle;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Loads data
 */
public class DataLoader extends org.bihmi.anonymization.data.DataLoader {

    /**
     * Loads a dataset given a reference definition
     *
     * @param path path of file do be loaded
     * @param reference data, definition will be copied
     * @param delimiter delimiter for loading data
     * 
     * @return DataHandle
     * @throws IOException
     */
    public static DataHandle getNewDataWithSameConfig(String path, Data reference, char delimiter) throws IOException {
        DataHandle handle = Data.create(path, Charset.defaultCharset(), delimiter).getHandle();
        handle.getDefinition().read(reference.getDefinition());
        return handle;
    }
}
