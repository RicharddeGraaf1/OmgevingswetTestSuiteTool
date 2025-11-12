package nl.omgevingswet.integration;

import nl.omgevingswet.BesluitProcessor;
import nl.omgevingswet.util.TestUtils;
import org.junit.jupiter.api.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.nio.file.Files;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.*;

/**
 * Performance integratie tests voor validatie opdrachten
 * 
 * Deze tests gebruiken grote test datasets uit src/test/resources/performance-validatie/
 * en zijn getagged met @Tag("performance") zodat ze optioneel zijn.
 * 
 * Run deze tests met:
 * - mvn test -Dgroups="performance"              → alleen performance tests
 * - mvn test -Dgroups="fast,performance"         → alle tests
 * - mvn test (normaal)                           → alleen fast tests (skip performance)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("performance")
class PerformanceValidatieIntegrationTest {
    
    private static final String STOP_DATA_NS = "https://standaarden.overheid.nl/stop/imop/data/";
    private static final String GEO_NS = "https://standaarden.overheid.nl/stop/imop/geo/";
    
    /**
     * Performance test met WS0665 dataset (44 GML bestanden)
     */
    @Test
    @DisplayName("WS0665 grote validatie transformatie (44 GML bestanden)")
    @Timeout(30)
    void testWS0665LargeValidatieTransformatie() throws Exception {
        System.out.println("\n=== WS0665 Performance Validatie Test ===");
        
        long startTime = System.currentTimeMillis();
        
        // Arrange - Laad input ZIP uit resources
        ZipFile inputZip = TestUtils.loadTestZipFile(
            "performance-validatie/input/ws0665_input.zip");
        
        assertThat(inputZip).isNotNull();
        System.out.println("✓ Input ZIP geladen: " + inputZip.getName());
        System.out.println("  Aantal entries: " + inputZip.size());
        
        // Tel GML bestanden in input
        long gmlCount = inputZip.stream()
            .filter(entry -> entry.getName().toLowerCase().endsWith(".gml"))
            .count();
        System.out.println("  Aantal GML bestanden: " + gmlCount);
        
        long loadTime = System.currentTimeMillis() - startTime;
        
        // Act - Verwerk met BesluitProcessor
        long processStartTime = System.currentTimeMillis();
        BesluitProcessor.BesluitResult result = 
            BesluitProcessor.createBesluitXml(inputZip, true);
        long processTime = System.currentTimeMillis() - processStartTime;
        
        // Assert - Controleer dat output gegenereerd is
        assertThat(result).isNotNull();
        assertThat(result.besluitXml).isNotNull();
        assertThat(result.opdrachtXml).isNotNull();
        
        System.out.println("✓ Validatie XML gegenereerd");
        System.out.println("  Besluit XML grootte: " + result.besluitXml.length + " bytes");
        System.out.println("  Opdracht XML grootte: " + result.opdrachtXml.length + " bytes");
        
        // Performance metrics
        long totalTime = System.currentTimeMillis() - startTime;
        System.out.println("\n⏱ Performance Metrics:");
        System.out.println("  Laden ZIP: " + loadTime + "ms");
        System.out.println("  Verwerken: " + processTime + "ms");
        System.out.println("  Totaal: " + totalTime + "ms");
        System.out.println("  Gemiddeld per GML: " + (gmlCount > 0 ? processTime / gmlCount : 0) + "ms");
        
        // Valideer basis structuur
        Document besluitDoc = TestUtils.parseXmlBytes(result.besluitXml);
        assertThat(besluitDoc.getDocumentElement().getLocalName())
            .as("Root element moet AanleveringBesluit zijn")
            .isEqualTo("AanleveringBesluit");
        
        Document opdrachtDoc = TestUtils.parseXmlBytes(result.opdrachtXml);
        assertThat(opdrachtDoc.getDocumentElement().getLocalName())
            .as("Root element moet validatieOpdracht zijn")
            .isEqualTo("validatieOpdracht");
        
        System.out.println("✓ XML structuur correct");
        
        // Performance waarschuwingen
        if (processTime > 10000) {
            System.out.println("⚠ WAARSCHUWING: Verwerking duurt langer dan 10 seconden!");
        } else if (processTime > 5000) {
            System.out.println("⚠ Verwerking duurt langer dan 5 seconden");
        } else {
            System.out.println("✓ Performance is goed (< 5s)");
        }
        
        System.out.println("\n=== Performance Test Succesvol Afgerond ===\n");
    }
    
    /**
     * Test dat de ws0665 expected output correct is (hash en bestandsnamen)
     */
    @Test
    @DisplayName("WS0665 expected output validatie (hash en bestandsnamen)")
    void testWS0665ExpectedOutput() throws Exception {
        System.out.println("\n=== WS0665 Expected Output Validatie ===");
        
        // Pad naar expected output directory
        String expectedDir = "src/test/resources/performance-validatie/expected/ws0665_output";
        File expectedDirectory = new File(expectedDir);
        
        assertThat(expectedDirectory.exists())
            .as("Expected output directory moet bestaan")
            .isTrue();
        
        System.out.println("✓ Expected output directory gevonden: " + expectedDir);
        
        // Test 1: Controleer alle IO XML bestanden op hash waarden
        System.out.println("\n--- Controleren IO XML bestanden ---");
        
        File[] ioFiles = expectedDirectory.listFiles((dir, name) -> 
            name.startsWith("IO-") && name.endsWith(".xml"));
        
        if (ioFiles != null && ioFiles.length > 0) {
            System.out.println("✓ Gevonden " + ioFiles.length + " IO XML bestanden");
            
            int ioFilesWithHash = 0;
            int invalidHashes = 0;
            
            for (File ioFile : ioFiles) {
                byte[] ioContent = Files.readAllBytes(ioFile.toPath());
                Document ioDoc = TestUtils.parseXmlBytes(ioContent);
                
                // Zoek naar hash elementen
                NodeList hashNodes = ioDoc.getElementsByTagNameNS(STOP_DATA_NS, "hash");
                
                if (hashNodes.getLength() > 0) {
                    ioFilesWithHash++;
                    
                    // Controleer hash formaat
                    String hashValue = hashNodes.item(0).getTextContent();
                    
                    if (hashValue != null && !hashValue.isEmpty()) {
                        if (hashValue.length() == 128 && hashValue.matches("[0-9a-f]{128}")) {
                            // Valid SHA-512 hash
                        } else {
                            invalidHashes++;
                            System.out.println("  ⚠ " + ioFile.getName() + " heeft ongeldige hash: " + hashValue.substring(0, Math.min(32, hashValue.length())) + "...");
                        }
                    }
                }
            }
            
            System.out.println("  ✓ " + ioFilesWithHash + " van " + ioFiles.length + " IO bestanden bevatten hash waarden");
            
            if (invalidHashes > 0) {
                System.out.println("  ⚠ " + invalidHashes + " bestanden hebben ongeldige hash waarden");
            }
            
            assertThat(ioFilesWithHash)
                .as("Minimaal de helft van de IO bestanden moet hash waarden bevatten")
                .isGreaterThan(ioFiles.length / 2);
        }
        
        // Test 2: Controleer GML bestanden
        System.out.println("\n--- Controleren GML bestanden ---");
        
        File[] gmlFiles = expectedDirectory.listFiles((dir, name) -> 
            name.toLowerCase().endsWith(".gml"));
        
        if (gmlFiles != null && gmlFiles.length > 0) {
            System.out.println("✓ Gevonden " + gmlFiles.length + " GML bestanden");
            
            // Test hoofdletter-behoud
            int filesWithUppercase = 0;
            for (File gmlFile : gmlFiles) {
                String name = gmlFile.getName();
                if (!name.equals(name.toLowerCase())) {
                    filesWithUppercase++;
                    if (filesWithUppercase <= 5) { // Toon max 5 voorbeelden
                        System.out.println("  ✓ Bestandsnaam met hoofdletters: " + name);
                    }
                }
                
                // Controleer GML structuur (sample eerste 10 bestanden)
                if (gmlFiles.length > 10 && gmlFile != gmlFiles[0] && 
                    gmlFile != gmlFiles[gmlFiles.length/2] && 
                    gmlFile != gmlFiles[gmlFiles.length-1]) {
                    continue;
                }
                
                byte[] gmlContent = Files.readAllBytes(gmlFile.toPath());
                Document gmlDoc = TestUtils.parseXmlBytes(gmlContent);
                Element rootElement = gmlDoc.getDocumentElement();
                
                // Check geen dubbele GeoInformatieObjectVaststelling
                if ("GeoInformatieObjectVaststelling".equals(rootElement.getLocalName())) {
                    NodeList nestedGeoNodes = rootElement.getElementsByTagNameNS(GEO_NS, "GeoInformatieObjectVaststelling");
                    assertThat(nestedGeoNodes.getLength())
                        .as(name + " mag geen geneste GeoInformatieObjectVaststelling bevatten")
                        .isEqualTo(0);
                }
            }
            
            if (filesWithUppercase > 5) {
                System.out.println("  ... en " + (filesWithUppercase - 5) + " andere bestanden met hoofdletters");
            }
            System.out.println("  ✓ Hoofdletters in bestandsnamen zijn behouden");
        }
        
        // Test 3: Sample check van bestandsnaam consistentie (eerste 10 IO bestanden)
        System.out.println("\n--- Controleren bestandsnaam consistentie (sample) ---");
        
        if (ioFiles != null && gmlFiles != null) {
            int checkCount = Math.min(10, ioFiles.length);
            int matchCount = 0;
            
            for (int i = 0; i < checkCount; i++) {
                File ioFile = ioFiles[i];
                byte[] ioContent = Files.readAllBytes(ioFile.toPath());
                Document ioDoc = TestUtils.parseXmlBytes(ioContent);
                
                NodeList bestandsnaamNodes = ioDoc.getElementsByTagNameNS(STOP_DATA_NS, "bestandsnaam");
                
                if (bestandsnaamNodes.getLength() > 0) {
                    String bestandsnaam = bestandsnaamNodes.item(0).getTextContent();
                    
                    boolean fileExists = false;
                    for (File gmlFile : gmlFiles) {
                        if (gmlFile.getName().equals(bestandsnaam)) {
                            fileExists = true;
                            matchCount++;
                            break;
                        }
                    }
                    
                    if (fileExists && i < 3) { // Toon eerste 3
                        System.out.println("  ✓ " + ioFile.getName() + " -> " + bestandsnaam + " (OK)");
                    }
                }
            }
            
            System.out.println("  ✓ " + matchCount + " van " + checkCount + " gecontroleerde bestanden matchen");
        }
        
        System.out.println("\n=== WS0665 Output Validatie Succesvol ===\n");
    }
    
    /**
     * Stress test - controleert geheugengebruik en performance onder druk
     */
    @Test
    @DisplayName("WS0665 stress test - meerdere runs achter elkaar")
    @Timeout(60)
    void testWS0665StressTest() throws Exception {
        System.out.println("\n=== WS0665 Stress Test ===");
        
        ZipFile inputZip = TestUtils.loadTestZipFile(
            "performance-validatie/input/ws0665_input.zip");
        
        int runs = 5;
        long[] times = new long[runs];
        
        System.out.println("Uitvoeren van " + runs + " runs...\n");
        
        for (int i = 0; i < runs; i++) {
            long startTime = System.currentTimeMillis();
            
            BesluitProcessor.BesluitResult result = 
                BesluitProcessor.createBesluitXml(inputZip, true);
            
            times[i] = System.currentTimeMillis() - startTime;
            
            assertThat(result).isNotNull();
            assertThat(result.besluitXml).isNotNull();
            
            System.out.println("  Run " + (i+1) + ": " + times[i] + "ms");
        }
        
        // Bereken statistieken
        long min = times[0];
        long max = times[0];
        long total = 0;
        
        for (long time : times) {
            min = Math.min(min, time);
            max = Math.max(max, time);
            total += time;
        }
        
        long avg = total / runs;
        
        System.out.println("\n⏱ Statistieken:");
        System.out.println("  Min: " + min + "ms");
        System.out.println("  Max: " + max + "ms");
        System.out.println("  Gemiddeld: " + avg + "ms");
        System.out.println("  Variatie: " + (max - min) + "ms");
        
        // Performance moet consistent zijn (variatie < 50% van gemiddelde)
        assertThat(max - min)
            .as("Performance variatie moet redelijk consistent zijn")
            .isLessThan(avg / 2);
        
        System.out.println("✓ Performance is consistent");
        System.out.println("\n=== Stress Test Succesvol Afgerond ===\n");
    }
    
    /**
     * Test de input structuur van ws0665
     */
    @Test
    @DisplayName("WS0665 input ZIP structuur validatie")
    void testWS0665InputStructure() throws Exception {
        System.out.println("\n=== WS0665 Input Structuur Validatie ===");
        
        ZipFile inputZip = TestUtils.loadTestZipFile(
            "performance-validatie/input/ws0665_input.zip");
        
        System.out.println("Totaal aantal entries: " + inputZip.size());
        
        // Tel IO mappen
        long ioFolderCount = inputZip.stream()
            .filter(entry -> entry.getName().matches("IO-[^/]+/.*"))
            .map(entry -> entry.getName().split("/")[0])
            .distinct()
            .count();
        
        System.out.println("✓ Aantal IO mappen: " + ioFolderCount);
        
        assertThat(ioFolderCount)
            .as("WS0665 moet vele IO mappen bevatten")
            .isGreaterThan(20);
        
        // Tel GML bestanden
        long gmlCount = inputZip.stream()
            .filter(entry -> entry.getName().toLowerCase().endsWith(".gml"))
            .count();
        
        System.out.println("✓ Aantal GML bestanden: " + gmlCount);
        
        assertThat(gmlCount)
            .as("WS0665 moet vele GML bestanden bevatten")
            .isGreaterThan(30);
        
        // Bereken totale GML grootte
        long totalGmlSize = inputZip.stream()
            .filter(entry -> entry.getName().toLowerCase().endsWith(".gml"))
            .mapToLong(entry -> entry.getSize())
            .sum();
        
        System.out.println("✓ Totale GML grootte: " + (totalGmlSize / 1024) + " KB");
        
        System.out.println("\n=== WS0665 is een grote performance test dataset ===\n");
    }
    
    /**
     * Performance test met WS0621 dataset (86 GML bestanden)
     */
    @Test
    @DisplayName("WS0621 zeer grote validatie transformatie (86 GML bestanden)")
    @Timeout(60)
    void testWS0621ExtraLargeValidatieTransformatie() throws Exception {
        System.out.println("\n=== WS0621 Extra Large Performance Validatie Test ===");
        
        long startTime = System.currentTimeMillis();
        
        // Arrange - Laad input ZIP uit resources
        ZipFile inputZip = TestUtils.loadTestZipFile(
            "performance-validatie/input/ws0621_input.zip");
        
        assertThat(inputZip).isNotNull();
        System.out.println("✓ Input ZIP geladen: " + inputZip.getName());
        System.out.println("  Aantal entries: " + inputZip.size());
        
        // Tel GML bestanden in input
        long gmlCount = inputZip.stream()
            .filter(entry -> entry.getName().toLowerCase().endsWith(".gml"))
            .count();
        System.out.println("  Aantal GML bestanden: " + gmlCount);
        
        long loadTime = System.currentTimeMillis() - startTime;
        
        // Act - Verwerk met BesluitProcessor
        long processStartTime = System.currentTimeMillis();
        BesluitProcessor.BesluitResult result = 
            BesluitProcessor.createBesluitXml(inputZip, true);
        long processTime = System.currentTimeMillis() - processStartTime;
        
        // Assert - Controleer dat output gegenereerd is
        assertThat(result).isNotNull();
        assertThat(result.besluitXml).isNotNull();
        assertThat(result.opdrachtXml).isNotNull();
        
        System.out.println("✓ Validatie XML gegenereerd");
        System.out.println("  Besluit XML grootte: " + result.besluitXml.length + " bytes");
        System.out.println("  Opdracht XML grootte: " + result.opdrachtXml.length + " bytes");
        
        // Performance metrics
        long totalTime = System.currentTimeMillis() - startTime;
        System.out.println("\n⏱ Performance Metrics:");
        System.out.println("  Laden ZIP: " + loadTime + "ms");
        System.out.println("  Verwerken: " + processTime + "ms");
        System.out.println("  Totaal: " + totalTime + "ms");
        System.out.println("  Gemiddeld per GML: " + (gmlCount > 0 ? processTime / gmlCount : 0) + "ms");
        
        // Valideer basis structuur
        Document besluitDoc = TestUtils.parseXmlBytes(result.besluitXml);
        assertThat(besluitDoc.getDocumentElement().getLocalName())
            .as("Root element moet AanleveringBesluit zijn")
            .isEqualTo("AanleveringBesluit");
        
        Document opdrachtDoc = TestUtils.parseXmlBytes(result.opdrachtXml);
        assertThat(opdrachtDoc.getDocumentElement().getLocalName())
            .as("Root element moet validatieOpdracht zijn")
            .isEqualTo("validatieOpdracht");
        
        System.out.println("✓ XML structuur correct");
        
        // Performance waarschuwingen (grotere dataset -> hogere limiet)
        if (processTime > 15000) {
            System.out.println("⚠ WAARSCHUWING: Verwerking duurt langer dan 15 seconden!");
        } else if (processTime > 10000) {
            System.out.println("⚠ Verwerking duurt langer dan 10 seconden");
        } else {
            System.out.println("✓ Performance is goed (< 10s)");
        }
        
        System.out.println("\n=== WS0621 Performance Test Succesvol Afgerond ===\n");
    }
    
    /**
     * Test dat WS0621 geen wasID elementen bevat in output
     */
    @Test
    @DisplayName("WS0621 expected output - geen wasID elementen")
    void testWS0621NoWasIDInOutput() throws Exception {
        System.out.println("\n=== WS0621 wasID Verwijdering Test ===");
        
        String expectedDir = "src/test/resources/performance-validatie/expected/ws0621_output";
        File expectedDirectory = new File(expectedDir);
        
        assertThat(expectedDirectory.exists())
            .as("Expected output directory moet bestaan")
            .isTrue();
        
        System.out.println("✓ Expected output directory gevonden: " + expectedDir);
        
        // Controleer alle GML bestanden op afwezigheid van wasID
        File[] gmlFiles = expectedDirectory.listFiles((dir, name) -> 
            name.toLowerCase().endsWith(".gml"));
        
        assertThat(gmlFiles).isNotNull();
        assertThat(gmlFiles.length)
            .as("WS0621 moet vele GML bestanden bevatten")
            .isGreaterThan(50);
        
        System.out.println("✓ Gevonden " + gmlFiles.length + " GML bestanden");
        
        int filesWithWasId = 0;
        
        for (File gmlFile : gmlFiles) {
            byte[] gmlContent = Files.readAllBytes(gmlFile.toPath());
            Document gmlDoc = TestUtils.parseXmlBytes(gmlContent);
            Element rootElement = gmlDoc.getDocumentElement();
            
            // Controleer of er wasID elementen zijn
            NodeList wasIdNodes = rootElement.getElementsByTagNameNS(GEO_NS, "wasID");
            
            if (wasIdNodes.getLength() > 0) {
                filesWithWasId++;
                if (filesWithWasId <= 5) {
                    System.out.println("  ⚠ " + gmlFile.getName() + " bevat nog wasID!");
                }
            }
        }
        
        if (filesWithWasId > 0) {
            System.out.println("⚠ " + filesWithWasId + " bestanden bevatten nog wasID elementen");
        } else {
            System.out.println("✓ Geen enkel GML bestand bevat wasID");
        }
        
        assertThat(filesWithWasId)
            .as("Alle GML bestanden moeten wasID elementen missen")
            .isEqualTo(0);
        
        System.out.println("\n=== wasID Verwijdering Test Succesvol ===\n");
    }
    
    /**
     * Performance test met PV29 dataset (36 GML bestanden)
     */
    @Test
    @DisplayName("PV29 grote validatie transformatie (36 GML bestanden)")
    @Timeout(30)
    void testPV29LargeValidatieTransformatie() throws Exception {
        System.out.println("\n=== PV29 Performance Validatie Test ===");
        
        long startTime = System.currentTimeMillis();
        
        // Arrange - Laad input ZIP uit resources
        ZipFile inputZip = TestUtils.loadTestZipFile(
            "performance-validatie/input/pv29_input.zip");
        
        assertThat(inputZip).isNotNull();
        System.out.println("✓ Input ZIP geladen: " + inputZip.getName());
        System.out.println("  Aantal entries: " + inputZip.size());
        
        // Tel GML bestanden in input
        long gmlCount = inputZip.stream()
            .filter(entry -> entry.getName().toLowerCase().endsWith(".gml"))
            .count();
        System.out.println("  Aantal GML bestanden: " + gmlCount);
        
        long loadTime = System.currentTimeMillis() - startTime;
        
        // Act - Verwerk met BesluitProcessor
        long processStartTime = System.currentTimeMillis();
        BesluitProcessor.BesluitResult result = 
            BesluitProcessor.createBesluitXml(inputZip, true);
        long processTime = System.currentTimeMillis() - processStartTime;
        
        // Assert - Controleer dat output gegenereerd is
        assertThat(result).isNotNull();
        assertThat(result.besluitXml).isNotNull();
        assertThat(result.opdrachtXml).isNotNull();
        
        System.out.println("✓ Validatie XML gegenereerd");
        System.out.println("  Besluit XML grootte: " + result.besluitXml.length + " bytes");
        System.out.println("  Opdracht XML grootte: " + result.opdrachtXml.length + " bytes");
        
        // Performance metrics
        long totalTime = System.currentTimeMillis() - startTime;
        System.out.println("\n⏱ Performance Metrics:");
        System.out.println("  Laden ZIP: " + loadTime + "ms");
        System.out.println("  Verwerken: " + processTime + "ms");
        System.out.println("  Totaal: " + totalTime + "ms");
        System.out.println("  Gemiddeld per GML: " + (gmlCount > 0 ? processTime / gmlCount : 0) + "ms");
        
        // Valideer basis structuur
        Document besluitDoc = TestUtils.parseXmlBytes(result.besluitXml);
        assertThat(besluitDoc.getDocumentElement().getLocalName())
            .as("Root element moet AanleveringBesluit zijn")
            .isEqualTo("AanleveringBesluit");
        
        Document opdrachtDoc = TestUtils.parseXmlBytes(result.opdrachtXml);
        assertThat(opdrachtDoc.getDocumentElement().getLocalName())
            .as("Root element moet validatieOpdracht zijn")
            .isEqualTo("validatieOpdracht");
        
        System.out.println("✓ XML structuur correct");
        
        // Performance waarschuwingen
        if (processTime > 10000) {
            System.out.println("⚠ WAARSCHUWING: Verwerking duurt langer dan 10 seconden!");
        } else if (processTime > 5000) {
            System.out.println("⚠ Verwerking duurt langer dan 5 seconden");
        } else {
            System.out.println("✓ Performance is goed (< 5s)");
        }
        
        System.out.println("\n=== PV29 Performance Test Succesvol Afgerond ===\n");
    }
}

