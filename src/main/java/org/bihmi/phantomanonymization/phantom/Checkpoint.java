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

package org.bihmi.phantomanonymization.phantom;

import static org.bihmi.phantomanonymization.io.DataLoader.getNewDataWithSameConfig;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.util.Properties;

import org.bihmi.phantomanonymization.config.ConfigReader;
import org.bihmi.phantomanonymization.config.ConfigWriter;
import org.bihmi.phantomanonymization.config.RiskAssessmentConfig;
import org.bihmi.privacy.mgmt.anonymization.config.AnonymizationConfig;
import org.bihmi.privacy.mgmt.anonymization.config.DataConfig;
import org.deidentifier.arx.Data;
import org.deidentifier.arx.DataHandle;

import lombok.extern.slf4j.Slf4j;

/**
 * Loads and saves checkpoint data for risk assessment runs
 */
@Slf4j
public class Checkpoint {

    /**
     * Class specifying the different types of artifacts which can be saved by the checkpoint.
     * Each type is mapped to a postfix for the filenames used to store the data.
     */
    enum ArtifactType {
        
        /** Input data for training*/
        TRAIN_IN("train_in"),
        /** Output data for training*/
        TRAIN_OUT("train_out"),
        /** Input data for testing*/
        TEST_IN("test_in"),
        /** Output data for testing*/
        TEST_OUT("test_out"),
        /** Cohort data*/
        COHORT("cohort"),
        /** Background data*/
        BACKGROUND("background");
        
    	/** File name of the corresponding to the artifact type */
        protected String fileName;
        
        /**
         * Creates a new artifact type
         * @param fileName
         */
        ArtifactType(String fileName){
            this.fileName = fileName;
        }
    }
    
    /**
     * An exception indicating that the current RiskAssessment or Anonymization config is incompatible
     * with a saved checkpoint configurations.
     */
    public class IncompatibleConfigurationException extends RuntimeException {

    	/** SVUID */
		private static final long serialVersionUID = -8901467400576826613L;

		/**
         * Constructor.
         *
         * @param message
         */
        public IncompatibleConfigurationException(String message) {
            super(message);
        }
    }
    
    /**
     * An exception indicating an error during loading checkpoint data.
     */
    public class CheckpointLoadException extends RuntimeException {

    	/** SVUID */
		private static final long serialVersionUID = -5371490867848610906L;

        /**
         * Constructor.
         *
         * @param message 
         * @param cause 
         */
        public CheckpointLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /** Folder for checkpoints*/
    private final String checkpointFolderName;
    /** Whether to use unsaved data*/
    private boolean useSavedData;
    /** Available files*/
    private Set<String> availableFiles;
    
    /**
     * Creates a new instance. Checks configuration to see if checkpoint data is to be used or to be generated.
     * If checkpoint data is to be used, checks if existing checkpoint data matches configuration and throws exception if not.
     * 
     * @param dataConfig
     * @param anonymizationConfig 
     * @param riskAssessmentConfig 
     * @throws IOException When failing to load some file
     * @throws IllegalArgumentException When checkpoint data does not match the configuration
     */
    public Checkpoint(DataConfig dataConfig, AnonymizationConfig anonymizationConfig, RiskAssessmentConfig riskAssessmentConfig) throws IOException, IllegalArgumentException {
        
        // Let us assume that we use saved data
		this.useSavedData = true;
		
		// Check if data configuration exists
		String dataConfigDir = riskAssessmentConfig.getPathToCheckpointData() + "/" + dataConfig.getDataSetName();
    	if (!checkDataConfig(dataConfigDir, dataConfig)) {
    		ConfigWriter configWriter = new ConfigWriter();
            configWriter.writeDataConfig(dataConfigDir + "/DataConfig.yml", dataConfig);
            this.useSavedData = false;
    	} 
		
    	// Check if anonymization configuration exists
    	String anonymizationConfigDir = riskAssessmentConfig.getPathToCheckpointData() + "/" + dataConfig.getDataSetName() + "/" + anonymizationConfig.getName();
    	if (!checkAnonymizationConfig(anonymizationConfigDir, anonymizationConfig)) {
            ConfigWriter cfgWriter = new ConfigWriter();
            cfgWriter.writeAnonymizationConfig(anonymizationConfigDir + "/AnonymizationConfig.yml", anonymizationConfig);
            this.useSavedData = false;
    	}
    	
		// Check if risk assessment configuration exists
    	String riskAssessmentConfigDir = riskAssessmentConfig.getPathToCheckpointData() + "/" + dataConfig.getDataSetName() + "/" + anonymizationConfig.getName() + "/" + riskAssessmentConfig.getName();
    	if (!checkRiskAssessmentConfig(riskAssessmentConfigDir, riskAssessmentConfig)) {
    		ConfigWriter configWriter = new ConfigWriter();
            configWriter.writePipelineConfig(riskAssessmentConfigDir + "/RiskAssessmentConfig.yml", riskAssessmentConfig);
            this.useSavedData = false;
    	}
    	
    	// Determine folder name
    	this.checkpointFolderName = riskAssessmentConfigDir;
    	
    	// Prepare use of available files
    	if (this.useSavedData){
    		log.info("Found checkpoint data for: " + riskAssessmentConfigDir);
    		this.availableFiles = getAvailableFiles();
    	}
    }

    /**
     * Checks whether a specific cohort or background file is contained in the folder.
     * 
     * @param runNumber number of current run
     * @param artifactType artifact type
     * @return true, if file was found
     */
    public boolean checkExistence(int runNumber, ArtifactType artifactType) {
        // TODO: Should those files have an ending (e.g. ".checkpoint") as well?
    	String fileName = artifactType.fileName + "_" + runNumber;
    	return useSavedData && availableFiles.contains(fileName);
    }
    
    /**
     * Checks whether a specific sample file is contained in the folder.
     * 
     * @param target targets index in the dataset
     * @param runNumber number of current run
     * @param iterationNumber current training or test iteration
     * @param artifactType artifact type
     * @return true, if file was found
     */
    public boolean checkExistence(int target, int runNumber, int iterationNumber, ArtifactType artifactType) {
        // TODO: Should those files have an ending (e.g. ".checkpoint") as well?
    	String fileName = target + "_" + runNumber + "_" + iterationNumber  + "_" + artifactType.fileName;
    	return useSavedData && availableFiles.contains(fileName);
    }
    
    /**
     * Loads training or test data from file.
     * 
     * @param referenceData reference data
     * @param target targets index in the dataset
     * @param runNumber number of current run
     * @param iterationNumber current training or test iteration
     * @param caType artifact type
     * @return
     */
    public DataHandle loadData(Data referenceData, int target, int runNumber, int iterationNumber, ArtifactType artifactType) {
        
        // File path
        String path = checkpointFolderName + "/" + target + "_" + runNumber + "_" + iterationNumber + "_" + artifactType.fileName + ".data";
        try {
            return getNewDataWithSameConfig(path, referenceData, ';');
        } catch (IOException e) {
            throw new CheckpointLoadException("Could not read data from " + path, e);
        }
    } 
    
    /**
     * Loads cohort or training ids from file.
     * 
     * @param runNumber number of current run
     * @param artifactType artifact type
     * @return
     */
    public Set<Integer> loadData(int runNumber, ArtifactType artifactType){
        
        // TODO: Minor: why use a ".txt" extension here?
    	String path = checkpointFolderName + "/" +artifactType.fileName + "_" + runNumber + ".txt";
    	
    	// Load IDs
    	Set<Integer> ids = new HashSet<Integer>();
		try {
			BufferedReader reader = new BufferedReader(new FileReader(path));
			String line;
			while( (line = reader.readLine() ) != null) {
				ids.add(Integer.valueOf(line));
			}
			reader.close();
		} catch (IOException e) {
			throw new CheckpointLoadException("Could not read data from " + path, e);
		}
		
		// Done
    	return ids;
    }
    
    /**
     * Loads the statistics of a test dataset in checkpointFolder; defined by target, testRun and in/out.
     * 
     * @param target
     * @param runNumber
     * @param iteration
     * @param artifactType
     * @return
     */
    public StatisticsWrapper loadTestDataStatistics(int target, int runNumber, int iteration, ArtifactType artifactType) {
        String path = checkpointFolderName + "/" + target + "_" + runNumber + "_" + iteration + "_" + artifactType.fileName  + ".statistics";
        
        Properties properties = new Properties();
        try (FileInputStream fileInputStream = new FileInputStream(path)) {
            properties.load(fileInputStream);
        } catch (IOException e) {
        	throw new CheckpointLoadException("Could not read data from " + path, e);
        }
        return new StatisticsWrapper(properties);
        
    }

    /**
     * Saves the anonymized training or test data in to the checkpoint folder.
     * For testing data a file containing utility estimates is written as well.
     * 
     * @param target targets index in the dataset
     * @param runNumber number of current run
     * @param iteratioNumber current training or test iteration
     * @param data data to be saved
     * @param statistics
     * @param artifactType artifact type
     */
    public void saveData(int target, int runNumber, int iterationNumber, DataHandle data, StatisticsWrapper statistics, ArtifactType artifactType) {
        
        String pathToCurrentFile = checkpointFolderName + "/" + target + "_" + runNumber + "_" + iterationNumber  + "_" + artifactType.fileName;
        
        // Save data
        try {
            data.save(pathToCurrentFile + ".data");
        } catch (IOException e) {
        	log.error("Checkpoint failure. Failed to save data for: " + pathToCurrentFile, e);
        }
        
        // Save utility estimates
        if (artifactType == ArtifactType.TEST_IN || artifactType == ArtifactType.TEST_OUT) {
            try (FileOutputStream fileOutputStream = new FileOutputStream(pathToCurrentFile + ".statistics")){
            	statistics.toProperties().store(fileOutputStream, null);
            } catch (IOException e) {
            	log.error("Checkpoint failure. Failed to save statistics for: " + pathToCurrentFile, e);
            }
        }
    }
    
    /**
     * Overloaded method to save the anonymized training data without statistics in the checkpoint folder.
     * 
     * @param target targets index in the dataset
     * @param runNumber number of the current run
     * @param iterationNumber current training or test iteration
     * @param data data to be saved
     * @param artifactType artifact type
     */
    public void saveData(int target, int runNumber, int iterationNumber, DataHandle data, ArtifactType artifactType) { 
    	saveData(target, runNumber, iterationNumber, data, null, artifactType);
    }
    
    /**
     * Saves cohort or training ids to file.
     * 
     * @param runNumber
     * @param ids
     * @param artifactType
     */
    public void saveData(int runNumber, Set<Integer> ids, ArtifactType artifactType){
        
        // TODO: Minor: why use a ".txt" extension here?
        // Path
    	String pathToCurrentFile = checkpointFolderName + "/" +artifactType.fileName + "_" + runNumber + ".txt";
    	try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(pathToCurrentFile));
        	Iterator<Integer> it = ids.iterator();
        	while(it.hasNext()) {
        		writer.write(it.next().toString());
        		writer.newLine();
        	}
            writer.close();
        } catch (IOException e) {
        	log.error("Checkpoint failure: Failed to save " + artifactType.fileName + " for: " + pathToCurrentFile, e);
        }
    }
    
    
    /**
     * Checks for an existing data config. If it exists, it checks whether the existing one is compatible with the given config.
     * 
     * @param dataConfigDir
     * @param dataConfig
     * @return
     */
    private boolean checkDataConfig(String dataConfigDir, DataConfig dataConfig) {
        
        // Path
    	Path path = Paths.get(dataConfigDir);
    	
    	// Create directories
    	try {
            Files.createDirectories(path);
        } catch (IOException e) {
            log.error("Could not create checkpoint/data directory at " + path + " " + e);
        }
    	
    	// Load saved config
    	DataConfig savedConfig;
    	try {
    		ConfigReader reader = new ConfigReader();
            savedConfig = reader.readDataConfig(path + "/DataConfig.yml");
        } catch (IOException e) {
        	log.warn("No DataConfig.yml found in " + path);
            return false;
        }

    	// Check for compatibility
        if (   !dataConfig.getDataSetName().equalsIgnoreCase(savedConfig.getDataSetName())
        	|| !dataConfig.getDataCsvFile().equalsIgnoreCase(savedConfig.getDataCsvFile())
        	|| dataConfig.getAttributeConfigs().size() != savedConfig.getAttributeConfigs().size()) {
            
        	throw new IncompatibleConfigurationException("Current data config (" + dataConfig.getDataSetName() + ") not compatible with saved config (" + savedConfig.getDataSetName() + ")");
        }
        return true;
    }
    
    /**
     * Checks for an existing anonymization config. If it exists, it checks whether the existing one is compatible with the given config.
     * 
     * @param anonymizationConfigDir
     * @param anonymizationConfig
     * @return
     */
    private boolean checkAnonymizationConfig(String anonymizationConfigDir, AnonymizationConfig anonymizationConfig) {
        
        // Path
    	Path path = Paths.get(anonymizationConfigDir);
    	
    	// Create directories
    	try {
            Files.createDirectories(path);
        } catch (IOException e) {
            log.error("Could not create checkpoint/anonymization directory at " + checkpointFolderName + "/" + anonymizationConfig.getName() +" : " + e);
        }
    	
    	// Load saved config
    	AnonymizationConfig savedConfig;
    	try {
            ConfigReader reader = new ConfigReader();
            savedConfig = reader.readAnonymizationConfig(path + "/AnonymizationConfig.yml");
        } catch (IOException e) {
        	log.warn("No AnonymizationConfig.yml found in " + path);
            return false;
        }

    	// Check for compatibility
        if (   !anonymizationConfig.getName().equalsIgnoreCase(savedConfig.getName())
        	|| Double.compare(anonymizationConfig.getSuppressionLimit(), savedConfig.getSuppressionLimit()) != 0
        	|| !anonymizationConfig.getHeuristicSearchStepLimit().equals(savedConfig.getHeuristicSearchStepLimit())
       		|| !anonymizationConfig.getHeuristicSearchTimeLimit().equals(savedConfig.getHeuristicSearchTimeLimit())
       		|| anonymizationConfig.getAnonymizationAlgorithm() != savedConfig.getAnonymizationAlgorithm()
       		|| anonymizationConfig.getLocalGeneralizationIterations() != savedConfig.getLocalGeneralizationIterations()
       		|| anonymizationConfig.isLocalGeneralization() != savedConfig.isLocalGeneralization()) {
            
        	throw new IncompatibleConfigurationException("Current anonymization config (" + anonymizationConfig.getName() + ") not compatible with saved config (" + savedConfig.getName() + ")");
        }
        return true;
    }

    /**
     * Checks for an existing risk assessment configuration.
     * If it exists, it checks whether the existing one is compatible with the given configuration.
     * 
     * @param riskAssessmentConfigDir
     * @param riskAssessmentConfig
     * @return
     */
    private boolean checkRiskAssessmentConfig(String riskAssessmentConfigDir, RiskAssessmentConfig riskAssessmentConfig) {
        
        // Path
    	Path path = Paths.get(riskAssessmentConfigDir);
    	
    	// Create directories
    	try {
            Files.createDirectories(path);
        } catch (IOException e) {
        	log.error("Could not create checkpoint directory at " + checkpointFolderName + ": " + e);
        }

    	// Load existing configuration
    	RiskAssessmentConfig savedRiskAssessmentConfiguration;
    	try {
            ConfigReader reader = new ConfigReader();
            savedRiskAssessmentConfiguration = reader.readRiskAssessmentConfig(path + "/RiskAssessmentConfig.yml");
        } catch (IOException e) {
            log.warn("No RiskAssessmentConfig.yml found in " + riskAssessmentConfig.getPathToCheckpointData());
            return false;
        }
    	
    	// Check for compatibility
        if (   Double.compare(riskAssessmentConfig.getSizeCohortFraction(), savedRiskAssessmentConfiguration.getSizeCohortFraction()) != 0 
            || riskAssessmentConfig.getSizeCohort() != savedRiskAssessmentConfiguration.getSizeCohort()
            || Double.compare(riskAssessmentConfig.getSizeBackgroundFraction(), savedRiskAssessmentConfiguration.getSizeBackgroundFraction()) != 0
            || riskAssessmentConfig.getSizeBackground() != savedRiskAssessmentConfiguration.getSizeBackground()
            || Double.compare(riskAssessmentConfig.getOverlap(), savedRiskAssessmentConfiguration.getOverlap()) != 0
            || riskAssessmentConfig.getSizeSampleTraining() != savedRiskAssessmentConfiguration.getSizeSampleTraining()
            || riskAssessmentConfig.getSizeSampleTest() != savedRiskAssessmentConfiguration.getSizeSampleTest()
            || riskAssessmentConfig.getTargetType() != savedRiskAssessmentConfiguration.getTargetType()) {
            
        	throw new IncompatibleConfigurationException("Current risk assessment config (" + riskAssessmentConfig.getName() + ") not compatible with saved config " + savedRiskAssessmentConfiguration.getName() +")");
        }
        
        // We can carry on
        return true;
    }
    
    /**
     * Creates a set containing the names of all files in the checkpoint folder.
     * 
     * @return Set of string containing file names
     */
    private Set<String> getAvailableFiles(){
        File checkpointFolder = new File(checkpointFolderName);
        File[] files = checkpointFolder.listFiles();
        Set<String> availableFiles = new HashSet<>();
        for (File file : files) {
        	// Add filename to set without extension
            // TODO: Why? Weird...
        	availableFiles.add(file.getName().replaceFirst("[.][^.]+$", ""));
        }
        return availableFiles;
    }
}