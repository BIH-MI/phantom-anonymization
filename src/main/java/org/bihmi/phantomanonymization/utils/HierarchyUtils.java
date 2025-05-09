package org.bihmi.phantomanonymization.utils;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;
import lombok.extern.slf4j.Slf4j;
import org.bihmi.anonymization.config.DataConfig;
import org.bihmi.anonymization.data.DataLoader;
import org.deidentifier.arx.*;
import org.deidentifier.arx.criteria.KAnonymity;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
public class HierarchyUtils {

    private static final char CSV_DELIMITER = ';';

    /**
     * Normalizes a string value based on its ARX DataType.
     * For numeric types, it attempts to convert to a canonical string representation
     * (e.g., "0.0" becomes "0").
     *
     * @param value The string value to normalize.
     * @param dataType The ARX DataType of the value.
     * @return The normalized string value, or the original value if normalization is not applicable or fails.
     */
    private static String normalizeValue(String value, DataType<?> dataType) {
        if (value == null || "*".equals(value)) {
            return value;
        }
        if (DataType.DECIMAL.equals(dataType) || DataType.INTEGER.equals(dataType)) {
            try {
                BigDecimal bd = new BigDecimal(value);
                return bd.stripTrailingZeros().toPlainString(); // "1.2300" -> "1.23", "123.0" -> "123", "0.0" -> "0"
            } catch (NumberFormatException e) {
                // If parsing fails (e.g., value is a range string like "[0-10]" or other non-numeric text),
                // return the original value. This allows non-numeric specific values to be handled by exact match.
                log.trace("Value '{}' (type: {}) could not be parsed to BigDecimal for normalization. Returning as is.", value, dataType);
                return value;
            }
        } 
        return value;
    }

    /**
     * Generates a new hierarchy for a numerical attribute by replacing interval strings
     * with the MEDIAN of data points (calculated by ARX microaggregation)
     * that fall into those generalizations.
     *
     * @param dataConfig             The data configuration for loading the dataset.
     * @param attributeName          The name of the numerical attribute.
     * @param outputHierarchyFilePath Path to save the generated median CSV hierarchy file.
     * @throws IOException  If file operations fail.
     * @throws CsvException If CSV parsing fails.
     */
    public static void generateMedianHierarchy(DataConfig dataConfig,
                                               String attributeName,
                                               String outputHierarchyFilePath) throws IOException, CsvException {

        log.info("Starting median hierarchy generation for attribute '{}' from data config '{}'", attributeName, dataConfig.getDataSetName());
        
        // Load data
        Data originalData = DataLoader.getData(dataConfig);
        DataHandle originalHandle = originalData.getHandle(); // Keep for reading original values throughout
        int targetAttributeIndexInOriginal = originalHandle.getColumnIndexOf(attributeName);

        // Check if attribute is present
        if (targetAttributeIndexInOriginal == -1) {
            log.error("Attribute '{}' not found in the dataset", attributeName);
            originalHandle.release(); // Release handle before throwing
            throw new IllegalArgumentException("Attribute '" + attributeName + "' not found in the dataset.");
        }

        // Check for numeric data type
        DataType<?> dataType = originalData.getDefinition().getDataType(attributeName);
        boolean isNumericOrDate = DataType.DECIMAL.equals(dataType) || DataType.INTEGER.equals(dataType) || DataType.DATE.equals(dataType);
        if (!isNumericOrDate) {
            log.warn("Attribute '{}' (type: {}) is not strictly numerical or date-like. Median aggregation might produce unexpected results or fail. " +
                            "Ensure data is clean and ARX median function supports this type.",
                    attributeName, dataType);
        }

        // Check if hierarchy is configured
        String inputHierarchyFilePath = dataConfig.getAttributeConfigs().get(targetAttributeIndexInOriginal).getPathToHierarchy();
        if (inputHierarchyFilePath == null || inputHierarchyFilePath.trim().isEmpty()) {
            originalHandle.release();
            throw new IllegalArgumentException("Path to input hierarchy for attribute '" + attributeName + "' is not configured or is empty.");
        }
        
        // Read hierarchy as csv
        log.info("Reading input hierarchy from '{}'", inputHierarchyFilePath);
        List<String[]> inputHierarchyRows = readCsv(inputHierarchyFilePath, CSV_DELIMITER);
        if (inputHierarchyRows.isEmpty()) {
            originalHandle.release();
            throw new IllegalArgumentException("Input hierarchy file is empty: " + inputHierarchyFilePath);
        }

        // Determine max generalization levels
        int highestGeneralizationLevel = inputHierarchyRows.get(0).length - 1; 
        if (highestGeneralizationLevel < 1) {
            originalHandle.release();
            throw new IllegalArgumentException("Input hierarchy file must have at least 2 columns (specific value + at least one generalization level). File: '" + inputHierarchyFilePath);
        }

        // Create a deep copy for the output hierarchy structure
        List<String[]> outputHierarchyRows = new ArrayList<>();
        for (String[] row : inputHierarchyRows) {
            outputHierarchyRows.add(Arrays.copyOf(row, row.length));
        }

        ARXConfiguration baseArxConfig = ARXConfiguration.create();
        baseArxConfig.addPrivacyModel(new KAnonymity(1));
        baseArxConfig.setAlgorithm(ARXConfiguration.AnonymizationAlgorithm.BEST_EFFORT_BOTTOM_UP);
        baseArxConfig.setHeuristicSearchStepLimit(1); // No actual search needed, fixed generalization
        ARXAnonymizer anonymizer = new ARXAnonymizer();

        for (int generalizationLevel = 1; generalizationLevel < highestGeneralizationLevel + 1; generalizationLevel++) {
            // The CSV column index in the hierarchy file that corresponds to this ARX generalization level
            log.info("Processing ARX generalization level {} (updates CSV column {}) for attribute '{}' using MEDIAN microaggregation.",
                    generalizationLevel, generalizationLevel, attributeName);

            Map<String, String> normalizedOriginalValueToGeneralizedValueMap = new HashMap<>();

            Data tempData;
            DataHandle inputHandle = null;
            DataHandle outputHandle = null;

            try {
                tempData = Data.create(originalData.getHandle().iterator());
                inputHandle = tempData.getHandle(); // Get handle for potential release
                DataDefinition dataDefinition = tempData.getDefinition();

                // configure attribute
                dataDefinition.setDataType(attributeName, dataType); 
                
                // configure generalization
                AttributeType.Hierarchy originalInputHierarchy = originalData.getDefinition().getHierarchyObject(attributeName);
                dataDefinition.setHierarchy(attributeName, originalInputHierarchy.clone());
                dataDefinition.setAttributeType(attributeName, AttributeType.QUASI_IDENTIFYING_ATTRIBUTE);
                dataDefinition.setMinimumGeneralization(attributeName, generalizationLevel);
                dataDefinition.setMaximumGeneralization(attributeName, generalizationLevel);
                dataDefinition.setMicroAggregationFunction(attributeName, AttributeType.MicroAggregationFunction.createMedian(), true); // performClustering = true

                // "Anonymize", i.e. aggregate using median
                ARXResult arxResult = anonymizer.anonymize(tempData, baseArxConfig);
                outputHandle = arxResult.getOutput();

                int targetAttributeIndexInOutput = outputHandle.getColumnIndexOf(attributeName);

                for (int r = 0; r < originalHandle.getNumRows(); r++) {

                    String originalValue = originalHandle.getValue(r, targetAttributeIndexInOriginal);
                    String normalizedOriginalValue = normalizeValue(originalValue, dataType);

                    if (normalizedOriginalValue != null) { // Should not be null if originalSpecificValue isn't, but check
                        String generalizedValue = outputHandle.getValue(r, targetAttributeIndexInOutput);
                        normalizedOriginalValueToGeneralizedValueMap.put(normalizedOriginalValue, generalizedValue);
                    } else {
                        log.warn("Original specific value '{}' (row {}) for attribute '{}' normalized to null. Skipping for median map.", originalValue, r, attributeName);
                    }
                }
            } catch (Exception e) { // Catch ARX processing errors, IOException, etc.
                log.error("Error during ARX microaggregation for attribute '{}' at level {}: {}. Skipping this level's update.", attributeName, generalizationLevel, e.getMessage(), e);
            } finally {
                if (outputHandle != null) {
                    outputHandle.release();
                }
                if (inputHandle != null) {
                    inputHandle.release();
                }
            }

            // Populate the output hierarchy file for the current generalization level
            for (int i = 0; i < outputHierarchyRows.size(); i++) {
                String[] outputRow = outputHierarchyRows.get(i);
                String originalValue = outputRow[0]; // Specific value is always in column 0
                String normalizedOriginalValue = normalizeValue(originalValue, dataType);

                if (normalizedOriginalValue == null) {
                    log.warn("Specific value '{}' from input hierarchy (row {}, col 0) normalized to null for attribute '{}'. Cannot lookup median. Keeping original hierarchy value '{}' at column {}.",
                            originalValue, i, attributeName, outputRow[generalizationLevel], generalizationLevel);
                    // Value is already from inputHierarchyRows via deep copy, so no change needed if lookup fails critically before map access.
                    continue;
                }

                String generalizedValue = normalizedOriginalValueToGeneralizedValueMap.get(normalizedOriginalValue);

                if (generalizedValue != null) {
                    outputRow[generalizationLevel] = generalizedValue;
                } else {
                    // Median not found for this specific value at this level.
                    // This is unexpected and should  not happen.
                    log.warn("Median for specific value '{}' (normalized: '{}', attr: '{}', ARX level: {}) not found in map. " +
                                    "Keeping original hierarchy value: '{}' in output column {}.",
                            originalValue, normalizedOriginalValue, attributeName, generalizationLevel,
                            inputHierarchyRows.get(i)[generalizationLevel], // Original value from input
                            generalizationLevel);
                    throw new RuntimeException("Unexpected error!");
                }
            }
        } // end loop over generalization levels

        originalHandle.release(); // Release the main original data handle

        log.info("Writing generated median hierarchy to '{}'", outputHierarchyFilePath);
        writeCsv(outputHierarchyRows, outputHierarchyFilePath, CSV_DELIMITER);
        log.info("Median hierarchy generation_complete for attribute '{}'.", attributeName);
    }

    private static List<String[]> readCsv(String filePath, char delimiter) throws IOException, CsvException {
        try (FileReader fileReader = new FileReader(filePath, StandardCharsets.UTF_8);
             CSVReader csvReader = new CSVReaderBuilder(fileReader)
                     .withCSVParser(new CSVParserBuilder().withSeparator(delimiter).build())
                     .build()) {
            return csvReader.readAll();
        }
    }

    private static void writeCsv(List<String[]> data, String filePath, char delimiter) throws IOException {
        try (FileWriter fileWriter = new FileWriter(filePath, StandardCharsets.UTF_8); // Use StandardCharsets here too
             CSVWriter writer = new CSVWriter(fileWriter, // Pass FileWriter directly
                     delimiter,
                     CSVWriter.NO_QUOTE_CHARACTER,
                     CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                     CSVWriter.DEFAULT_LINE_END)) {
            writer.writeAll(data);
        }
    }
}