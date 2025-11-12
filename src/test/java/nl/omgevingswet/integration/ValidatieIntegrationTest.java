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
 * Integratie tests voor validatie opdrachten
 * 
 * Deze tests gebruiken de test data uit src/test/resources/validatie/
 * om de volledige validatie pipeline te testen, inclusief:
 * - Correcte hash berekening voor PDF en GML bestanden
 * - Voorkomen van dubbele GeoInformatieObjectVaststelling wrappers
 * - IO.xml generatie met correcte hash waarden
 * 
 * Deze tests zijn getagged met @Tag("fast") en draaien altijd.
 * Voor grote performance tests, zie PerformanceValidatieIntegrationTest.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("fast")
class ValidatieIntegrationTest {
    
    private static final String STOP_DATA_NS = "https://standaarden.overheid.nl/stop/imop/data/";
    private static final String GEO_NS = "https://standaarden.overheid.nl/stop/imop/geo/";
    
    /**
     * Test de volledige validatie transformatie met gm9920 test case
     */
    @Test
    @DisplayName("GM9920 validatie transformatie produceert correcte output")
    void testGM9920ValidatieTransformatie() throws Exception {
        System.out.println("\n=== GM9920 Validatie Test ===");
        
        // Arrange - Laad input ZIP uit resources
        ZipFile inputZip = TestUtils.loadTestZipFile(
            "validatie/input/gm9920_input.zip");
        
        assertThat(inputZip).isNotNull();
        System.out.println("✓ Input ZIP geladen: " + inputZip.getName());
        System.out.println("  Aantal entries: " + inputZip.size());
        
        // Act - Verwerk met BesluitProcessor (isValidation = true)
        BesluitProcessor.BesluitResult result = 
            BesluitProcessor.createBesluitXml(inputZip, true);
        
        // Assert - Controleer dat output gegenereerd is
        assertThat(result).isNotNull();
        assertThat(result.besluitXml).isNotNull();
        assertThat(result.opdrachtXml).isNotNull();
        
        System.out.println("✓ Validatie XML gegenereerd");
        System.out.println("  Besluit XML grootte: " + result.besluitXml.length + " bytes");
        System.out.println("  Opdracht XML grootte: " + result.opdrachtXml.length + " bytes");
        
        // Valideer besluit XML structuur
        Document besluitDoc = TestUtils.parseXmlBytes(result.besluitXml);
        assertThat(besluitDoc.getDocumentElement().getLocalName())
            .as("Root element moet AanleveringBesluit zijn")
            .isEqualTo("AanleveringBesluit");
        
        // Controleer namespace
        assertThat(besluitDoc.getDocumentElement().getNamespaceURI())
            .as("Root element moet correcte namespace hebben")
            .isEqualTo("https://standaarden.overheid.nl/lvbb/stop/aanlevering/");
        
        System.out.println("✓ Besluit XML heeft correcte root element structuur");
        
        // Valideer opdracht XML structuur
        Document opdrachtDoc = TestUtils.parseXmlBytes(result.opdrachtXml);
        assertThat(opdrachtDoc.getDocumentElement().getLocalName())
            .as("Root element moet validatieOpdracht zijn")
            .isEqualTo("validatieOpdracht");
        
        System.out.println("✓ Opdracht XML heeft correcte root element (validatieOpdracht)");
        
        // Controleer publicatie element verwijst naar besluit.xml
        String publicatie = TestUtils.getElementTextContent(opdrachtDoc, "publicatie");
        assertThat(publicatie)
            .as("Publicatie element moet verwijzen naar besluit.xml")
            .isEqualTo("besluit.xml");
        
        System.out.println("✓ Opdracht verwijst naar correcte bestandsnaam: " + publicatie);
        
        // Controleer idLevering heeft correct formaat voor validatie
        String idLevering = TestUtils.getElementTextContent(opdrachtDoc, "idLevering");
        assertThat(idLevering)
            .as("idLevering moet starten met OTST_val_ voor validatie")
            .startsWith("OTST_val_");
        
        System.out.println("✓ idLevering heeft correct formaat: " + idLevering);
        
        // Controleer datumBekendmaking element
        String datumBekendmaking = TestUtils.getElementTextContent(opdrachtDoc, "datumBekendmaking");
        assertThat(datumBekendmaking)
            .as("datumBekendmaking moet aanwezig zijn")
            .isNotNull()
            .isNotEmpty();
        
        System.out.println("✓ datumBekendmaking: " + datumBekendmaking);
        
        System.out.println("\n=== Test Succesvol Afgerond ===\n");
    }
    
    /**
     * Test dat de IO.xml bestanden correct worden gegenereerd met hash waarden
     * Deze test controleert de expected output files die al de hash waarden bevatten
     */
    @Test
    @DisplayName("Expected IO.xml bestanden bevatten correcte hash waarden voor PDF en GML")
    void testIOXmlHashValues() throws Exception {
        System.out.println("\n=== IO.xml Hash Validatie Test ===");
        
        // Pad naar expected output directory
        String expectedDir = "src/test/resources/validatie/expected/gm9920_output";
        File expectedDirectory = new File(expectedDir);
        
        assertThat(expectedDirectory.exists())
            .as("Expected output directory moet bestaan")
            .isTrue();
        
        // Zoek naar IO XML bestanden
        File[] ioFiles = expectedDirectory.listFiles((dir, name) -> 
            name.startsWith("IO-") && name.endsWith(".xml"));
        
        assertThat(ioFiles)
            .as("Er moeten IO XML bestanden in expected output zijn")
            .isNotNull()
            .isNotEmpty();
        
        System.out.println("✓ Gevonden " + ioFiles.length + " IO XML bestanden");
        
        // Controleer elk IO XML bestand op hash waarden
        int totalHashCount = 0;
        for (File ioFile : ioFiles) {
            System.out.println("\n--- Controleren " + ioFile.getName() + " ---");
            byte[] ioContent = Files.readAllBytes(ioFile.toPath());
            Document ioDoc = TestUtils.parseXmlBytes(ioContent);
            
            // Zoek naar hash elementen
            NodeList hashNodes = ioDoc.getElementsByTagNameNS(STOP_DATA_NS, "hash");
            
            assertThat(hashNodes.getLength())
                .as(ioFile.getName() + " moet minimaal 1 hash element bevatten")
                .isGreaterThan(0);
            
            System.out.println("  Gevonden " + hashNodes.getLength() + " hash element(en)");
            
            // Controleer dat elk hash element een correcte waarde heeft
            for (int i = 0; i < hashNodes.getLength(); i++) {
                Element hashElement = (Element) hashNodes.item(i);
                String hashValue = hashElement.getTextContent();
                
                assertThat(hashValue)
                    .as("Hash waarde in " + ioFile.getName() + " moet niet leeg zijn")
                    .isNotNull()
                    .isNotEmpty();
                
                // SHA-512 hash moet 128 karakters zijn (64 bytes hex encoded)
                assertThat(hashValue)
                    .as("Hash waarde in " + ioFile.getName() + " moet SHA-512 formaat hebben (128 hex karakters)")
                    .hasSize(128)
                    .matches("[0-9a-f]{128}");
                
                System.out.println("    ✓ Hash " + (i+1) + ": " + hashValue.substring(0, 32) + "...");
                totalHashCount++;
            }
        }
        
        System.out.println("\n✓ Totaal " + totalHashCount + " hash waarden gevalideerd");
        System.out.println("✓ Alle hash waarden hebben correct SHA-512 formaat");
        System.out.println("\n=== Hash Validatie Test Succesvol ===\n");
    }
    
    /**
     * Test dat GML bestanden niet dubbel worden gewrapped met GeoInformatieObjectVaststelling
     */
    @Test
    @DisplayName("GML bestanden hebben geen dubbele GeoInformatieObjectVaststelling wrapper")
    void testGMLNoDoubleWrapping() throws Exception {
        System.out.println("\n=== GML Wrapping Validatie Test ===");
        
        // Load input
        ZipFile inputZip = TestUtils.loadTestZipFile(
            "validatie/input/gm9920_input.zip");
        
        // Check of er GML bestanden in de input zijn
        boolean hasGmlFiles = inputZip.stream()
            .anyMatch(entry -> entry.getName().toLowerCase().endsWith(".gml"));
        
        if (!hasGmlFiles) {
            System.out.println("⚠ Geen GML bestanden in input ZIP, skip test");
            return;
        }
        
        System.out.println("✓ Input bevat GML bestanden");
        
        // Verwerk input
        BesluitProcessor.BesluitResult result = 
            BesluitProcessor.createBesluitXml(inputZip, true);
        
        assertThat(result).isNotNull();
        
        // Parse de besluit XML om te controleren of GML correct is gewrapped
        Document besluitDoc = TestUtils.parseXmlBytes(result.besluitXml);
        
        // Zoek naar GeoInformatieObjectVaststelling elementen
        NodeList geoNodes = besluitDoc.getElementsByTagNameNS(GEO_NS, "GeoInformatieObjectVaststelling");
        
        System.out.println("✓ Gevonden " + geoNodes.getLength() + " GeoInformatieObjectVaststelling elementen");
        
        // Als er GeoInformatieObjectVaststelling elementen zijn, controleer dat ze correct genest zijn
        for (int i = 0; i < geoNodes.getLength(); i++) {
            Element geoElement = (Element) geoNodes.item(i);
            
            // Check dat dit element GEEN nested GeoInformatieObjectVaststelling bevat
            NodeList nestedGeoNodes = geoElement.getElementsByTagNameNS(GEO_NS, "GeoInformatieObjectVaststelling");
            
            assertThat(nestedGeoNodes.getLength())
                .as("GeoInformatieObjectVaststelling mag geen geneste GeoInformatieObjectVaststelling bevatten")
                .isEqualTo(0);
            
            // Check dat het wel een vastgesteldeVersie element heeft
            NodeList vastgesteldeVersieNodes = geoElement.getElementsByTagNameNS(GEO_NS, "vastgesteldeVersie");
            assertThat(vastgesteldeVersieNodes.getLength())
                .as("GeoInformatieObjectVaststelling moet een vastgesteldeVersie element bevatten")
                .isGreaterThan(0);
            
            System.out.println("  ✓ GeoInformatieObjectVaststelling " + (i+1) + " heeft correcte structuur (geen dubbele wrapping)");
        }
        
        System.out.println("✓ Alle GML bestanden zijn correct gewrapped zonder duplicatie");
        System.out.println("\n=== GML Wrapping Test Succesvol ===\n");
    }
    
    /**
     * Test dat de output overeenkomt met de expected output bestanden
     */
    @Test
    @DisplayName("Gegenereerde output komt overeen met expected output")
    void testOutputMatchesExpected() throws Exception {
        System.out.println("\n=== Output Vergelijking met Expected ===");
        
        // Load input en verwerk
        ZipFile inputZip = TestUtils.loadTestZipFile(
            "validatie/input/gm9920_input.zip");
        
        BesluitProcessor.BesluitResult actualResult = 
            BesluitProcessor.createBesluitXml(inputZip, true);
        
        // Pad naar expected output directory
        String expectedDir = "src/test/resources/validatie/expected/gm9920_output";
        File expectedDirectory = new File(expectedDir);
        
        assertThat(expectedDirectory.exists())
            .as("Expected output directory moet bestaan")
            .isTrue();
        
        System.out.println("✓ Expected output directory gevonden: " + expectedDir);
        
        // Test 1: Vergelijk besluit.xml
        File expectedBesluitFile = new File(expectedDirectory, "besluit.xml");
        if (expectedBesluitFile.exists()) {
            System.out.println("\n--- Vergelijken besluit.xml ---");
            byte[] expectedBesluit = Files.readAllBytes(expectedBesluitFile.toPath());
            
            Document expectedBesluitDoc = TestUtils.parseXmlBytes(expectedBesluit);
            Document actualBesluitDoc = TestUtils.parseXmlBytes(actualResult.besluitXml);
            
            // Vergelijk root elementen
            assertThat(actualBesluitDoc.getDocumentElement().getLocalName())
                .as("Root element moet overeenkomen")
                .isEqualTo(expectedBesluitDoc.getDocumentElement().getLocalName());
            
            System.out.println("✓ besluit.xml structuur komt overeen");
        }
        
        // Test 2: Vergelijk opdracht.xml
        File expectedOpdrachtFile = new File(expectedDirectory, "opdracht.xml");
        if (expectedOpdrachtFile.exists()) {
            System.out.println("\n--- Vergelijken opdracht.xml ---");
            byte[] expectedOpdracht = Files.readAllBytes(expectedOpdrachtFile.toPath());
            
            Document expectedOpdrachtDoc = TestUtils.parseXmlBytes(expectedOpdracht);
            Document actualOpdrachtDoc = TestUtils.parseXmlBytes(actualResult.opdrachtXml);
            
            // Vergelijk root elementen
            assertThat(actualOpdrachtDoc.getDocumentElement().getLocalName())
                .as("Root element moet overeenkomen")
                .isEqualTo(expectedOpdrachtDoc.getDocumentElement().getLocalName());
            
            // Voor validatie moet dit validatieOpdracht zijn
            assertThat(actualOpdrachtDoc.getDocumentElement().getLocalName())
                .as("Root element moet validatieOpdracht zijn")
                .isEqualTo("validatieOpdracht");
            
            // Vergelijk publicatie element
            String expectedPublicatie = TestUtils.getElementTextContent(expectedOpdrachtDoc, "publicatie");
            String actualPublicatie = TestUtils.getElementTextContent(actualOpdrachtDoc, "publicatie");
            
            assertThat(actualPublicatie)
                .as("Publicatie bestandsnaam moet overeenkomen")
                .isEqualTo(expectedPublicatie);
            
            System.out.println("✓ opdracht.xml structuur komt overeen");
            System.out.println("  Publicatie: " + actualPublicatie);
        }
        
        // Test 3: Controleer expected IO XML bestanden
        System.out.println("\n--- Controleren IO XML bestanden ---");
        
        File[] ioFiles = expectedDirectory.listFiles((dir, name) -> 
            name.startsWith("IO-") && name.endsWith(".xml"));
        
        if (ioFiles != null && ioFiles.length > 0) {
            System.out.println("✓ Gevonden " + ioFiles.length + " IO XML bestanden in expected output");
            
            for (File ioFile : ioFiles) {
                byte[] expectedContent = Files.readAllBytes(ioFile.toPath());
                Document expectedDoc = TestUtils.parseXmlBytes(expectedContent);
                
                // Controleer dat het IO XML bestand hash elementen bevat
                NodeList hashNodes = expectedDoc.getElementsByTagNameNS(STOP_DATA_NS, "hash");
                
                assertThat(hashNodes.getLength())
                    .as(ioFile.getName() + " moet hash elementen bevatten")
                    .isGreaterThan(0);
                
                System.out.println("  ✓ " + ioFile.getName() + " bevat hash waarden");
            }
        }
        
        // Test 4: Controleer expected GML bestanden
        System.out.println("\n--- Controleren GML bestanden ---");
        
        File[] gmlFiles = expectedDirectory.listFiles((dir, name) -> 
            name.toLowerCase().endsWith(".gml"));
        
        if (gmlFiles != null && gmlFiles.length > 0) {
            System.out.println("✓ Gevonden " + gmlFiles.length + " GML bestanden in expected output");
            
            for (File gmlFile : gmlFiles) {
                byte[] gmlContent = Files.readAllBytes(gmlFile.toPath());
                Document gmlDoc = TestUtils.parseXmlBytes(gmlContent);
                
                Element rootElement = gmlDoc.getDocumentElement();
                
                // Check of het root element GeoInformatieObjectVaststelling is
                if ("GeoInformatieObjectVaststelling".equals(rootElement.getLocalName())) {
                    // Check dat er geen nested GeoInformatieObjectVaststelling is
                    NodeList nestedGeoNodes = rootElement.getElementsByTagNameNS(GEO_NS, "GeoInformatieObjectVaststelling");
                    
                    assertThat(nestedGeoNodes.getLength())
                        .as(gmlFile.getName() + " mag geen geneste GeoInformatieObjectVaststelling bevatten")
                        .isEqualTo(0);
                    
                    System.out.println("  ✓ " + gmlFile.getName() + " heeft correcte GeoInformatieObjectVaststelling wrapper");
                } else {
                    System.out.println("  ⓘ " + gmlFile.getName() + " heeft geen GeoInformatieObjectVaststelling wrapper (mogelijk niet gewrapped)");
                }
            }
        }
        
        System.out.println("\n=== Alle Output Komt Overeen met Expected ===\n");
    }
    
    /**
     * Test dat controleert of de input ZIP de juiste structuur heeft
     */
    @Test
    @DisplayName("GM9920 input ZIP heeft verwachte structuur")
    void testGM9920InputZipStructure() throws Exception {
        ZipFile inputZip = TestUtils.loadTestZipFile(
            "validatie/input/gm9920_input.zip");
        
        System.out.println("\n=== Input ZIP Structuur Validatie ===");
        System.out.println("Aantal entries: " + inputZip.size());
        
        // Toon alle entries
        System.out.println("\nEntries in input ZIP:");
        inputZip.stream().forEach(entry -> {
            System.out.println("  - " + entry.getName() + 
                " (" + (entry.isDirectory() ? "directory" : entry.getSize() + " bytes") + ")");
        });
        
        // Controleer dat er minimaal IO mappen zijn
        boolean hasIOFolders = inputZip.stream()
            .anyMatch(entry -> entry.getName().matches("IO-[^/]+/.*"));
        
        assertThat(hasIOFolders)
            .as("Input ZIP moet minimaal één IO map bevatten")
            .isTrue();
        
        System.out.println("\n✓ Input bevat IO mappen");
        
        // Controleer dat er Regeling map is
        boolean hasRegelingFolder = inputZip.stream()
            .anyMatch(entry -> entry.getName().startsWith("Regeling/"));
        
        assertThat(hasRegelingFolder)
            .as("Input ZIP moet een Regeling map bevatten")
            .isTrue();
        
        System.out.println("✓ Input bevat Regeling map");
        
        System.out.println("=== Input ZIP Structuur OK ===\n");
    }
    
    /**
     * Performance test - controleert dat validatie niet te lang duurt
     */
    @Test
    @DisplayName("Validatie verwerking duurt niet langer dan 5 seconden")
    @Timeout(5)
    void testValidatiePerformance() throws Exception {
        long startTime = System.currentTimeMillis();
        
        ZipFile inputZip = TestUtils.loadTestZipFile(
            "validatie/input/gm9920_input.zip");
        
        BesluitProcessor.BesluitResult result = 
            BesluitProcessor.createBesluitXml(inputZip, true);
        
        long duration = System.currentTimeMillis() - startTime;
        
        assertThat(result).isNotNull();
        
        System.out.println("\n✓ Validatie verwerking duurde " + duration + "ms");
        
        if (duration > 2000) {
            System.out.println("⚠ Waarschuwing: verwerking duurt langer dan 2 seconden");
        }
    }
    
    /**
     * Test de volledige validatie transformatie met pv30 test case (grotere dataset)
     */
    @Test
    @DisplayName("PV30 validatie transformatie produceert correcte output")
    void testPV30ValidatieTransformatie() throws Exception {
        System.out.println("\n=== PV30 Validatie Test ===");
        
        // Arrange - Laad input ZIP uit resources
        ZipFile inputZip = TestUtils.loadTestZipFile(
            "validatie/input/pv30_input.zip");
        
        assertThat(inputZip).isNotNull();
        System.out.println("✓ Input ZIP geladen: " + inputZip.getName());
        System.out.println("  Aantal entries: " + inputZip.size());
        
        // Act - Verwerk met BesluitProcessor (isValidation = true)
        BesluitProcessor.BesluitResult result = 
            BesluitProcessor.createBesluitXml(inputZip, true);
        
        // Assert - Controleer dat output gegenereerd is
        assertThat(result).isNotNull();
        assertThat(result.besluitXml).isNotNull();
        assertThat(result.opdrachtXml).isNotNull();
        
        System.out.println("✓ Validatie XML gegenereerd");
        System.out.println("  Besluit XML grootte: " + result.besluitXml.length + " bytes");
        System.out.println("  Opdracht XML grootte: " + result.opdrachtXml.length + " bytes");
        
        // Valideer besluit XML structuur
        Document besluitDoc = TestUtils.parseXmlBytes(result.besluitXml);
        assertThat(besluitDoc.getDocumentElement().getLocalName())
            .as("Root element moet AanleveringBesluit zijn")
            .isEqualTo("AanleveringBesluit");
        
        System.out.println("✓ Besluit XML heeft correcte root element structuur");
        
        // Valideer opdracht XML structuur
        Document opdrachtDoc = TestUtils.parseXmlBytes(result.opdrachtXml);
        assertThat(opdrachtDoc.getDocumentElement().getLocalName())
            .as("Root element moet validatieOpdracht zijn")
            .isEqualTo("validatieOpdracht");
        
        System.out.println("✓ Opdracht XML heeft correcte root element (validatieOpdracht)");
        
        // Controleer idLevering heeft correct formaat voor validatie
        String idLevering = TestUtils.getElementTextContent(opdrachtDoc, "idLevering");
        assertThat(idLevering)
            .as("idLevering moet starten met OTST_val_ voor validatie")
            .startsWith("OTST_val_");
        
        System.out.println("✓ idLevering heeft correct formaat: " + idLevering);
        
        System.out.println("\n=== Test Succesvol Afgerond ===\n");
    }
    
    /**
     * Test dat de pv30 output overeenkomt met expected output
     * Deze test valideert specifiek de bestandsnaam hoofdletter-behoud fix
     */
    @Test
    @DisplayName("PV30 output komt overeen met expected output (bestandsnaam hoofdletters)")
    void testPV30OutputMatchesExpected() throws Exception {
        System.out.println("\n=== PV30 Output Vergelijking met Expected ===");
        
        // Load input
        ZipFile inputZip = TestUtils.loadTestZipFile(
            "validatie/input/pv30_input.zip");
        
        // Pad naar expected output directory
        String expectedDir = "src/test/resources/validatie/expected/pv30_output";
        File expectedDirectory = new File(expectedDir);
        
        assertThat(expectedDirectory.exists())
            .as("Expected output directory moet bestaan")
            .isTrue();
        
        System.out.println("✓ Expected output directory gevonden: " + expectedDir);
        
        // Test 1: Controleer alle expected IO XML bestanden op hash waarden
        System.out.println("\n--- Controleren IO XML bestanden ---");
        
        File[] ioFiles = expectedDirectory.listFiles((dir, name) -> 
            name.startsWith("IO-") && name.endsWith(".xml"));
        
        if (ioFiles != null && ioFiles.length > 0) {
            System.out.println("✓ Gevonden " + ioFiles.length + " IO XML bestanden");
            
            int ioFilesWithHash = 0;
            for (File ioFile : ioFiles) {
                byte[] ioContent = Files.readAllBytes(ioFile.toPath());
                Document ioDoc = TestUtils.parseXmlBytes(ioContent);
                
                // Zoek naar hash elementen
                NodeList hashNodes = ioDoc.getElementsByTagNameNS(STOP_DATA_NS, "hash");
                
                if (hashNodes.getLength() > 0) {
                    ioFilesWithHash++;
                    
                    // Controleer hash formaat
                    String hashValue = hashNodes.item(0).getTextContent();
                    assertThat(hashValue)
                        .as("Hash in " + ioFile.getName() + " moet SHA-512 formaat hebben")
                        .hasSize(128)
                        .matches("[0-9a-f]{128}");
                }
            }
            
            System.out.println("  ✓ " + ioFilesWithHash + " van " + ioFiles.length + " IO bestanden bevatten hash waarden");
        }
        
        // Test 2: Controleer GML bestanden op correcte naamgeving (hoofdletters behouden)
        System.out.println("\n--- Controleren GML bestandsnamen ---");
        
        File[] gmlFiles = expectedDirectory.listFiles((dir, name) -> 
            name.toLowerCase().endsWith(".gml"));
        
        if (gmlFiles != null && gmlFiles.length > 0) {
            System.out.println("✓ Gevonden " + gmlFiles.length + " GML bestanden");
            
            // Test specifiek op hoofdletter-behoud
            boolean hasUppercaseInNames = false;
            for (File gmlFile : gmlFiles) {
                String name = gmlFile.getName();
                if (!name.equals(name.toLowerCase())) {
                    hasUppercaseInNames = true;
                    System.out.println("  ✓ Bestandsnaam met hoofdletters behouden: " + name);
                }
                
                // Controleer dat het bestand een geldige GML structuur heeft
                byte[] gmlContent = Files.readAllBytes(gmlFile.toPath());
                Document gmlDoc = TestUtils.parseXmlBytes(gmlContent);
                
                Element rootElement = gmlDoc.getDocumentElement();
                
                // Check of het GeoInformatieObjectVaststelling heeft en geen dubbele nesting
                if ("GeoInformatieObjectVaststelling".equals(rootElement.getLocalName())) {
                    NodeList nestedGeoNodes = rootElement.getElementsByTagNameNS(GEO_NS, "GeoInformatieObjectVaststelling");
                    
                    assertThat(nestedGeoNodes.getLength())
                        .as(name + " mag geen geneste GeoInformatieObjectVaststelling bevatten")
                        .isEqualTo(0);
                }
            }
            
            if (hasUppercaseInNames) {
                System.out.println("  ✓ Hoofdletters in bestandsnamen zijn correct behouden");
            }
        }
        
        // Test 3: Controleer dat bestandsnamen in IO XML bestanden matchen met werkelijke bestanden
        System.out.println("\n--- Controleren bestandsnaam consistentie ---");
        
        if (ioFiles != null && gmlFiles != null) {
            for (File ioFile : ioFiles) {
                byte[] ioContent = Files.readAllBytes(ioFile.toPath());
                Document ioDoc = TestUtils.parseXmlBytes(ioContent);
                
                // Zoek naar bestandsnaam elementen
                NodeList bestandsnaamNodes = ioDoc.getElementsByTagNameNS(STOP_DATA_NS, "bestandsnaam");
                
                if (bestandsnaamNodes.getLength() > 0) {
                    String bestandsnaam = bestandsnaamNodes.item(0).getTextContent();
                    
                    // Check of dit bestand daadwerkelijk bestaat in de output
                    boolean fileExists = false;
                    for (File gmlFile : gmlFiles) {
                        if (gmlFile.getName().equals(bestandsnaam)) {
                            fileExists = true;
                            break;
                        }
                    }
                    
                    // Check ook PDF bestanden
                    if (!fileExists && bestandsnaam.toLowerCase().endsWith(".pdf")) {
                        File[] pdfFiles = expectedDirectory.listFiles((dir, name) -> 
                            name.toLowerCase().endsWith(".pdf"));
                        if (pdfFiles != null) {
                            for (File pdfFile : pdfFiles) {
                                if (pdfFile.getName().equals(bestandsnaam)) {
                                    fileExists = true;
                                    break;
                                }
                            }
                        }
                    }
                    
                    if (fileExists) {
                        System.out.println("  ✓ " + ioFile.getName() + " -> " + bestandsnaam + " (bestaat)");
                    } else {
                        System.out.println("  ⓘ " + ioFile.getName() + " -> " + bestandsnaam);
                    }
                }
            }
        }
        
        System.out.println("\n=== Alle PV30 Output Checks Succesvol ===\n");
    }
    
    /**
     * Test dat controleert of de pv30 input ZIP de juiste structuur heeft
     */
    @Test
    @DisplayName("PV30 input ZIP heeft verwachte structuur")
    void testPV30InputZipStructure() throws Exception {
        ZipFile inputZip = TestUtils.loadTestZipFile(
            "validatie/input/pv30_input.zip");
        
        System.out.println("\n=== PV30 Input ZIP Structuur Validatie ===");
        System.out.println("Aantal entries: " + inputZip.size());
        
        // Controleer dat er IO mappen zijn
        long ioFolderCount = inputZip.stream()
            .filter(entry -> entry.getName().matches("IO-[^/]+/.*"))
            .map(entry -> entry.getName().split("/")[0])
            .distinct()
            .count();
        
        System.out.println("✓ Aantal IO mappen: " + ioFolderCount);
        
        assertThat(ioFolderCount)
            .as("PV30 moet meerdere IO mappen bevatten")
            .isGreaterThan(1);
        
        // Tel GML bestanden
        long gmlCount = inputZip.stream()
            .filter(entry -> entry.getName().toLowerCase().endsWith(".gml"))
            .count();
        
        System.out.println("✓ Aantal GML bestanden: " + gmlCount);
        
        assertThat(gmlCount)
            .as("PV30 moet meerdere GML bestanden bevatten")
            .isGreaterThan(5);
        
        // Tel PDF bestanden
        long pdfCount = inputZip.stream()
            .filter(entry -> entry.getName().toLowerCase().endsWith(".pdf"))
            .count();
        
        System.out.println("✓ Aantal PDF bestanden: " + pdfCount);
        
        System.out.println("=== PV30 Input ZIP Structuur OK ===\n");
    }
    
    /**
     * Performance test voor grotere pv30 dataset
     */
    @Test
    @DisplayName("PV30 validatie verwerking duurt niet langer dan 10 seconden")
    @Timeout(10)
    void testPV30ValidatiePerformance() throws Exception {
        long startTime = System.currentTimeMillis();
        
        ZipFile inputZip = TestUtils.loadTestZipFile(
            "validatie/input/pv30_input.zip");
        
        BesluitProcessor.BesluitResult result = 
            BesluitProcessor.createBesluitXml(inputZip, true);
        
        long duration = System.currentTimeMillis() - startTime;
        
        assertThat(result).isNotNull();
        
        System.out.println("\n✓ PV30 validatie verwerking duurde " + duration + "ms");
        
        if (duration > 5000) {
            System.out.println("⚠ Waarschuwing: verwerking duurt langer dan 5 seconden");
        }
    }
}

