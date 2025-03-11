package org.bihmi.phantomanonymization.phantom;

import java.util.List;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Properties;
import java.util.HashSet;

import org.bihmi.anonymization.config.AttributeConfig;
import org.bihmi.anonymization.config.DataConfig;
import org.bihmi.phantomanonymization.config.StatisticsConfig;
import org.deidentifier.arx.ARXClassificationConfiguration;
import org.deidentifier.arx.Data;
import org.deidentifier.arx.DataHandle;
import org.deidentifier.arx.aggregates.StatisticsBuilder;

public class StatisticsWrapper {

	/** Granularity */
	private final double granularity;
	
	/** Granularity of categorical attributes */
    private final double granularityCategoricalAttributes;
	
	/** Entropy */
	private final double entropy;
	
	/** Discernibility */
	private final double discernibility;
	
	/** Max. equivalence class */
	private final int maximalEquivalenceClassSize;
	
	/** Avg. equivalence class */
	private final double averageEquivalenceClassSize;
	
	/** Min. equivalence class */
	private final int minimalEquivalenceClassSize;
	
	/** Number of suppressed records */
	private final int numberOfSuppressedRecords;
	
	/** JSON encoded location and limits of numerical attributes */
	private final String locationAndLimits;
	
	/** JSON encoded location and limits of numerical attributes */
    private final double classificationAccuracy;
	
	/** Flag indicating initialization */
	private static boolean isInitialized = false;
	
	/** List of categorical attributes */
	private static List<String> categoricalAttributes;
	
	/** List of continuous attributes */
    private static List<String> continuousAttributes;
    
    /** Flag indicating whether classification is performed */
    private static boolean performClassification = false;
    
    /** List of attributes used as features for linear regression task */
    private static String[] featureAttributes;
    
    /** Attribute which class will be predicted  */
    private static String targetAttribute;
	
	
    /**
     * Static method to initialize the class. Must be called before creating instances.
     * Creates a list of categorical and continuous attributes using the dataConfig.
     *
     * @param dataConfig 
     * @param statisticsConfig
     */
    public static void initialize(DataConfig dataConfig, StatisticsConfig statisticsConfig) {
        if (!isInitialized) {
            List<String> categoricalAttributes = new ArrayList<String>();
            List<String> continuousAttributes = new ArrayList<String>();
            List<AttributeConfig> attributeConfigs = dataConfig.getAttributeConfigs();
            for (AttributeConfig attributeConfig : attributeConfigs) {
                switch (attributeConfig.getDataType()) {
                case "categorical":
                    categoricalAttributes.add(attributeConfig.getName());
                    break;
                case "continuous":
                    continuousAttributes.add(attributeConfig.getName());
                    break;
                default:
                    throw new IllegalStateException("Unsupported data type: " + attributeConfig.getDataType());
                }
            }
            StatisticsWrapper.categoricalAttributes = categoricalAttributes;
            StatisticsWrapper.continuousAttributes = continuousAttributes;
            isInitialized = true;
            
            if (statisticsConfig != null) {
                StatisticsWrapper.performClassification = true;
                StatisticsWrapper.featureAttributes = statisticsConfig.getFeatureAttributes();
                StatisticsWrapper.targetAttribute = statisticsConfig.getTargetAttribute();
            }
        }
    }
    
	
	/**
	 * Constructor used by the PAAssessment class to create the object using ARX StatisticsBuilder.
	 * 
	 * @param dataRaw
	 * @param handleAnonymized
	 */
	public StatisticsWrapper(Data dataRaw, DataHandle handleAnonymized){
	    if (!isInitialized) {
            throw new IllegalStateException("StatisticsWrapper is not initialized. Call initialize() first.");
        }
	    StatisticsBuilder statistics = handleAnonymized.getStatistics();
		this.granularity = statistics.getQualityStatistics().getGranularity().getArithmeticMean();
		this.granularityCategoricalAttributes = statistics.getQualityStatistics(new HashSet<String>(categoricalAttributes)).getGranularity().getArithmeticMean(); 
		this.entropy = statistics.getQualityStatistics().getNonUniformEntropy().getArithmeticMean();
		this.discernibility = statistics.getQualityStatistics().getDiscernibility().getValue();
		this.maximalEquivalenceClassSize = statistics.getEquivalenceClassStatistics().getMaximalEquivalenceClassSize();
		this.averageEquivalenceClassSize = statistics.getEquivalenceClassStatistics().getAverageEquivalenceClassSize();
		this.minimalEquivalenceClassSize = statistics.getEquivalenceClassStatistics().getMinimalEquivalenceClassSize();
		this.numberOfSuppressedRecords = statistics.getEquivalenceClassStatistics().getNumberOfSuppressedRecords();
		DataHandle handleRaw = dataRaw.getHandle();
		this.locationAndLimits = getLocationAndLimits(handleRaw, handleAnonymized);
		this.classificationAccuracy = StatisticsWrapper.performClassification ? performClassification(statistics) : -1d;
	}
	
    /**
     * Constructor used by the checkpoint class to create the object using Java properties
     * 
     * @param properties
     */
    public StatisticsWrapper(Properties properties) {
        this.granularity = Double.parseDouble(properties.getProperty("granularity"));
        this.granularityCategoricalAttributes = Double.parseDouble(properties.getProperty("granularityCategoricalAttributes"));
        this.entropy = Double.parseDouble(properties.getProperty("entropy"));
        this.discernibility = Double.parseDouble(properties.getProperty("discernibility"));
        this.maximalEquivalenceClassSize = Integer.parseInt(properties.getProperty("maximalEquivalenceClassSize"));
        this.averageEquivalenceClassSize = Double.parseDouble(properties.getProperty("averageEquivalenceClassSize"));
        this.minimalEquivalenceClassSize = Integer.parseInt(properties.getProperty("minimalEquivalenceClassSize"));
        this.numberOfSuppressedRecords = Integer.parseInt(properties.getProperty("numberOfSuppressedRecords"));
        this.locationAndLimits = properties.getProperty("locationAndLimits");
        this.classificationAccuracy = Double.parseDouble(properties.getProperty("classificationAccuracy"));
    }

    /**
     * Retrieves mean, median, minimum, and maximum values for a given attribute.
     * 
     * @param handle DataHandle object providing data access.
     * @param attributeName The name of the attribute to analyze.
     * @return An array containing mean, median, min, and max values.
     */
    private static double[] getStatistics(DataHandle handle, String attributeName) {
        int liveRows = 0;
        double sum = 0.0;
        double min = Double.NaN;
        double max = Double.NaN;
        ArrayList<Double> values = new ArrayList<>();
        int columnIndex = handle.getColumnIndexOf(attributeName);

        // Calculate min, max, and mean
        for (int row = 0; row < handle.getNumRows(); row++) {
            if (!handle.isSuppressed(row) && !handle.getValue(row, columnIndex).equals("*") ) {
                double value = Double.valueOf(handle.getValue(row, columnIndex));
                values.add(value);
                sum += value;
                if (Double.isNaN(min) || value < min) min = value;
                if (Double.isNaN(max) || value > max) max = value;
                liveRows++;
            }
        }

        double mean = (liveRows > 0) ? sum / liveRows : Double.NaN;

        // Calculating median
        double median = Double.NaN;
        if (liveRows > 0) {
            Collections.sort(values);
            int middle = values.size() / 2;
            if (values.size() % 2 == 0) {
                median = (values.get(middle - 1) + values.get(middle)) / 2.0;
            } else {
                median = values.get(middle);
            }
        }

        return new double[]{min, max, mean, median};
    }
    
    /**
     * Generates a JSON representation of statistical information for numeric attributes in the provided data handle.
     * The statistics include minimum, maximum, arithmetic mean, median, and standard deviation.
     *
     * @param handleRaw             data handle 
     * @param handleAnonymized      statistics builder
     * @return A JSON-formatted string representing statistical information for numeric attributes.
     */
    private static String getLocationAndLimits(DataHandle handleRaw, DataHandle handleAnonymized) {

        StringBuilder builder = new StringBuilder("{");
        boolean emptyResult = true;

        for (String attributeName : continuousAttributes) {

            if (emptyResult) {
                emptyResult = false;
            } else {
                builder.append(",");
            }

            double[] statsRaw = getStatistics(handleRaw, attributeName);
            double[] statsAnonymized = getStatistics(handleAnonymized, attributeName);
            
            builder.append("\"");
            builder.append(attributeName);
            builder.append("\":{\"min\":[\"");
            builder.append(formatDouble(statsRaw[0]));
            builder.append("\", \"");
            builder.append(formatDouble(statsAnonymized[0]));
            builder.append("\"],\"max\":[\"");
            builder.append(formatDouble(statsRaw[1]));
            builder.append("\", \"");
            builder.append(formatDouble(statsAnonymized[1]));
            builder.append("\"],\"aMean\":[\"");
            builder.append(formatDouble(statsRaw[2]));
            builder.append("\", \"");
            builder.append(formatDouble(statsAnonymized[2]));
            builder.append("\"],\"median\":[\"");
            builder.append(formatDouble(statsRaw[3]));
            builder.append("\", \"");
            builder.append(formatDouble(statsAnonymized[3]));
            builder.append("\"]}");

        }

        builder.append("}");

        return builder.toString();
    }
    
    /**
     * Performs classification task using linear regression.
     * 
     * @param statistics
     * @return
     */
    private Double performClassification(StatisticsBuilder statistics) {
        try {
            return statistics.getClassificationPerformance(StatisticsWrapper.featureAttributes, StatisticsWrapper.targetAttribute, ARXClassificationConfiguration.createLogisticRegression()).getAccuracy();
        } catch (ParseException e) {
            throw new IllegalStateException("Failed to perfrom classification", e);
        }
    }
    
    /**
     * Method to convert object to Properties
     * 
     * @return
     */
    public Properties toProperties() {
        Properties properties = new Properties();
        properties.setProperty("granularity", formatDouble(granularity));
        properties.setProperty("granularityCategoricalAttributes", formatDouble(granularityCategoricalAttributes));
        properties.setProperty("entropy", formatDouble(entropy));
        properties.setProperty("discernibility", formatDouble(discernibility));
        properties.setProperty("maximalEquivalenceClassSize", String.valueOf(maximalEquivalenceClassSize));
        properties.setProperty("averageEquivalenceClassSize", formatDouble(averageEquivalenceClassSize));
        properties.setProperty("minimalEquivalenceClassSize", String.valueOf(minimalEquivalenceClassSize));
        properties.setProperty("numberOfSuppressedRecords", String.valueOf(numberOfSuppressedRecords));
        properties.setProperty("locationAndLimits", locationAndLimits);
        properties.setProperty("classificationAccuracy", formatDouble(classificationAccuracy));
        return properties;
    }

    /**
     * Method to truncate double to 3 digits and handle null and NaN values
     * 
     * @param number
     * @return
     */
    private static String formatDouble(double number) {
        if (Double.isNaN(number)) {
            return "NaN";
        } else {
            return number != 0.0 ? String.format(Locale.US, "%.3f", number) : "0.000";
        }
    }
    
    @Override
    public String toString() {
        return formatDouble(granularity) + ";" +
               formatDouble(granularityCategoricalAttributes) + ";" +
               formatDouble(entropy) + ";" +
               formatDouble(discernibility) + ";" +
               maximalEquivalenceClassSize + ";" +
               formatDouble(averageEquivalenceClassSize) + ";" +
               minimalEquivalenceClassSize + ";" +
               numberOfSuppressedRecords + ";" +
               locationAndLimits+ ";" +
               formatDouble(classificationAccuracy);
    }
}
