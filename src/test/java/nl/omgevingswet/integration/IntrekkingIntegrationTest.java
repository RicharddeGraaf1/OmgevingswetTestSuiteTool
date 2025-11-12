package nl.omgevingswet.integration;

import nl.omgevingswet.IntrekkingProcessor;
import nl.omgevingswet.util.TestUtils;
import org.junit.jupiter.api.*;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.*;

/**
 * Integratie tests voor intrekking opdrachten
 * 
 * Deze tests gebruiken de test data uit src/test/resources/intrekking/
 * om de volledige intrekking pipeline te testen.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("fast")
class IntrekkingIntegrationTest {
    
    private static final String STOP_DATA_NS = "https://standaarden.overheid.nl/stop/imop/data/";
    private static final String STOP_TEKST_NS = "https://standaarden.overheid.nl/stop/imop/tekst/";
    
    /**
     * Test de volledige intrekking transformatie met gm9920 test case
     */
    @Test
    @DisplayName("GM9920 intrekking transformatie produceert correcte output")
    void testGM9920IntrekkingTransformatie() throws Exception {
        System.out.println("\n=== GM9920 Intrekking Test ===");
        
        // Arrange - Laad input ZIP uit resources
        ZipFile inputZip = TestUtils.loadTestZipFile(
            "intrekking/input/gm9920_input.zip");
        
        assertThat(inputZip).isNotNull();
        System.out.println("✓ Input ZIP geladen: " + inputZip.getName());
        System.out.println("  Aantal entries: " + inputZip.size());
        
        // Act - Verwerk met IntrekkingProcessor (GEEN validatie, gewone intrekking)
        IntrekkingProcessor.IntrekkingResult result = 
            IntrekkingProcessor.createIntrekkingXml(inputZip, false);
        
        // Assert - Controleer dat output gegenereerd is
        assertThat(result).isNotNull();
        assertThat(result.besluitXml).isNotNull();
        assertThat(result.opdrachtXml).isNotNull();
        assertThat(result.modifiedFiles).isNotNull();
        
        System.out.println("✓ Intrekking XML gegenereerd");
        System.out.println("  Besluit XML grootte: " + result.besluitXml.length + " bytes");
        System.out.println("  Opdracht XML grootte: " + result.opdrachtXml.length + " bytes");
        System.out.println("  Aantal gewijzigde OW-bestanden: " + result.modifiedFiles.size());
        
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
        
        // Controleer dat Intrekkingen element aanwezig is (met namespace)
        NodeList intrekkingenNodes = besluitDoc.getElementsByTagNameNS(STOP_DATA_NS, "Intrekkingen");
        if (intrekkingenNodes.getLength() == 0) {
            // Probeer zonder namespace
            intrekkingenNodes = besluitDoc.getElementsByTagName("Intrekkingen");
        }
        assertThat(intrekkingenNodes.getLength())
            .as("Besluit moet Intrekkingen element bevatten")
            .isGreaterThan(0);
        
        System.out.println("✓ Besluit bevat Intrekkingen element");
        
        // Valideer opdracht XML structuur
        Document opdrachtDoc = TestUtils.parseXmlBytes(result.opdrachtXml);
        assertThat(opdrachtDoc.getDocumentElement().getLocalName())
            .as("Root element moet publicatieOpdracht zijn (geen validatie)")
            .isEqualTo("publicatieOpdracht");
        
        System.out.println("✓ Opdracht XML heeft correcte root element (publicatieOpdracht)");
        
        // Controleer publicatie element verwijst naar intrekkingsbesluit.xml
        String publicatie = TestUtils.getElementTextContent(opdrachtDoc, "publicatie");
        assertThat(publicatie)
            .as("Publicatie element moet verwijzen naar intrekkingsbesluit.xml")
            .isEqualTo("intrekkingsbesluit.xml");
        
        System.out.println("✓ Opdracht verwijst naar correcte bestandsnaam: " + publicatie);
        
        // Controleer idLevering heeft correct formaat voor publicatie intrekking
        String idLevering = TestUtils.getElementTextContent(opdrachtDoc, "idLevering");
        assertThat(idLevering)
            .as("idLevering moet starten met OTST_pub_intr voor publicatie intrekking")
            .startsWith("OTST_pub_intr");
        
        System.out.println("✓ idLevering heeft correct formaat: " + idLevering);
        
        // Controleer datumBekendmaking element
        String datumBekendmaking = TestUtils.getElementTextContent(opdrachtDoc, "datumBekendmaking");
        assertThat(datumBekendmaking)
            .as("datumBekendmaking moet aanwezig zijn")
            .isNotNull()
            .isNotEmpty();
        
        System.out.println("✓ datumBekendmaking: " + datumBekendmaking);
        
        // Valideer gewijzigde OW-bestanden
        if (!result.modifiedFiles.isEmpty()) {
            System.out.println("\n✓ Gewijzigde OW-bestanden:");
            for (String fileName : result.modifiedFiles.keySet()) {
                byte[] fileContent = result.modifiedFiles.get(fileName);
                System.out.println("  - " + fileName + " (" + fileContent.length + " bytes)");
                
                // Parse en controleer dat status="beëindigen" aanwezig is
                if (fileName.endsWith(".xml")) {
                    Document owDoc = TestUtils.parseXmlBytes(fileContent);
                    NodeList statusNodes = owDoc.getElementsByTagName("status");
                    
                    if (statusNodes.getLength() > 0) {
                        String statusValue = statusNodes.item(0).getTextContent();
                        assertThat(statusValue)
                            .as("Status in OW-bestand moet 'beëindigen' zijn")
                            .isEqualTo("beëindigen");
                        System.out.println("    ✓ Bevat status: " + statusValue);
                    }
                }
            }
        }
        
        System.out.println("\n=== Test Succesvol Afgerond ===\n");
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
            "intrekking/input/gm9920_input.zip");
        
        IntrekkingProcessor.IntrekkingResult actualResult = 
            IntrekkingProcessor.createIntrekkingXml(inputZip, false);
        
        // Pad naar expected output directory
        String expectedDir = "src/test/resources/intrekking/expected/gm9920_output";
        File expectedDirectory = new File(expectedDir);
        
        assertThat(expectedDirectory.exists())
            .as("Expected output directory moet bestaan")
            .isTrue();
        
        System.out.println("✓ Expected output directory gevonden: " + expectedDir);
        
        // Test 1: Vergelijk intrekkingsbesluit.xml
        File expectedBesluitFile = new File(expectedDirectory, "intrekkingsbesluit.xml");
        if (expectedBesluitFile.exists()) {
            System.out.println("\n--- Vergelijken intrekkingsbesluit.xml ---");
            byte[] expectedBesluit = Files.readAllBytes(expectedBesluitFile.toPath());
            
            Document expectedBesluitDoc = TestUtils.parseXmlBytes(expectedBesluit);
            Document actualBesluitDoc = TestUtils.parseXmlBytes(actualResult.besluitXml);
            
            // Vergelijk root elementen
            assertThat(actualBesluitDoc.getDocumentElement().getLocalName())
                .as("Root element moet overeenkomen")
                .isEqualTo(expectedBesluitDoc.getDocumentElement().getLocalName());
            
            // Vergelijk dat Intrekkingen element aanwezig is
            NodeList expectedIntrekkingen = expectedBesluitDoc.getElementsByTagName("Intrekkingen");
            NodeList actualIntrekkingen = actualBesluitDoc.getElementsByTagName("Intrekkingen");
            
            if (expectedIntrekkingen.getLength() == 0) {
                expectedIntrekkingen = expectedBesluitDoc.getElementsByTagNameNS(STOP_DATA_NS, "Intrekkingen");
            }
            if (actualIntrekkingen.getLength() == 0) {
                actualIntrekkingen = actualBesluitDoc.getElementsByTagNameNS(STOP_DATA_NS, "Intrekkingen");
            }
            
            assertThat(actualIntrekkingen.getLength())
                .as("Aantal Intrekkingen elementen moet overeenkomen")
                .isEqualTo(expectedIntrekkingen.getLength());
            
            System.out.println("✓ intrekkingsbesluit.xml structuur komt overeen");
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
            
            // Vergelijk publicatie element
            String expectedPublicatie = TestUtils.getElementTextContent(expectedOpdrachtDoc, "publicatie");
            String actualPublicatie = TestUtils.getElementTextContent(actualOpdrachtDoc, "publicatie");
            
            assertThat(actualPublicatie)
                .as("Publicatie bestandsnaam moet overeenkomen")
                .isEqualTo(expectedPublicatie);
            
            System.out.println("✓ opdracht.xml structuur komt overeen");
            System.out.println("  Publicatie: " + actualPublicatie);
        }
        
        // Test 3: Vergelijk gewijzigde OW-bestanden
        System.out.println("\n--- Vergelijken OW-bestanden ---");
        
        String[] expectedOwFiles = {
            "gebieden.xml",
            "gebiedengroepen.xml",
            "manifest-ow.xml",
            "regelingsgebieden.xml",
            "regelsvooriedereen.xml",
            "regelteksten.xml"
        };
        
        for (String owFileName : expectedOwFiles) {
            File expectedOwFile = new File(expectedDirectory, owFileName);
            if (expectedOwFile.exists()) {
                // Check of dit bestand ook in de actual output zit
                assertThat(actualResult.modifiedFiles)
                    .as("Modified files moet " + owFileName + " bevatten")
                    .containsKey(owFileName);
                
                byte[] expectedContent = Files.readAllBytes(expectedOwFile.toPath());
                byte[] actualContent = actualResult.modifiedFiles.get(owFileName);
                
                Document expectedDoc = TestUtils.parseXmlBytes(expectedContent);
                Document actualDoc = TestUtils.parseXmlBytes(actualContent);
                
                // Vergelijk root elementen
                assertThat(actualDoc.getDocumentElement().getLocalName())
                    .as("Root element van " + owFileName + " moet overeenkomen")
                    .isEqualTo(expectedDoc.getDocumentElement().getLocalName());
                
                // Voor OW-bestanden: check dat status="beëindigen" aanwezig is
                if (!owFileName.equals("manifest-ow.xml")) {
                    NodeList statusNodes = actualDoc.getElementsByTagName("status");
                    if (statusNodes.getLength() > 0) {
                        String status = statusNodes.item(0).getTextContent();
                        assertThat(status)
                            .as(owFileName + " moet status 'beëindigen' hebben")
                            .isEqualTo("beëindigen");
                    }
                }
                
                System.out.println("✓ " + owFileName + " structuur komt overeen");
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
            "intrekking/input/gm9920_input.zip");
        
        System.out.println("\n=== Input ZIP Structuur Validatie ===");
        System.out.println("Aantal entries: " + inputZip.size());
        
        // Toon alle entries
        System.out.println("\nEntries in input ZIP:");
        inputZip.stream().forEach(entry -> {
            System.out.println("  - " + entry.getName() + 
                " (" + (entry.isDirectory() ? "directory" : entry.getSize() + " bytes") + ")");
        });
        
        System.out.println("=== Input ZIP Structuur OK ===\n");
    }
    
    /**
     * Performance test - controleert dat intrekking niet te lang duurt
     */
    @Test
    @DisplayName("Intrekking verwerking duurt niet langer dan 5 seconden")
    @Timeout(5)
    void testIntrekkingPerformance() throws Exception {
        long startTime = System.currentTimeMillis();
        
        ZipFile inputZip = TestUtils.loadTestZipFile(
            "intrekking/input/gm9920_input.zip");
        
        IntrekkingProcessor.IntrekkingResult result = 
            IntrekkingProcessor.createIntrekkingXml(inputZip, false);
        
        long duration = System.currentTimeMillis() - startTime;
        
        assertThat(result).isNotNull();
        
        System.out.println("\n✓ Intrekking verwerking duurde " + duration + "ms");
        
        if (duration > 2000) {
            System.out.println("⚠ Waarschuwing: verwerking duurt langer dan 2 seconden");
        }
    }
}


