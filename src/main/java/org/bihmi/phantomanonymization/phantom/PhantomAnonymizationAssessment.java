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

import com.google.common.util.concurrent.AtomicDouble;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.LinkedBlockingQueue;

import org.bihmi.anonymization.anon.AnonymizationMethods;
import org.bihmi.anonymization.anon.AnonymizationMethods.AnonymizationMethod;
import org.bihmi.anonymization.config.AnonymizationConfig;
import org.bihmi.anonymization.config.AttributeConfig;
import org.bihmi.anonymization.config.DataConfig;
import org.bihmi.anonymization.data.DataLoader;
import org.bihmi.phantomanonymization.config.RiskAssessmentConfig;
import org.bihmi.phantomanonymization.config.StatisticsConfig;
import org.bihmi.phantomanonymization.features.Dictionary;
import org.bihmi.phantomanonymization.features.FeatureType;
import org.bihmi.phantomanonymization.io.ReportWriter;
import org.bihmi.phantomanonymization.target.TargetSelection;
import org.apache.commons.math3.util.Pair;
import org.deidentifier.arx.Data;
import org.deidentifier.arx.DataHandle;

import java.io.IOException;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Class to perform risk assessment with multiple threads
 *
 * @author Thierry Meurers
 * @author Fabian Prasser
 */
@Slf4j
public class PhantomAnonymizationAssessment {
    

    /**
     * Thread to perform a risk assessment run
     *
     * @author Thierry Meurers
     * @author Fabian Prasser
     */
    class BenchmarkThread implements Runnable {

        /** Data of whole population*/
        private final Data population;

        /**
         * Creates new thread
         */
        BenchmarkThread(Data population){
            this.population = population;
        }

        /**
         * Executes jobs
         */
        @Override
        public void run() {
            
            // The job to run
            Job job;
            
            // Perform jobs until done
            while ((job = jobQueue.poll()) != null && isRunning() ) {
                
                // Prepare
                Set<String> resultLines = new HashSet<>();
                job.population = population;
                
                // Execute
                List<Job.Result> results = execute(job);
                
                // Collect results
                for (Job.Result result : results) {
                     
                    // Update stats
                    trueGuesses.get(job.targetId).getAndAdd(result.predictedLabel == result.trueLabel ? 1 : 0);
                     
                    // Add result lines for log file
                    resultLines.add(job.runID + ";" + job.targetId + ";" + result);
                }

                // Write to log file
                reportWriter.writeToLogFile(resultLines);
            }
        }
    }
    
    /**
     * Class which holds all properties of a run
     */
    class Job {
        
        /**
         * Class which holds test result of a single assessment 
         */
        class Result {

            /** Test iteration within a run */
            int iteration;
            /** True label of the sample */
            int trueLabel;
            /** Predicted label of the sample */
            int predictedLabel;
            /** Probability of the classifiers prediction */
            double predictionProbability;
            /** Statistics wrapper */
            StatisticsWrapper statistics;
            
            /**
             * Creates a new instance
             * @param iteration number of test
             * @param trueLabel expected label for the sample
             * @param prediction predicted label for the test sample
             * @param statistics metrics of the anonymized data
             */
            Result(int iteration, boolean trueLabel, Pair<Boolean, Double> prediction, StatisticsWrapper statistics) {
                this.iteration = iteration;
                this.trueLabel = trueLabel ? 1 : 0;
                this.predictedLabel = prediction.getFirst() ? 1 : 0;
                this.predictionProbability = prediction.getSecond();
                this.statistics = statistics;
            }

            @Override
            public String toString() {
                return iteration + ";" + trueLabel + ";" + predictedLabel + ";" + predictionProbability + ";" + statistics.toString();
            }
        }
        
        /** Data of whole population*/
        Data population; 
        /** Indices of records which are part of cohort */
        Set<Integer> cohortIds; 
        /** Indices of records which are part of background */
        Set<Integer> backgroundIds;
        /** Index of target record */
        int targetId; 
        /** Anonymization method used */
        AnonymizationMethods.AnonymizationMethod anonymizationMethod; 
        /** Risk assessment config used */
        RiskAssessmentConfig riskAssessmentConfig; 
        /** Number of run */
        int runID;
        /** List of results (one per each test sample) */
        List<Result> results = new ArrayList<>();
        
        /**
         * Creates a new instance
         * @param cohortIds Indices of records which are part of cohort
         * @param backgroundIds Indices of records which are part of background
         * @param targetId Index of target record
         * @param anonymizationMethod Anonymization method used
         * @param riskAssessmentConfig Risk assessment config used
         * @param testRunID Number of TestRun
         */
        Job(Set<Integer> cohortIds,
            Set<Integer> backgroundIds,
            int targetId,
            AnonymizationMethod anonymizationMethod,
            RiskAssessmentConfig riskAssessmentConfig,
            int testRunID) {
                this.cohortIds = cohortIds;
                this.backgroundIds = backgroundIds;
                this.targetId = targetId;
                this.anonymizationMethod = anonymizationMethod;
                this.riskAssessmentConfig = riskAssessmentConfig;
                this.runID = testRunID;
        }
        
        /**
         * Adds a new result to the run.
         * 
         * @param iteration number of test
         * @param trueLabel expected label for the sample
         * @param prediction predicted label for the test sample
         * @param granularity granularity of the anonymized data
         */
        void addResult(int iteration, boolean trueLabel, Pair<Boolean, Double> prediction, StatisticsWrapper statistics) {
            results.add(new Result(iteration, trueLabel, prediction, statistics));
        }
    }
    
    @SuppressWarnings("serial")
	public static Set<String> getAttributesToConsider(AttributesForAttackType attributesForAttack, Data referenceData) {
        switch (attributesForAttack) {
            case ALL_ATTRIBUTES:
                return new HashSet<String>(){{
                    addAll(referenceData.getDefinition().getQuasiIdentifyingAttributes());
                    addAll(referenceData.getDefinition().getIdentifyingAttributes());
                    addAll(referenceData.getDefinition().getSensitiveAttributes());
                    addAll(referenceData.getDefinition().getInsensitiveAttributes());
                }};
            case ALL_QIS:
                return referenceData.getDefinition().getQuasiIdentifyingAttributes();
        }
        throw new RuntimeException("No valid anonymization method specified");
    }

    /**
     * Adds names of attributes to the dictionary
     * @param attributeConfigs
     * @return
     */
    public static Dictionary getFilledDictionaryFromAttributeConfig(List<AttributeConfig> attributeConfigs){
        Dictionary dictionary = new Dictionary();
        for (AttributeConfig attributeConfig : attributeConfigs) {
            if (attributeConfig.getInclude() && "categorical".equalsIgnoreCase(attributeConfig.getDataType())
                    && attributeConfig.getPossibleEntries() != null) {
                for (String value : attributeConfig.getPossibleEntries()) {
                    dictionary.probe(attributeConfig.getName(), value);
                }
            }
        }
        return dictionary;
    }

    /** Dictionary */
    private final Dictionary dictionary;

    /** Risk assessment configuration */
    private RiskAssessmentConfig riskAssessmentConfig;

    /** Data configuration */
    private DataConfig dataConfig;

    /** Report writer */
    private ReportWriter reportWriter;

    /**  Number of true guesses for targets */
    private final Map<Integer, AtomicInteger> trueGuesses = new HashMap<>();

    /** An array of threads that make up the thread pool for concurrent execution */
    private final Thread[] threadPool;

    /** Flag indicating whether the assessment is currently running */
    private boolean running = true;

    /** The number of runs required */
    private final int runsRequired;

    /** Atomic counter for the number of runs executed so far */
    private final AtomicInteger runsExecuted = new AtomicInteger();

    /** Atomic value representing the progress step */
    private final AtomicDouble progressStep = new AtomicDouble();

    /** The method of anonymization to be used */
    private final AnonymizationMethods.AnonymizationMethod anonymizationMethod;

    /** The type of feature to use */
    private final FeatureType featureType;

    /** Checkpoint management */
    private Checkpoint checkpoint;

    /** Flag to determine whether checkpoints are used in the process */
    private final boolean useCheckpoint;

    /** Size of the cohort involved in the process */
    private final int cohortSize;

    /** Size of the background dataset */
    private final int backgroundSize;

    /** A queue of jobs to be processed, maintained in a thread-safe manner */
    private final Queue<Job> jobQueue = new LinkedBlockingQueue<>();

    /**
     * Creates a new instance and starts processing
     * @param threadCount
     * @param resultDirectory
     * @param assessmentName
     * @param riskAssessmentConfig
     * @param anonymizationConfig
     * @param dataConfig
     * @param featureType
     * @throws IOException
     * @throws ParseException
     */
    public PhantomAnonymizationAssessment(int threadCount,
                                          String resultDirectory,
                                          String assessmentName,
                                          RiskAssessmentConfig riskAssessmentConfig,
                                          AnonymizationConfig anonymizationConfig,
                                          DataConfig dataConfig,
                                          StatisticsConfig statisticsConfig,
                                          FeatureType featureType) throws IOException, ParseException {
        
        // Store settings
        this.riskAssessmentConfig = riskAssessmentConfig;
        this.anonymizationMethod = AnonymizationMethods.CONFIG_ANONYMIZATION(anonymizationConfig);
        this.featureType = featureType;
        this.dataConfig = dataConfig;
        this.reportWriter = new ReportWriter(resultDirectory, assessmentName, riskAssessmentConfig, anonymizationConfig, dataConfig);
        this.runsRequired = 2 * riskAssessmentConfig.getTargetCount() * riskAssessmentConfig.getRunCount() * (riskAssessmentConfig.getRunTrainingCount() + riskAssessmentConfig.getRunTestCount());

        // Create dataset
        Data referenceDataset = DataLoader.getData(dataConfig);

        // Set/calculate absolute cohort and background size
        int populationSize = referenceDataset.getHandle().getNumRows();
        if (riskAssessmentConfig.getSizeCohortFraction() > 0) {
            cohortSize = (int) (riskAssessmentConfig.getSizeCohortFraction() * populationSize);
        } else {
            cohortSize = riskAssessmentConfig.getSizeCohort();
        }
        if (riskAssessmentConfig.getSizeBackgroundFraction() > 0) {
            backgroundSize = (int) (riskAssessmentConfig.getSizeBackgroundFraction() * populationSize);
        } else {
            backgroundSize = riskAssessmentConfig.getSizeBackground();
        }
        
        // Get targets
        TargetSelection targetSelection = new TargetSelection(referenceDataset);
        Set<Integer> targets = targetSelection.getTargets(riskAssessmentConfig.getTargetType(), riskAssessmentConfig.getTargetCount(), riskAssessmentConfig.getTargetImportFile());

        // Initialize checkpoint and set flag if checkpoint will be used
        if(riskAssessmentConfig.getUseCheckpointData()) {
        	this.checkpoint = new Checkpoint(dataConfig, anonymizationConfig, riskAssessmentConfig);
        	this.useCheckpoint = true; 
        } else {
        	this.useCheckpoint = false; 
        }
        
        // Initialize map of guesses
        for(Integer target : targets) {
            trueGuesses.put(target, new AtomicInteger(0));
        }

        // Initialize dictionary
        this.dictionary = getFilledDictionaryFromAttributeConfig(dataConfig.getAttributeConfigs());
        dictionary.addAllPossibleHierarchyValues(dataConfig.getAttributeConfigs(), referenceDataset);

        log.info("Assessment will require " + this.runsRequired + " anonymizations and is executed with " + threadCount + " threads.");

        // Create jobs
        createJobs(riskAssessmentConfig, referenceDataset, targets);
        
        // Initialize statistics wrapper
        StatisticsWrapper.initialize(dataConfig, statisticsConfig);

        // Create threads
        threadPool = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threadPool[i] = new Thread(new BenchmarkThread(DataLoader.getData(dataConfig)));
        }
    }

    /**
     * Called to start the threads
     */
    public void execute() throws InterruptedException, IOException, ParseException {
        
        // Print config
        log.info(riskAssessmentConfig.toString());
        
        // Start threads
        for (Thread t : threadPool) {
            t.start();
        }
        
        // Wait for threads to finish (throws InterruptedException)
        for (Thread t : threadPool) {
            t.join();
        }
        
        // Write Summary file
        reportWriter.writeSummaryFile(riskAssessmentConfig, dataConfig, trueGuesses);
    }
    
    /**
     * Executes a job, i.e. trains a prediction model for the target specified and evaluates the trained classifier.
     * @param job
     * @return
     */
    public List<Job.Result> execute(Job job) {
        
        // Prepare
        Set<String> attributesToConsider = getAttributesToConsider(riskAssessmentConfig.getAttributesForAttack(), job.population);
        // TODO: Maybe this should be configurable in risk assessment config
        Dictionary clonedDictionary = dictionary.clone();
        MembershipPredictionModel model = new MembershipPredictionModel(dataConfig.getAttributeConfigs(), clonedDictionary);

        // Perform training
        for (int k = 0; k < riskAssessmentConfig.getRunTrainingCount(); k++) {

            // Anonymize training data or load anonymized data using checkpoint function
            DataHandle rTrainOutHandle;
            DataHandle rTrainInHandle;
			if (this.useCheckpoint
				&& checkpoint.checkExistence(job.targetId, job.runID, k, Checkpoint.ArtifactType.TRAIN_OUT)
				&& checkpoint.checkExistence(job.targetId, job.runID, k, Checkpoint.ArtifactType.TRAIN_IN)) {
			    
			    // Load from checkpoint
				rTrainOutHandle = checkpoint.loadData(job.population, job.targetId, job.runID, k, Checkpoint.ArtifactType.TRAIN_OUT);
				rTrainInHandle = checkpoint.loadData(job.population, job.targetId, job.runID, k, Checkpoint.ArtifactType.TRAIN_IN);
            } else {
                
                // Draw sample (trainOut) without target and create a copy of it with target (trainIn)
                Set<Integer> trainOut = getSubSample(job.backgroundIds, riskAssessmentConfig.getSizeSampleTraining());
                Set<Integer> trainIn = getSampleWithTarget(trainOut, job.targetId);
                Data rTrainOutRaw = getCopy(job.population, trainOut); 
                Data rTrainInRaw = getCopy(job.population, trainIn); 
                rTrainOutHandle = anonymize(rTrainOutRaw, anonymizationMethod);
                rTrainInHandle = anonymize(rTrainInRaw, anonymizationMethod);
                if (this.useCheckpoint) {
                    checkpoint.saveData(job.targetId, job.runID, k, rTrainOutHandle, Checkpoint.ArtifactType.TRAIN_OUT);
                    checkpoint.saveData(job.targetId, job.runID, k, rTrainInHandle, Checkpoint.ArtifactType.TRAIN_IN);
                }
            }
			
            // Train
            model.train(rTrainOutHandle, false, featureType, attributesToConsider);
            model.train(rTrainInHandle, true, featureType, attributesToConsider);
            
            // Release DataHandle
            rTrainOutHandle.release();
            rTrainInHandle.release();
        }

        // Create array to store granularity of testSamples
        StatisticsWrapper[] statistics = new StatisticsWrapper[riskAssessmentConfig.getRunTestCount()*2];
        
        // Perform tests
        for(int k = 0; k < riskAssessmentConfig.getRunTestCount(); k++) {

            // Anonymize training data or load anonymized data using checkpoint function
            DataHandle rTestOutHandle;
            DataHandle rTestInHandle;
            StatisticsWrapper statisticsOut;
            StatisticsWrapper statisticsIn;
			if (this.useCheckpoint
			    && checkpoint.checkExistence(job.targetId, job.runID, k, Checkpoint.ArtifactType.TEST_OUT)
				&& checkpoint.checkExistence(job.targetId, job.runID, k, Checkpoint.ArtifactType.TEST_IN)) {
			    
			    // Load from checkpoint
                rTestOutHandle = checkpoint.loadData(job.population, job.targetId, job.runID, k, Checkpoint.ArtifactType.TEST_OUT);
                rTestInHandle = checkpoint.loadData(job.population, job.targetId, job.runID, k, Checkpoint.ArtifactType.TEST_IN);
                statisticsOut = checkpoint.loadTestDataStatistics(job.targetId, job.runID, k, Checkpoint.ArtifactType.TEST_OUT);
                statisticsIn = checkpoint.loadTestDataStatistics(job.targetId, job.runID, k, Checkpoint.ArtifactType.TEST_IN);
            } else {
                
                // Calculate
                Set<Integer> testOut = getSubSample(job.cohortIds, riskAssessmentConfig.getSizeSampleTest());
                Set<Integer> testIn = getSampleWithTarget(testOut , job.targetId);
                Data rTestOutRaw = getCopy(job.population, testOut); 
                Data rTestInRaw = getCopy(job.population, testIn); 
                rTestOutHandle = anonymize(rTestOutRaw, anonymizationMethod);
                rTestInHandle = anonymize(rTestInRaw, anonymizationMethod);
                statisticsOut = new StatisticsWrapper(rTestOutRaw, rTestOutHandle);
                statisticsIn = new StatisticsWrapper(rTestInRaw, rTestInHandle);
                if (this.useCheckpoint) {
                    checkpoint.saveData(job.targetId, job.runID, k, rTestOutHandle, statisticsOut, Checkpoint.ArtifactType.TEST_OUT);
                    checkpoint.saveData(job.targetId, job.runID, k, rTestInHandle, statisticsIn, Checkpoint.ArtifactType.TEST_IN);
                }
            }
			
			// Store statistics
			statistics[2*k] = statisticsOut;
			statistics[2*k+1] = statisticsIn;
			
			// Store test data
			model.test(rTestOutHandle, featureType, attributesToConsider);
			model.test(rTestInHandle, featureType, attributesToConsider);
			
            // Release
            rTestOutHandle.release();
            rTestInHandle.release();
        }
        
        // Perform prediction
		Pair<Boolean, Double>[] prediction = model.predict(featureType, riskAssessmentConfig.getClassifierType(), attributesToConsider);
        
        // Store results
        for(int k = 0; k < riskAssessmentConfig.getRunTestCount(); k++) {
        	job.addResult(k, false, prediction[k*2], statistics[k*2]);
        	job.addResult(k, true, prediction[k*2+1], statistics[k*2+1]);
        }
        
        // Keep track of progress
        int runsExecuted = this.runsExecuted.addAndGet(2 * (riskAssessmentConfig.getRunTrainingCount() + riskAssessmentConfig.getRunTestCount()));
        if ((double) runsExecuted/runsRequired >= progressStep.get()) {
            log.info("Progress: " + runsExecuted + "/" + this.runsRequired);
            progressStep.addAndGet(0.1);
        }
        
        // Done
        return job.results;
    }

    /**
     * Call to interrupt benchmark
     */
    public void stop() {
        running = false;
    }

    /**
     * Perform the anonymization
     */
    private DataHandle anonymize(Data dataset,  AnonymizationMethods.AnonymizationMethod anonymization) {
        return anonymization.anonymize(dataset);
    }

    /**
     * Fills the lists with jobs, distinguished by targetId, and test run number.
     * Jobs also get a cohort and background, since these remain identical within a testrun.
     *
     * @param riskAssessmentConfig
     * @param population
     * @param targetIds
     */
    private void createJobs(RiskAssessmentConfig riskAssessmentConfig, Data population, Set<Integer> targetIds) {
        
        // For each run
    	 for(int testRunID = 0; testRunID < riskAssessmentConfig.getRunCount(); testRunID++) {
    	     
    	     // Prepare
    		 Set<Integer> cohortIds;
    		 Set<Integer> backgroundIds;
    		 
    		 // Load from checkpoint
    		 if(useCheckpoint && checkpoint.checkExistence(testRunID, Checkpoint.ArtifactType.COHORT) && checkpoint.checkExistence(testRunID, Checkpoint.ArtifactType.BACKGROUND)) {
                 cohortIds = checkpoint.loadData(testRunID, Checkpoint.ArtifactType.COHORT);
                 backgroundIds = checkpoint.loadData(testRunID, Checkpoint.ArtifactType.BACKGROUND);
             
             // Configure
    		 } else {
    		     
	             // Get train sample and adversary population
	             Pair<Set<Integer>, Set<Integer>> samples = getSampleWithOverlap(population, cohortSize, backgroundSize, riskAssessmentConfig.getOverlap());
	             // Sample without target
	             cohortIds = samples.getFirst();
	             // Sample adversary population
	             backgroundIds = samples.getSecond();
	             if(useCheckpoint) {
	            	 checkpoint.saveData(testRunID, cohortIds, Checkpoint.ArtifactType.COHORT);
	            	 checkpoint.saveData(testRunID, backgroundIds, Checkpoint.ArtifactType.BACKGROUND);
	             }
    		 }
    		 
             // For each target
             for (int targetId : targetIds) {
                 
            	 // remove target from cohort and background
            	 Set<Integer> cohortIdsOut = removeTargetFromSample(cohortIds, targetId);
            	 Set<Integer> backgroundIdsOut = removeTargetFromSample(backgroundIds, targetId);
            	 
                 // Create job
            	 Job job = new Job(cohortIdsOut, backgroundIdsOut, targetId, anonymizationMethod, riskAssessmentConfig, testRunID);
            	 jobQueue.add(job);
    		 }
    	 }
    }

    /**
     * Create a copy of data
     */
    private static Data getCopy(Data dataset, Set<Integer> indices) {
        DataHandle handle = dataset.getHandle();
        List<String[]> rows = new ArrayList<>(indices.size() + 1);  // + 1 for header
        rows.add(handle.iterator().next());
        for (int row=0; row < handle.getNumRows(); row++) {
            if (indices.contains(row)) {
                rows.add(getRow(handle, row));
            }
        }
        Data result = Data.create(rows);
        result.getDefinition().read(dataset.getDefinition());
        return result;
    }

    /**
     * Extracts a row from the handle
     */
    private static String[] getRow(DataHandle handle, int row) {
        String[] result = new String[handle.getNumColumns()];
        for (int column = 0; column < result.length; column++) {
            result[column] = handle.getValue(row, column);
        }
        return result;
    }

    /**
     * Creates samples for the test sample and the adversary reference data excluding the targets.
     * The overlap defines the fractions of records from the test sample which are also in the adversary reference data.
     * 
     * @param data Population
     * @param targets target IDs
     * @param testSampleSize Size of test sample
     * @param aRefSize Size of the advesary reference data
     * @param overlap fraction of overlapping records
     * @return First returned set is the test sample; Second returned set is the adversary reference data
     */
    private static Pair<Set<Integer>, Set<Integer>> getSampleWithOverlap(Data data, int testSampleSize, int aRefSize, double overlap) {
        
        // Number of records which do only appear in test sample and NOT in adversary reference data
        int numberRecordsDistinct = testSampleSize - (int)(testSampleSize * overlap);
        
        // Shuffled indices
        List<Integer> population = new ArrayList<>();
        for (int row = 0; row < data.getHandle().getNumRows(); row++) {
            population.add(row);
        }
        Collections.shuffle(population, new Random());
        
        // Extract
        List<Integer> testSample = population.subList(0, testSampleSize);
        List<Integer> aRef = population.subList(numberRecordsDistinct, numberRecordsDistinct+aRefSize);
        Collections.shuffle(aRef, new Random());
        
        // Return
        return new Pair<>(new HashSet<>(testSample), new HashSet<>(aRef));
    }

    /**
     * Removes the specified targt id from a set of ids if it is present.
     *
     * @param sample the input set of integers
     * @param id the target id to be removed from the  set
     * @return a modified set without the specified target ID if it was found, otherwise returns the original set
     */
    public static Set<Integer> removeTargetFromSample(Set<Integer> sample, Integer id) {
        // Create a copy of the input set to avoid modifying the original set
        Set<Integer> modifiedSample = new HashSet<>(sample);
        
        // Check if id is in the sample set
        if (modifiedSample.contains(id)) {
            // If id is present, remove it from the set
            modifiedSample.remove(id);
        }
        
        // Return the modified set (or original set if id was not found)
        return modifiedSample;
    }
    
    /**
     * Adds the target to the sample
     */
    private static Set<Integer> getSampleWithTarget(Set<Integer> samples, int target) {
        
        // Prepare
        Set<Integer> result = new HashSet<>(samples);
        
        // Remove one random element
        int index = new Random().nextInt(result.size() - 1) + 1;
        Iterator<Integer> iter = result.iterator();
        for (int i = 0; i < index; i++) {
            iter.next();
        }
        iter.remove();
        
        // Add target
        result.add(target);
        
        // Done
        return result;
    }

    /**
     * Obtain a subset from a set.
     */
    private static Set<Integer> getSubSample(Set<Integer> samples, int subSampleSize){
        subSampleSize = Math.min(subSampleSize, samples.size());
    	List<Integer> list = new ArrayList<>(samples);
        Collections.shuffle(list, new Random());
        return new HashSet<>(list.subList(0, subSampleSize));
    }

    /**
     * Called by thread to see if benchmark was interrupted.
     */
    private boolean isRunning() {
        return running;
    }
}
