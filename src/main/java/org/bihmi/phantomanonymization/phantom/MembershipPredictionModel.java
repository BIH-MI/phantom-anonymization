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

import org.apache.commons.math3.util.Pair;
import org.bihmi.anonymization.config.AttributeConfig;
import org.bihmi.phantomanonymization.features.*;
import org.deidentifier.arx.DataHandle;
import org.deidentifier.arx.DataType;
import smile.classification.DecisionTree.SplitRule;
import smile.classification.KNN;
import smile.classification.LogisticRegression;
import smile.classification.RandomForest;
import smile.classification.SoftClassifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.ArrayList;

/**
 * Membership prediction
 */
public class MembershipPredictionModel {

    /** Number of bins to use for histogram feature */
    private final static int NUM_BINS = 10;
    
    /** Classifier */
    private SoftClassifier<double[]> classifier;
    
    /** Compiled */
    private boolean compiled = false;
    
    /** To ensure consistency of data types */
    private final Map<String, DataType<?>> dataTypes = new HashMap<>();
    
    /** Dictionary */
    private final Dictionary dictionary;
    
    /** Attribute Config for whole dataset */
    private final List<AttributeConfig> attributeConfigs;
    
    /** Training data */
    private final Map<Feature, Boolean> trainingData = new HashMap<>();
    
    /** Test data */
    private final List<Feature> testData = new ArrayList<>();

    /**
     * Creates a new instance
     * @param attributeConfigs
     * @param dictionary
     */
    public MembershipPredictionModel(List<AttributeConfig> attributeConfigs, Dictionary dictionary) {
        this.dictionary = dictionary;
        this.attributeConfigs = attributeConfigs;
    }

    /**
     * Predicts for all datasets whether the target is included
     */
    public Pair<Boolean, Double>[] predict(FeatureType featureType, ClassifierType classifierType, Set<String> attributesToConsider) {
        
        // Check
        if (this.trainingData.isEmpty()) {
            throw new IllegalStateException("No training data has been provided");
        }
        
        // Check
        if (this.testData.isEmpty()) {
            throw new IllegalStateException("No test data has been provided");
        }

        // Actually train the classifier
        if (!compiled) {
            compile(classifierType);
        }

        double[][] xValues = new double[testData.size()][];
        for (int i = 0; i < testData.size(); i++) {
            xValues[i] = testData.get(i).compile();
        }

        // Prepare
        @SuppressWarnings("unchecked")
        Pair<Boolean, Double>[] result = new Pair[testData.size()];
        for (int i = 0; i < testData.size(); i++) {

            // Predict label
            double[] probabilities = new double[]{0, 0};
            int target = classifier.predict(xValues[i], probabilities);

            double confidence = probabilities[target];
            result[i] = new Pair<>(target == 1, confidence);
        }
        // Done
        return result;
    }

    /**
     * Train the the classifier
     */
    public void train(DataHandle data, boolean targetIncluded, FeatureType featureType, Set<String> attributesToConsider) {
        
        // Check
        if (compiled) {
            throw new IllegalStateException("Classifier already compiled to perform predictions");
        }

        // Store training data
        Feature features = getFeatures(data, featureType, attributesToConsider);
        trainingData.put(features, targetIncluded);
    }
    
    /**
     * Add test samples to classifier
     */
    public void test(DataHandle data, FeatureType featureType, Set<String> attributesToConsider) {
	    // Check
	    if (compiled) {
	        throw new IllegalStateException("Classifier already compiled to perform predictions");
	    }
	
	    // Store test data
	    Feature features = getFeatures(data, featureType, attributesToConsider);
	    testData.add(features);
    }
    
    /**
     * Called before predictions
     */
    private void compile(ClassifierType classifierType) {

        // Prepare
        double[][] xTrain = new double[trainingData.size()][];
        int[] yTrain = new int[trainingData.size()];

        // Collect data
        int index = 0;
        int featureSize = Integer.MIN_VALUE;
        for (Entry<Feature, Boolean> data : trainingData.entrySet()) {

            // Store
            xTrain[index] = data.getKey().compile();
            yTrain[index] = data.getValue() ? 1 : 0;

            // Sanity checks
            if (featureSize == Integer.MIN_VALUE) {
                featureSize = xTrain[index].length;
            } else if (featureSize != xTrain[index].length) {
                throw new IllegalArgumentException("Inconsistent feature size: " + featureSize + " and " + xTrain[index].length);
            }

            // Next
            index++;
        }

        // Train
        switch (classifierType) {
            case KNN:
                // TODO relocated to main and to configuration eventually
                int numberOfNeighbors = 5; // 5 is used by Stadler et al.
                classifier = KNN.learn(xTrain, yTrain, numberOfNeighbors, null);
                break;
            case LR:
                classifier = new LogisticRegression(xTrain, yTrain, null);
                break;
            case RF:
                // TODO relocated to main and to configuration eventually
                int numberOfTrees = 100; // sklearn default := 100 | ARX default := 500
                int maxNumberOfLeafNodes = Integer.MAX_VALUE; // sklean default := +INF | ARX default = 100;
                int minSizeOfLeafNodes = 1; // sklean default := 1 | ARX default := 5
                int numberOfVariablesToSplit = (int) Math.floor(Math.sqrt(xTrain[0].length)); // sklearn := auto (i.e. sqrt(#features)) | ARX default := 0
                double subSample = 1d; // skleanr --> provided at total number (2) | ARX default := 1d
                SplitRule splitRule = SplitRule.GINI; // sklearn default := GINI | ARX dedault: = GINI
                classifier = new RandomForest(null, xTrain, yTrain, numberOfTrees, maxNumberOfLeafNodes, minSizeOfLeafNodes, numberOfVariablesToSplit, subSample, splitRule, null);
                break;
            default:
                throw new RuntimeException("Classifier not supported");
        }
        
        this.compiled = true;
    }

    /**
     * Calculates features
     */
    private Feature getFeatures(DataHandle handle, FeatureType featureType, Set<String> attributesToConsider) {
        switch (featureType) {
            case ENSEMBLE:
                return new FeatureEnsemble(handle, attributesToConsider, attributeConfigs, dictionary, dataTypes, NUM_BINS);
            case CORRELATION:
                return new FeatureCorrelation(handle, attributesToConsider, attributeConfigs, dictionary);
            case HISTOGRAM:
                return new FeatureHistogram(handle, attributesToConsider, attributeConfigs, dictionary, dataTypes, NUM_BINS);
            case NAIVE:
                return new FeatureNaive(handle, attributesToConsider, attributeConfigs, dictionary, dataTypes);
            default:
                throw new IllegalArgumentException("Unknown feature!");
        }
    }
}