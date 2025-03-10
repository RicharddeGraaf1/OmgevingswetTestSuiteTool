package nl.omgevingswet;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.*;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class MainController {

    @FXML private TextField sourceZipField;
    @FXML private TextField targetZipField;
    @FXML private CheckBox customOutputCheck;
    @FXML private VBox customOutputBox;
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;
    @FXML private TextArea logArea;
    
    // Nieuwe velden voor metadata weergave
    @FXML private TextArea metadataArea;
    @FXML private Button analyzeButton;

    @FXML
    public void initialize() {
        // Set default path to example file
        String inputPath = new File("InputVoorbeeld/response.zip").getAbsolutePath();
        sourceZipField.setText(inputPath);
        
        // Voeg listener toe voor source file wijzigingen
        sourceZipField.textProperty().addListener((observable, oldValue, newValue) -> {
            analyzeButton.setDisable(newValue == null || newValue.trim().isEmpty());
            if (!customOutputCheck.isSelected()) {
                updateDefaultTargetPath(new File(newValue));
            }
        });
    }

    @FXML
    private void handleSourceBrowse() {
        File file = showFileChooser("Selecteer bron ZIP-bestand");
        if (file != null) {
            sourceZipField.setText(file.getAbsolutePath());
            if (!customOutputCheck.isSelected()) {
                updateDefaultTargetPath(file);
            }
        }
    }

    @FXML
    private void handleTargetBrowse() {
        File file = showFileChooser("Selecteer uitvoerlocatie");
        if (file != null) {
            targetZipField.setText(file.getAbsolutePath());
        }
    }

    @FXML
    private void handleCustomOutputToggle() {
        customOutputBox.setVisible(customOutputCheck.isSelected());
        if (!customOutputCheck.isSelected()) {
            File sourceFile = new File(sourceZipField.getText());
            if (sourceFile.exists()) {
                updateDefaultTargetPath(sourceFile);
            }
        }
    }

    private void updateDefaultTargetPath(File sourceFile) {
        String sourcePath = sourceFile.getAbsolutePath();
        String parentPath = sourceFile.getParent();
        String targetPath = parentPath + File.separator + "InitieleAanlevering.zip";
        targetZipField.setText(targetPath);
    }

    @FXML
    private void handleTransform() {
        String sourcePath = sourceZipField.getText();
        
        if (sourcePath.isEmpty()) {
            showError("Selecteer eerst een bronbestand.");
            return;
        }

        // Genereer het doel pad als het niet is ingesteld
        String targetPath = targetZipField.getText();
        if (targetPath.isEmpty()) {
            File sourceFile = new File(sourcePath);
            updateDefaultTargetPath(sourceFile);
            targetPath = targetZipField.getText();
        }

        try {
            File sourceFile = new File(sourcePath);
            if (!sourceFile.exists()) {
                showError("Bronbestand bestaat niet: " + sourcePath);
                return;
            }

            File targetFile = new File(targetPath);
            File targetDir = targetFile.getParentFile();
            if (targetDir != null && !targetDir.exists()) {
                targetDir.mkdirs();
            }

            transformZipFile(sourcePath, targetPath);
        } catch (Exception e) {
            logError("Fout tijdens transformatie: " + e.getMessage());
            e.printStackTrace(); // Voor debugging
        }
    }

    private void transformZipFile(String sourcePath, String targetPath) throws IOException {
        progressBar.setVisible(true);
        statusLabel.setText("Bezig met transformeren...");
        
        // Maak het rapport bestand pad
        String reportPath = targetPath.substring(0, targetPath.lastIndexOf('.')) + "_rapport.txt";
        StringBuilder reportContent = new StringBuilder();
        reportContent.append("Rapport Omgevingswet Test Suite Tool\n");
        reportContent.append("================================\n\n");
        
        try (ZipFile sourceZip = new ZipFile(sourcePath);
             ZipOutputStream targetZip = new ZipOutputStream(Files.newOutputStream(Paths.get(targetPath)));
             BufferedWriter reportWriter = Files.newBufferedWriter(Paths.get(reportPath))) {
            
            List<ZipEntry> entries = sourceZip.stream().collect(Collectors.toList());
            int totalFiles = entries.size();
            int processedFiles = 0;

            // Zoek eerst het Metadata.xml en Identificatie.xml bestand
            boolean metadataFound = false;
            boolean identificatieFound = false;
            Map<String, String> metadata = new HashMap<>();
            
            for (ZipEntry entry : entries) {
                String entryName = entry.getName();
                
                if (entryName.equals("Regeling/Metadata.xml")) {
                    logMessage("Verwerken van metadata: " + entryName);
                    byte[] content = sourceZip.getInputStream(entry).readAllBytes();
                    Map<String, String> metadataValues = MetadataProcessor.processMetadata(content);
                    metadata.putAll(metadataValues);
                    metadataFound = true;
                }
                else if (entryName.equals("Regeling/Identificatie.xml")) {
                    logMessage("Verwerken van identificatie: " + entryName);
                    byte[] content = sourceZip.getInputStream(entry).readAllBytes();
                    Map<String, String> identificatieValues = MetadataProcessor.processIdentificatie(content);
                    metadata.putAll(identificatieValues);
                    identificatieFound = true;
                }
            }

            // Analyseer GML bestanden
            MetadataProcessor.GMLInfo gmlInfo = MetadataProcessor.analyzeGMLFiles(sourceZip);
            metadata.put("gml-count", String.valueOf(gmlInfo.count));
            metadata.put("gml-total-size", formatFileSize(gmlInfo.totalSize));
            metadata.put("gml-files", String.join("\n", gmlInfo.fileNames));

            // Verwerk IO mappen
            Map<String, IOProcessor.IOContent> ioContents = IOProcessor.processIOFolders(sourceZip);
            int ioFoldersProcessed = 0;

            // Genereer besluit.xml
            try {
                byte[] besluitXml = BesluitProcessor.createBesluitXml(sourceZip);
                ZipEntry besluitEntry = new ZipEntry("besluit.xml");
                targetZip.putNextEntry(besluitEntry);
                targetZip.write(besluitXml);
                targetZip.closeEntry();
                logMessage("besluit.xml gegenereerd");
                reportContent.append("besluit.xml gegenereerd\n\n");
            } catch (Exception e) {
                logError("Fout bij genereren besluit.xml: " + e.getMessage());
                e.printStackTrace();  // Voor debugging
                reportContent.append("Waarschuwing: Kon besluit.xml niet genereren\n\n");
            }

            // Schrijf metadata naar rapport
            reportContent.append("Metadata informatie:\n");
            reportContent.append("-------------------\n");
            
            // Bevoegd gezag informatie
            if (metadata.containsKey("bevoegdgezag-code")) {
                reportContent.append("Bevoegd gezag code: ").append(metadata.get("bevoegdgezag-code")).append("\n");
                reportContent.append("Type: ").append(metadata.get("bevoegdgezag-type")).append("\n\n");
            }

            // Identificatie informatie
            if (metadata.containsKey("FRBRWork")) {
                reportContent.append("Identificatie:\n");
                reportContent.append("FRBRWork: ").append(metadata.get("FRBRWork")).append("\n");
            }
            if (metadata.containsKey("FRBRExpression")) {
                reportContent.append("FRBRExpression: ").append(metadata.get("FRBRExpression")).append("\n\n");
            }

            // Citeertitel
            if (metadata.containsKey("citeerTitel")) {
                reportContent.append("Citeertitel: ").append(metadata.get("citeerTitel")).append("\n\n");
            }

            // GML informatie
            if (metadata.containsKey("gml-count")) {
                reportContent.append("GML Bestanden:\n");
                reportContent.append("Aantal GML bestanden: ").append(metadata.get("gml-count")).append("\n");
                reportContent.append("Totale grootte: ").append(metadata.get("gml-total-size")).append("\n");
                if (metadata.containsKey("gml-files") && !metadata.get("gml-files").isEmpty()) {
                    reportContent.append("\nGML bestandslijst:\n");
                    reportContent.append(metadata.get("gml-files")).append("\n");
                }
                reportContent.append("\n");
            }

            // Waarschuwingen
            if (!metadataFound) {
                reportContent.append("Waarschuwing: Geen Metadata.xml bestand gevonden in de Regeling map\n");
            }
            if (!identificatieFound) {
                reportContent.append("Waarschuwing: Geen Identificatie.xml bestand gevonden in de Regeling map\n");
            }
            reportContent.append("\n");

            // Verwerk alle bestanden
            reportContent.append("Bestandstransformaties:\n");
            reportContent.append("----------------------\n");
            int owFilesCount = 0;
            int gmlFilesCount = 0;

            // Houd bij welke IO-mappen we al hebben verwerkt
            Set<String> processedIOFolders = new HashSet<>();

            for (ZipEntry entry : entries) {
                String entryName = entry.getName();
                
                // Skip als het een directory is
                if (entryName.endsWith("/")) {
                    processedFiles++;
                    continue;
                }
                
                // Sla pakbon.xml over
                if (entryName.equals("pakbon.xml")) {
                    logMessage("Pakbon.xml overgeslagen");
                    processedFiles++;
                    continue;
                }

                // Bepaal de nieuwe entry naam en of we het bestand moeten verwerken
                String newEntryName = entryName;
                boolean shouldProcess = true;

                // Check eerst op GML bestanden, ongeacht in welke map ze zitten
                if (entryName.toLowerCase().endsWith(".gml")) {
                    // Haal alleen de bestandsnaam op (laatste deel van het pad)
                    newEntryName = new File(entryName).getName();
                    gmlFilesCount++;
                    logMessage("GML-bestand verplaatst naar root: " + newEntryName);
                    reportContent.append("Verplaatst: ").append(entryName).append(" -> ").append(newEntryName).append("\n");
                }
                // Controleer of dit een bestand in een IO-map is
                else if (entryName.matches("IO-\\d+/.*")) {
                    String ioNumber = entryName.substring(3, entryName.indexOf('/', 3));
                    
                    // Als we deze IO-map nog niet hebben verwerkt
                    if (!processedIOFolders.contains(ioNumber) && ioContents.containsKey(ioNumber)) {
                        // Maak het gecombineerde XML bestand
                        byte[] combinedXml = IOProcessor.createCombinedXML(ioContents.get(ioNumber));
                        
                        // Schrijf het nieuwe bestand
                        String newFileName = "IO-" + ioNumber + ".xml";
                        ZipEntry newEntry = new ZipEntry(newFileName);
                        targetZip.putNextEntry(newEntry);
                        targetZip.write(combinedXml);
                        targetZip.closeEntry();
                        
                        logMessage("IO map verwerkt: " + ioNumber);
                        reportContent.append("IO map verwerkt: IO-").append(ioNumber).append("\n");
                        
                        processedIOFolders.add(ioNumber);
                        ioFoldersProcessed++;
                    }
                    
                    // Sla het originele bestand over
                    processedFiles++;
                    continue;
                }
                // Als het bestand in de OW-bestanden map staat, verplaats het naar de root
                else if (entryName.startsWith("OW-bestanden/")) {
                    // Haal alleen de bestandsnaam op (laatste deel van het pad)
                    newEntryName = new File(entryName).getName();
                    owFilesCount++;
                    logMessage("OW-bestand verplaatst naar root: " + newEntryName);
                    reportContent.append("Verplaatst: ").append(entryName).append(" -> ").append(newEntryName).append("\n");
                }
                
                if (shouldProcess) {
                    // Lees en schrijf het bestand
                    byte[] content = sourceZip.getInputStream(entry).readAllBytes();
                    ZipEntry newEntry = new ZipEntry(newEntryName);
                    targetZip.putNextEntry(newEntry);
                    targetZip.write(content);
                    targetZip.closeEntry();
                }
                
                processedFiles++;
                updateProgress(processedFiles, totalFiles);
            }

            // Schrijf samenvatting naar rapport
            reportContent.append("\nSamenvatting transformaties:\n");
            if (owFilesCount > 0) {
                reportContent.append("Aantal verplaatste OW-bestanden: ").append(owFilesCount).append("\n");
            }
            if (gmlFilesCount > 0) {
                reportContent.append("Aantal verplaatste GML-bestanden: ").append(gmlFilesCount).append("\n");
            }
            if (ioFoldersProcessed > 0) {
                reportContent.append("Aantal verwerkte IO-mappen: ").append(ioFoldersProcessed).append("\n");
            }
            reportContent.append("\n");
            
            // Schrijf het rapport
            reportWriter.write(reportContent.toString());
            
            statusLabel.setText("Transformatie voltooid!");
            logMessage("Transformatie succesvol afgerond. Output bestand: " + targetPath);
            logMessage("Rapport gegenereerd: " + reportPath);
            
        } catch (Exception e) {
            logError("Fout bij verwerken van bestanden: " + e.getMessage());
            throw new IOException("Fout bij transformatie", e);
        } finally {
            progressBar.setVisible(false);
        }
    }

    private void updateProgress(int processed, int total) {
        double progress = (double) processed / total;
        progressBar.setProgress(progress);
    }

    private File showFileChooser(String title) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("ZIP bestanden", "*.zip")
        );
        return fileChooser.showOpenDialog(new Stage());
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Fout");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void logMessage(String message) {
        logArea.appendText(message + "\n");
        logArea.setScrollTop(Double.MAX_VALUE);
    }

    private void logError(String message) {
        logArea.appendText("ERROR: " + message + "\n");
        logArea.setScrollTop(Double.MAX_VALUE);
        statusLabel.setText("Fout opgetreden!");
        statusLabel.setTextFill(javafx.scene.paint.Color.RED);
    }

    @FXML
    private void handleAnalyze() {
        String sourcePath = sourceZipField.getText();
        
        if (sourcePath.isEmpty()) {
            showError("Selecteer eerst een bronbestand.");
            return;
        }

        try {
            File sourceFile = new File(sourcePath);
            if (!sourceFile.exists()) {
                showError("Bronbestand bestaat niet: " + sourcePath);
                return;
            }

            Map<String, String> metadata = analyzeZipFile(sourcePath);
            displayMetadata(metadata);
            
        } catch (Exception e) {
            logError("Fout tijdens analyse: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Map<String, String> analyzeZipFile(String sourcePath) throws IOException {
        Map<String, String> metadata = new HashMap<>();
        
        try (ZipFile sourceZip = new ZipFile(sourcePath)) {
            List<ZipEntry> entries = sourceZip.stream().collect(Collectors.toList());
            
            // Zoek Metadata.xml en Identificatie.xml
            for (ZipEntry entry : entries) {
                String entryName = entry.getName();
                
                if (entryName.equals("Regeling/Metadata.xml")) {
                    logMessage("Analyseren van metadata: " + entryName);
                    byte[] content = sourceZip.getInputStream(entry).readAllBytes();
                    Map<String, String> metadataValues = MetadataProcessor.processMetadata(content);
                    metadata.putAll(metadataValues);
                }
                else if (entryName.equals("Regeling/Identificatie.xml")) {
                    logMessage("Analyseren van identificatie: " + entryName);
                    byte[] content = sourceZip.getInputStream(entry).readAllBytes();
                    Map<String, String> identificatieValues = MetadataProcessor.processIdentificatie(content);
                    metadata.putAll(identificatieValues);
                }
            }

            // Analyseer GML bestanden
            MetadataProcessor.GMLInfo gmlInfo = MetadataProcessor.analyzeGMLFiles(sourceZip);
            metadata.put("gml-count", String.valueOf(gmlInfo.count));
            metadata.put("gml-total-size", formatFileSize(gmlInfo.totalSize));
            metadata.put("gml-files", String.join("\n", gmlInfo.fileNames));
        }
        
        return metadata;
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private void displayMetadata(Map<String, String> metadata) {
        StringBuilder display = new StringBuilder();
        display.append("Metadata informatie:\n");
        display.append("-------------------\n\n");
        
        // Bevoegd gezag informatie
        if (metadata.containsKey("bevoegdgezag-code")) {
            display.append("Bevoegd gezag code: ").append(metadata.get("bevoegdgezag-code")).append("\n");
            display.append("Type: ").append(metadata.get("bevoegdgezag-type")).append("\n\n");
        }

        // Identificatie informatie
        if (metadata.containsKey("FRBRWork")) {
            display.append("Identificatie:\n");
            display.append("FRBRWork: ").append(metadata.get("FRBRWork")).append("\n");
        }
        if (metadata.containsKey("FRBRExpression")) {
            display.append("FRBRExpression: ").append(metadata.get("FRBRExpression")).append("\n\n");
        }

        // Citeertitel
        if (metadata.containsKey("citeerTitel")) {
            display.append("Citeertitel: ").append(metadata.get("citeerTitel")).append("\n\n");
        }

        // GML informatie
        if (metadata.containsKey("gml-count")) {
            display.append("GML Bestanden:\n");
            display.append("Aantal GML bestanden: ").append(metadata.get("gml-count")).append("\n");
            display.append("Totale grootte: ").append(metadata.get("gml-total-size")).append("\n");
            if (metadata.containsKey("gml-files") && !metadata.get("gml-files").isEmpty()) {
                display.append("\nGML bestandslijst:\n");
                display.append(metadata.get("gml-files")).append("\n");
            }
            display.append("\n");
        }

        metadataArea.setText(display.toString());
    }
} 