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
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
        
        // Houd bij welke bestanden we al hebben toegevoegd
        Set<String> addedFiles = new HashSet<>();
        
        try (ZipFile sourceZip = new ZipFile(sourcePath);
             FileOutputStream fos = new FileOutputStream(targetPath);
             ZipOutputStream targetZip = new ZipOutputStream(fos);
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

            // Haal eerst de analyse data op
            BesluitProcessor.AnalyseData data = BesluitProcessor.analyseZip(sourceZip);

            // Genereer besluit.xml en opdracht.xml
            try {
                BesluitProcessor.BesluitResult result = BesluitProcessor.createBesluitXml(sourceZip);
                
                // Voeg besluit.xml toe
                ZipEntry besluitEntry = new ZipEntry("besluit.xml");
                targetZip.putNextEntry(besluitEntry);
                targetZip.write(result.besluitXml);
                targetZip.closeEntry();
                addedFiles.add("besluit.xml");
                logMessage("besluit.xml gegenereerd");
                
                // Voeg opdracht.xml toe
                ZipEntry opdrachtEntry = new ZipEntry("opdracht.xml");
                targetZip.putNextEntry(opdrachtEntry);
                targetZip.write(result.opdrachtXml);
                targetZip.closeEntry();
                addedFiles.add("opdracht.xml");
                logMessage("opdracht.xml gegenereerd");
                
                reportContent.append("besluit.xml en opdracht.xml gegenereerd\n\n");
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
                
                // Sla pakbon.xml en bestanden die we al hebben toegevoegd over
                if (entryName.equals("pakbon.xml") || 
                    (!entryName.equals("opdracht.xml") && addedFiles.contains(new File(entryName).getName()))) {
                    logMessage(entryName + " overgeslagen");
                    processedFiles++;
                    continue;
                }

                // Bepaal de nieuwe entry naam en of we het bestand moeten verwerken
                String newEntryName = entryName;
                boolean shouldProcess = true;

                // Sla alle bestanden in de Regeling map over
                if (entryName.startsWith("Regeling/")) {
                    logMessage("Regeling bestand overgeslagen: " + entryName);
                    processedFiles++;
                    continue;
                }

                // Check eerst op GML bestanden, ongeacht in welke map ze zitten
                if (entryName.toLowerCase().endsWith(".gml")) {
                    // Haal alleen de bestandsnaam op (laatste deel van het pad)
                    newEntryName = new File(entryName).getName();
                    if (!addedFiles.contains(newEntryName)) {
                        gmlFilesCount++;
                        logMessage("GML-bestand verplaatst naar root: " + newEntryName);
                        reportContent.append("Verplaatst: ").append(entryName).append(" -> ").append(newEntryName).append("\n");
                    } else {
                        shouldProcess = false;
                    }
                }
                // Controleer of dit een bestand in een IO-map is
                else if (entryName.matches("IO-\\d+/.*")) {
                    String ioNumber = entryName.substring(3, entryName.indexOf('/', 3));
                    
                    // Als we deze IO-map nog niet hebben verwerkt
                    if (!processedIOFolders.contains(ioNumber)) {
                        String newFileName = "IO-" + ioNumber + ".xml";
                        if (!addedFiles.contains(newFileName)) {
                            // Haal de IO data op
                            BesluitProcessor.AnalyseData.InformatieObjectData ioData = null;
                            for (BesluitProcessor.AnalyseData.InformatieObjectData io : data.informatieObjecten) {
                                if (io.folder.equals("IO-" + ioNumber)) {
                                    ioData = io;
                                    break;
                                }
                            }
                            
                            if (ioData != null) {
                                // Maak het IO XML bestand
                                byte[] ioXml = IOProcessor.createIOXml(ioData, sourceZip);
                                
                                // Schrijf het nieuwe bestand
                                ZipEntry newEntry = new ZipEntry(newFileName);
                                targetZip.putNextEntry(newEntry);
                                targetZip.write(ioXml);
                                targetZip.closeEntry();
                                addedFiles.add(newFileName);
                                
                                logMessage("IO map verwerkt: " + ioNumber);
                                reportContent.append("IO map verwerkt: IO-").append(ioNumber).append("\n");
                                
                                processedIOFolders.add(ioNumber);
                                ioFoldersProcessed++;
                            }
                        }
                    }
                    
                    // Sla het originele bestand over
                    processedFiles++;
                    continue;
                }
                // Als het bestand in de OW-bestanden map staat, verplaats het naar de root
                else if (entryName.startsWith("OW-bestanden/")) {
                    // Haal alleen de bestandsnaam op (laatste deel van het pad)
                    newEntryName = new File(entryName).getName();
                    if (!addedFiles.contains(newEntryName)) {
                        owFilesCount++;
                        logMessage("OW-bestand verplaatst naar root: " + newEntryName);
                        reportContent.append("Verplaatst: ").append(entryName).append(" -> ").append(newEntryName).append("\n");
                    } else {
                        shouldProcess = false;
                    }
                }
                
                if (shouldProcess && !addedFiles.contains(newEntryName)) {
                    // Lees en schrijf het bestand
                    byte[] content = sourceZip.getInputStream(entry).readAllBytes();
                    ZipEntry newEntry = new ZipEntry(newEntryName);
                    targetZip.putNextEntry(newEntry);
                    targetZip.write(content);
                    targetZip.closeEntry();
                    addedFiles.add(newEntryName);
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
            
            // Genereer en voeg manifest.xml toe
            try {
                byte[] manifestXml = ManifestProcessor.generateManifest(sourceZip);
                ZipEntry manifestEntry = new ZipEntry("manifest.xml");
                targetZip.putNextEntry(manifestEntry);
                targetZip.write(manifestXml);
                targetZip.closeEntry();
                logMessage("manifest.xml gegenereerd");
            } catch (Exception e) {
                logError("Fout bij genereren manifest.xml: " + e.getMessage());
                e.printStackTrace();  // Voor debugging
            }
            
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
        try {
            String sourcePath = sourceZipField.getText();
            if (sourcePath.isEmpty()) {
                showError("Selecteer eerst een bronbestand");
                return;
            }

            File sourceFile = new File(sourcePath);
            if (!sourceFile.exists()) {
                showError("Bronbestand bestaat niet: " + sourcePath);
                return;
            }

            try (ZipFile zipFile = new ZipFile(sourceFile)) {
                // Voer de analyse uit
                BesluitProcessor.AnalyseData data = BesluitProcessor.analyseZip(zipFile);
                
                // Bouw de output tekst
                StringBuilder output = new StringBuilder();
                output.append("Analyse van ZIP bestand:\n\n");
                
                // Algemene informatie
                output.append("Algemene informatie:\n");
                output.append("-------------------\n");
                output.append("FRBRWork: ").append(data.frbrWork).append("\n");
                output.append("FRBRExpression: ").append(data.frbrExpression).append("\n");
                output.append("Doel: ").append(data.doel).append("\n");
                output.append("Bevoegd gezag: ").append(data.bevoegdGezag).append("\n");
                output.append("Aantal informatieobjecten: ").append(data.aantalInformatieObjecten).append("\n");
                output.append("Totale GML bestandsgrootte: ").append(formatFileSize(data.totaleGmlBestandsgrootte)).append("\n\n");
                
                // Informatie per informatieobject
                output.append("Informatieobjecten:\n");
                output.append("------------------\n");
                for (BesluitProcessor.AnalyseData.InformatieObjectData io : data.informatieObjecten) {
                    output.append("\nInformatieobject: ").append(io.folder).append("\n");
                    output.append("  FRBRWork: ").append(io.frbrWork).append("\n");
                    output.append("  FRBRExpression: ").append(io.frbrExpression).append("\n");
                    output.append("  ExtIoRef-eId: ").append(io.extIoRefEId).append("\n");
                    if (io.officieleTitel != null) {
                        output.append("  Officiële titel: ").append(io.officieleTitel).append("\n");
                    }
                    if (io.bestandsnaam != null) {
                        output.append("  Bestandsnaam: ").append(io.bestandsnaam).append("\n");
                    }
                }
                
                // Toon de output
                metadataArea.setText(output.toString());
            }
        } catch (Exception e) {
            showError("Fout bij analyseren van ZIP bestand: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
} 