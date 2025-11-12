package nl.omgevingswet;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.DirectoryChooser;
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
import java.util.ArrayList;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerConfigurationException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;

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
    @FXML private Button transformButton;
    @FXML private Button validateButton;
    @FXML private Button intrekkingPubButton;
    @FXML private Button intrekkingValButton;
    @FXML private Button doorleverenRegelingVersieButton;
    @FXML private Button validatieDoorLeverenRegelingversieButton;

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
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Selecteer uitvoermap");
        File selectedDirectory = directoryChooser.showDialog(new Stage());
        if (selectedDirectory != null) {
            // Gebruik de geselecteerde map en voeg de bestandsnaam toe
            String targetPath = selectedDirectory.getAbsolutePath() + File.separator + "publicatieOpdracht_initieel.zip";
            targetZipField.setText(targetPath);
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
        } else {
            // Als aangepaste uitvoerlocatie wordt aangevinkt, maak het doelpad leeg
            targetZipField.setText("");
        }
    }

    private void updateDefaultTargetPath(File sourceFile) {
        String sourcePath = sourceFile.getAbsolutePath();
        String parentPath = sourceFile.getParent();
        String targetPath = parentPath + File.separator + "publicatieOpdracht_initieel.zip";
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

        // Vervang alle mogelijke varianten met "publicatieOpdracht" in de bestandsnaam
        targetPath = targetPath.replace("validatieOpdracht", "publicatieOpdracht")
                             .replace("intrekkingOpdracht", "publicatieOpdracht")
                             .replace("intrekkingValidatieOpdracht", "publicatieOpdracht")
                             .replace("doorleverenRegelingVersie", "publicatieOpdracht")
                             .replace("validatieDoorLeverenRegelingversie", "publicatieOpdracht");
        targetZipField.setText(targetPath);

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

    @FXML
    private void handleValidate() {
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

        // Vervang alle mogelijke varianten met "validatieOpdracht" in de bestandsnaam
        targetPath = targetPath.replace("publicatieOpdracht", "validatieOpdracht")
                             .replace("intrekkingOpdracht", "validatieOpdracht")
                             .replace("intrekkingValidatieOpdracht", "validatieOpdracht")
                             .replace("doorleverenRegelingVersie", "validatieOpdracht")
                             .replace("validatieDoorLeverenRegelingversie", "validatieOpdracht");
        targetZipField.setText(targetPath);

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

            transformZipFile(sourcePath, targetPath, true);
        } catch (Exception e) {
            logError("Fout tijdens transformatie: " + e.getMessage());
            e.printStackTrace(); // Voor debugging
        }
    }

    @FXML
    private void handleIntrekkingPub() {
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

        // Vervang alle mogelijke varianten met "intrekkingOpdracht" in de bestandsnaam
        targetPath = targetPath.replace("publicatieOpdracht", "intrekkingOpdracht")
                             .replace("validatieOpdracht", "intrekkingOpdracht")
                             .replace("intrekkingValidatieOpdracht", "intrekkingOpdracht")
                             .replace("doorleverenRegelingVersie", "intrekkingOpdracht")
                             .replace("validatieDoorLeverenRegelingversie", "intrekkingOpdracht");
        targetZipField.setText(targetPath);

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

            transformZipFile(sourcePath, targetPath, false, true);
        } catch (Exception e) {
            logError("Fout tijdens transformatie: " + e.getMessage());
            e.printStackTrace(); // Voor debugging
        }
    }

    @FXML
    private void handleIntrekkingVal() {
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

        // Vervang alle mogelijke varianten met "intrekkingValidatieOpdracht" in de bestandsnaam
        targetPath = targetPath.replace("publicatieOpdracht", "intrekkingValidatieOpdracht")
                             .replace("validatieOpdracht", "intrekkingValidatieOpdracht")
                             .replace("intrekkingOpdracht", "intrekkingValidatieOpdracht")
                             .replace("doorleverenRegelingVersie", "intrekkingValidatieOpdracht")
                             .replace("validatieDoorLeverenRegelingversie", "intrekkingValidatieOpdracht");
        targetZipField.setText(targetPath);

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

            transformZipFile(sourcePath, targetPath, true, true);
        } catch (Exception e) {
            logError("Fout tijdens transformatie: " + e.getMessage());
            e.printStackTrace(); // Voor debugging
        }
    }

    @FXML
    private void handleDoorleverenRegelingVersie() {
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

        // Vervang alle mogelijke varianten met "doorleverenRegelingVersie" in de bestandsnaam
        targetPath = targetPath.replace("publicatieOpdracht", "doorleverenRegelingVersie")
                             .replace("validatieOpdracht", "doorleverenRegelingVersie")
                             .replace("intrekkingOpdracht", "doorleverenRegelingVersie")
                             .replace("intrekkingValidatieOpdracht", "doorleverenRegelingVersie")
                             .replace("validatieDoorLeverenRegelingversie", "doorleverenRegelingVersie");
        targetZipField.setText(targetPath);

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

            transformZipFile(sourcePath, targetPath, false, false, true);
        } catch (Exception e) {
            logError("Fout tijdens transformatie: " + e.getMessage());
            e.printStackTrace(); // Voor debugging
        }
    }

    @FXML
    private void handleValidatieDoorLeverenRegelingversie() {
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

        // Vervang alle mogelijke varianten met "validatieDoorLeverenRegelingversie" in de bestandsnaam
        targetPath = targetPath.replace("publicatieOpdracht", "validatieDoorLeverenRegelingversie")
                             .replace("validatieOpdracht", "validatieDoorLeverenRegelingversie")
                             .replace("intrekkingOpdracht", "validatieDoorLeverenRegelingversie")
                             .replace("intrekkingValidatieOpdracht", "validatieDoorLeverenRegelingversie")
                             .replace("doorleverenRegelingVersie", "validatieDoorLeverenRegelingversie");
        targetZipField.setText(targetPath);

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

            transformZipFile(sourcePath, targetPath, true, false, true);
        } catch (Exception e) {
            logError("Fout tijdens transformatie: " + e.getMessage());
            e.printStackTrace(); // Voor debugging
        }
    }

    private void transformZipFile(String sourcePath, String targetPath) throws IOException {
        transformZipFile(sourcePath, targetPath, false, false, false);
    }

    private void transformZipFile(String sourcePath, String targetPath, boolean isValidation) throws IOException {
        transformZipFile(sourcePath, targetPath, isValidation, false, false);
    }

    private void transformZipFile(String sourcePath, String targetPath, boolean isValidation, boolean isIntrekking) throws IOException {
        transformZipFile(sourcePath, targetPath, isValidation, isIntrekking, false);
    }

    private void transformZipFile(String sourcePath, String targetPath, boolean isValidation, boolean isIntrekking, boolean isDoorleveren) throws IOException {
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
            
            // Haal eerst de analyse data op
            BesluitProcessor.AnalyseData data = BesluitProcessor.analyseZip(sourceZip);
            
            // Log de analyse data
            logMessage("Analyse data opgehaald:");
            logMessage("FRBRWork: " + data.frbrWork);
            logMessage("FRBRExpression: " + data.frbrExpression);
            logMessage("Doel: " + data.doel);
            logMessage("Bevoegd gezag: " + data.bevoegdGezag);
            logMessage("Aantal informatieobjecten: " + data.aantalInformatieObjecten);
            logMessage("Totale GML bestandsgrootte: " + data.totaleGmlBestandsgrootte);
            
            // Log de informatieobjecten
            for (BesluitProcessor.AnalyseData.InformatieObjectData io : data.informatieObjecten) {
                logMessage("Informatieobject: " + io.folder);
                logMessage("  FRBRWork: " + io.frbrWork);
                logMessage("  FRBRExpression: " + io.frbrExpression);
                logMessage("  ExtIoRef-eId: " + io.extIoRefEId);
                if (io.officieleTitel != null) {
                    logMessage("  Officiële titel: " + io.officieleTitel);
                }
                if (io.bestandsnaam != null) {
                    logMessage("  Bestandsnaam: " + io.bestandsnaam);
                }
            }

            // Verwerk informatieobjecten voor initiële publicaties
            if (!isIntrekking) {
                // Verzamel IO nummers
                Set<String> ioNumbers = new HashSet<>();
                for (ZipEntry entry : sourceZip.stream().collect(Collectors.toList())) {
                    String entryName = entry.getName();
                    if (entryName.startsWith("IO-")) {
                        String ioNumber = entryName.substring(3, entryName.indexOf('/', 3));
                        ioNumbers.add(ioNumber);
                    }
                }

                // Log de gevonden IO nummers
                logMessage("Gevonden IO nummers: " + ioNumbers);
                reportContent.append("Gevonden IO nummers: ").append(ioNumbers).append("\n");

                // Verwerk IO bestanden en verplaats PDF/GML bestanden
                try {
                    processIOFiles(sourceZip, targetZip, addedFiles, data, ioNumbers, reportContent);
                } catch (Exception e) {
                    logError("Fout bij verwerken van IO bestanden: " + e.getMessage());
                    e.printStackTrace();  // Voor debugging
                    reportContent.append("Waarschuwing: Kon IO bestanden niet verwerken: " + e.getMessage() + "\n\n");
                }
            } else {
                // Verwerk IO intrekkingen
                try {
                    IOIntrekkingProcessor.processIOIntrekkingen(sourceZip, targetZip, data, addedFiles, reportContent);
                } catch (Exception e) {
                    logError("Fout bij verwerken van IO intrekkingen: " + e.getMessage());
                    e.printStackTrace();  // Voor debugging
                    reportContent.append("Waarschuwing: Kon IO intrekkingen niet verwerken: " + e.getMessage() + "\n\n");
                }
            }

            // Genereer besluit.xml en opdracht.xml
            byte[] besluitXml = null;
            byte[] opdrachtXml = null;
            String besluitFileName = isIntrekking ? "intrekkingsbesluit.xml" : "besluit.xml";
            
            try {
                if (isIntrekking) {
                    IntrekkingProcessor.IntrekkingResult result = IntrekkingProcessor.createIntrekkingXml(sourceZip, isValidation);
                    besluitXml = result.besluitXml;
                    opdrachtXml = result.opdrachtXml;
                    
                    // Voeg de gewijzigde OW-bestanden toe aan de lijst van bestanden die we al hebben toegevoegd
                    for (Map.Entry<String, byte[]> entry : result.modifiedFiles.entrySet()) {
                        String fileName = entry.getKey();
                        byte[] content = entry.getValue();
                        
                        // Voeg het gewijzigde bestand toe aan de ZIP
                        ZipEntry newEntry = new ZipEntry(fileName);
                        targetZip.putNextEntry(newEntry);
                        targetZip.write(content);
                        targetZip.closeEntry();
                        addedFiles.add(fileName);
                        logMessage("Gewijzigd OW-bestand toegevoegd: " + fileName);
                    }
                } else {
                    BesluitProcessor.BesluitResult result = BesluitProcessor.createBesluitXml(sourceZip, isValidation);
                    besluitXml = result.besluitXml;
                    opdrachtXml = result.opdrachtXml;
                }
                
                // Voeg besluit.xml toe
                if (besluitXml != null) {
                    ZipEntry besluitEntry = new ZipEntry(besluitFileName);
                    targetZip.putNextEntry(besluitEntry);
                    targetZip.write(besluitXml);
                    targetZip.closeEntry();
                    addedFiles.add(besluitFileName);
                    logMessage(besluitFileName + " gegenereerd");
                }
                
                // Voeg opdracht.xml toe
                if (opdrachtXml != null) {
                    ZipEntry opdrachtEntry = new ZipEntry("opdracht.xml");
                    targetZip.putNextEntry(opdrachtEntry);
                    targetZip.write(opdrachtXml);
                    targetZip.closeEntry();
                    addedFiles.add("opdracht.xml");
                    logMessage("opdracht.xml gegenereerd");
                }
                
                reportContent.append(besluitFileName).append(" en opdracht.xml gegenereerd\n\n");
            } catch (Exception e) {
                logError("Fout bij genereren " + besluitFileName + ": " + e.getMessage());
                e.printStackTrace();  // Voor debugging
                reportContent.append("Waarschuwing: Kon " + besluitFileName + " niet genereren: " + e.getMessage() + "\n\n");
                throw e;  // Gooi de exceptie door om de verwerking te stoppen
            }

            // Verwerk alle andere bestanden
            List<ZipEntry> entries = sourceZip.stream().collect(Collectors.toList());
            int totalFiles = entries.size();
            int processedFiles = 0;

            for (ZipEntry entry : entries) {
                String entryName = entry.getName();
                boolean shouldProcess = true;

                // Skip als het een directory is of als het bestand al is toegevoegd
                if (entryName.endsWith("/") || addedFiles.contains(new File(entryName).getName())) {
                    processedFiles++;
                    continue;
                }

                // Skip IO-mappen en hun bestanden
                if (entryName.startsWith("IO-")) {
                    processedFiles++;
                    continue;
                }

                // Sla pakbon.xml over
                if (entryName.equals("pakbon.xml")) {
                    logMessage(entryName + " overgeslagen");
                    processedFiles++;
                    continue;
                }

                // Bepaal de nieuwe entry naam en of we het bestand moeten verwerken
                String newEntryName = entryName;
                shouldProcess = true;

                // Sla alle bestanden in de Regeling map over
                if (entryName.startsWith("Regeling/")) {
                    // Check of het een afbeelding is
                    String fileName = new File(entryName).getName();
                    String fileNameLower = fileName.toLowerCase();
                    if (!isIntrekking && (fileNameLower.endsWith(".jpg") || fileNameLower.endsWith(".jpeg") || fileNameLower.endsWith(".png"))) {
                        // Verplaats de afbeelding naar de root
                        if (!addedFiles.contains(fileName)) {
                            logMessage("Afbeelding verplaatst naar root: " + fileName);
                            reportContent.append("Verplaatst: ").append(entryName).append(" -> ").append(fileName).append("\n");
                            
                            // Lees en schrijf het bestand
                            byte[] content = sourceZip.getInputStream(entry).readAllBytes();
                            ZipEntry newEntry = new ZipEntry(fileName);
                            targetZip.putNextEntry(newEntry);
                            targetZip.write(content);
                            targetZip.closeEntry();
                            addedFiles.add(fileName);
                        }
                    } else {
                        logMessage("Regeling bestand overgeslagen: " + entryName);
                    }
                    processedFiles++;
                    continue;
                }
                
                // Als het bestand in de OW-bestanden map staat, verplaats het naar de root
                else if (entryName.startsWith("OW-bestanden/")) {
                    // Haal alleen de bestandsnaam op (laatste deel van het pad)
                    newEntryName = new File(entryName).getName();
                    if (!addedFiles.contains(newEntryName)) {
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
            reportContent.append("----------------------\n");

            // Schrijf het rapport
            reportWriter.write(reportContent.toString());
            
            // Genereer en voeg manifest.xml toe als laatste stap
            try {
                // Voeg manifest.xml toe aan de lijst van bestanden
                addedFiles.add("manifest.xml");
                
                // Genereer manifest.xml met alleen de daadwerkelijke bestanden
                byte[] manifestXml = ManifestProcessor.generateManifest(sourceZip, addedFiles, isIntrekking);
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

    private void processIOFiles(ZipFile sourceZip, ZipOutputStream targetZip, Set<String> addedFiles, 
                               BesluitProcessor.AnalyseData data, Set<String> ioNumbers, 
                               StringBuilder reportContent) throws IOException, Exception {
        int gmlFilesCount = 0;
        int ioFoldersProcessed = 0;

        // Verwerk elke IO-map
        for (String ioNumber : ioNumbers) {
            // Zoek de IO data
            BesluitProcessor.AnalyseData.InformatieObjectData ioData = null;
            for (BesluitProcessor.AnalyseData.InformatieObjectData io : data.informatieObjecten) {
                if (io.folder.equals("IO-" + ioNumber)) {
                    ioData = io;
                    break;
                }
            }

            if (ioData != null) {
                // Genereer IO XML bestand
                String xmlFileName = "IO-" + ioNumber + ".xml";
                if (!addedFiles.contains(xmlFileName)) {
                    byte[] ioXml = IOProcessor.createIOXml(ioData, sourceZip, data.frbrWork);
                    ZipEntry newEntry = new ZipEntry(xmlFileName);
                    targetZip.putNextEntry(newEntry);
                    targetZip.write(ioXml);
                    targetZip.closeEntry();
                    addedFiles.add(xmlFileName);
                    logMessage("IO XML gegenereerd: " + xmlFileName);
                    ioFoldersProcessed++;
                }

                // Verplaats PDF en GML bestanden naar de root
                for (ZipEntry entry : sourceZip.stream().collect(Collectors.toList())) {
                    String entryName = entry.getName();
                    if (entryName.startsWith("IO-" + ioNumber + "/")) {
                        String fileName = new File(entryName).getName();
                        String fileNameLower = fileName.toLowerCase();
                        if ((fileNameLower.endsWith(".pdf") || fileNameLower.endsWith(".gml")) && !addedFiles.contains(fileName)) {
                            byte[] content = sourceZip.getInputStream(entry).readAllBytes();
                            
                            if (fileNameLower.endsWith(".gml")) {
                                content = IOProcessor.wrapGmlContent(content);
                                gmlFilesCount++;
                            }
                            
                            ZipEntry newEntry = new ZipEntry(fileName);
                            targetZip.putNextEntry(newEntry);
                            targetZip.write(content);
                            targetZip.closeEntry();
                            addedFiles.add(fileName);
                            logMessage("Bestand verplaatst naar root: " + fileName);
                        }
                    }
                }
            } else {
                // Als we geen IO data hebben, probeer dan de bestanden direct te verwerken
                logMessage("Geen IO data gevonden voor IO-" + ioNumber + ", probeer direct te verwerken");
                
                // Verplaats PDF en GML bestanden naar de root
                for (ZipEntry entry : sourceZip.stream().collect(Collectors.toList())) {
                    String entryName = entry.getName();
                    if (entryName.startsWith("IO-" + ioNumber + "/")) {
                        String fileName = new File(entryName).getName();
                        String fileNameLower = fileName.toLowerCase();
                        if ((fileNameLower.endsWith(".pdf") || fileNameLower.endsWith(".gml")) && !addedFiles.contains(fileName)) {
                            byte[] content = sourceZip.getInputStream(entry).readAllBytes();
                            
                            if (fileNameLower.endsWith(".gml")) {
                                content = IOProcessor.wrapGmlContent(content);
                                gmlFilesCount++;
                            }
                            
                            ZipEntry newEntry = new ZipEntry(fileName);
                            targetZip.putNextEntry(newEntry);
                            targetZip.write(content);
                            targetZip.closeEntry();
                            addedFiles.add(fileName);
                            logMessage("Bestand verplaatst naar root: " + fileName);
                        }
                    }
                }
                
                // Genereer een eenvoudig IO XML bestand
                String xmlFileName = "IO-" + ioNumber + ".xml";
                if (!addedFiles.contains(xmlFileName)) {
                    // Maak een eenvoudig IO XML bestand
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    Document doc = builder.newDocument();
                    
                    Element root = doc.createElement("informatieobject");
                    root.setAttribute("xmlns", "http://www.omgevingswet.nl/ow/1.0");
                    root.setAttribute("xmlns:xlink", "http://www.w3.org/1999/xlink");
                    doc.appendChild(root);
                    
                    Element metadata = doc.createElement("metadata");
                    root.appendChild(metadata);
                    
                    Element identificatie = doc.createElement("identificatie");
                    metadata.appendChild(identificatie);
                    
                    Element frbrWork = doc.createElement("frbrWork");
                    frbrWork.setTextContent(data.frbrWork);
                    identificatie.appendChild(frbrWork);
                    
                    Element eId = doc.createElement("eId");
                    eId.setTextContent("IO-" + ioNumber);
                    identificatie.appendChild(eId);
                    
                    // Converteer het document naar een byte array
                    TransformerFactory transformerFactory = TransformerFactory.newInstance();
                    Transformer transformer = transformerFactory.newTransformer();
                    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
                    
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    transformer.transform(new DOMSource(doc), new StreamResult(outputStream));
                    
                    byte[] ioXml = outputStream.toByteArray();
                    
                    ZipEntry newEntry = new ZipEntry(xmlFileName);
                    targetZip.putNextEntry(newEntry);
                    targetZip.write(ioXml);
                    targetZip.closeEntry();
                    addedFiles.add(xmlFileName);
                    logMessage("IO XML gegenereerd: " + xmlFileName);
                    ioFoldersProcessed++;
                }
            }
        }

        // Update rapport
        if (gmlFilesCount > 0) {
            reportContent.append("Aantal verplaatste GML-bestanden: ").append(gmlFilesCount).append("\n");
        }
        if (ioFoldersProcessed > 0) {
            reportContent.append("Aantal verwerkte IO-mappen: ").append(ioFoldersProcessed).append("\n");
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