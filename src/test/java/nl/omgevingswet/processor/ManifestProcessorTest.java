package nl.omgevingswet.processor;

import nl.omgevingswet.ManifestProcessor;
import nl.omgevingswet.util.TestUtils;
import org.junit.jupiter.api.*;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests voor ManifestProcessor
 * 
 * Deze tests controleren of de ManifestProcessor correct:
 * - Manifest bestanden genereert
 * - Content types correct bepaalt
 * - Alleen relevante bestanden opneemt
 */
class ManifestProcessorTest {
    
    @Test
    @DisplayName("generateManifest maakt valide XML")
    void testGenerateManifestCreatesValidXml() throws Exception {
        Set<String> testFiles = new HashSet<>();
        testFiles.add("besluit.xml");
        testFiles.add("opdracht.xml");
        testFiles.add("test.pdf");
        
        byte[] manifest = ManifestProcessor.generateManifest(null, testFiles, false);
        
        // Parse de XML
        Document doc = TestUtils.parseXmlBytes(manifest);
        
        // Controleer root element
        assertThat(doc.getDocumentElement().getLocalName()).isEqualTo("manifest");
        assertThat(doc.getDocumentElement().getNamespaceURI())
            .isEqualTo("http://www.overheid.nl/2017/lvbb");
    }
    
    @Test
    @DisplayName("generateManifest voegt alle bestanden toe")
    void testGenerateManifestIncludesAllFiles() throws Exception {
        Set<String> testFiles = new HashSet<>();
        testFiles.add("besluit.xml");
        testFiles.add("opdracht.xml");
        testFiles.add("test.gml");
        
        byte[] manifest = ManifestProcessor.generateManifest(null, testFiles, false);
        Document doc = TestUtils.parseXmlBytes(manifest);
        
        // Tel het aantal bestand elementen
        NodeList bestandNodes = doc.getElementsByTagName("bestand");
        assertThat(bestandNodes.getLength()).isEqualTo(3);
    }
    
    @Test
    @DisplayName("generateManifest gebruikt correcte content types")
    void testGenerateManifestUsesCorrectContentTypes() throws Exception {
        Set<String> testFiles = new HashSet<>();
        testFiles.add("test.xml");
        testFiles.add("test.gml");
        testFiles.add("test.pdf");
        testFiles.add("test.jpg");
        testFiles.add("test.png");
        
        byte[] manifest = ManifestProcessor.generateManifest(null, testFiles, false);
        Document doc = TestUtils.parseXmlBytes(manifest);
        
        // Controleer of de content types correct zijn
        NodeList contentTypeNodes = doc.getElementsByTagName("contentType");
        
        // We verwachten 5 contentType elementen
        assertThat(contentTypeNodes.getLength()).isEqualTo(5);
        
        // Specifieke content types kunnen we niet eenvoudig testen zonder
        // de volgorde te kennen, maar we kunnen wel controleren dat ze bestaan
        boolean hasXml = false;
        boolean hasGml = false;
        boolean hasPdf = false;
        boolean hasImage = false;
        
        for (int i = 0; i < contentTypeNodes.getLength(); i++) {
            String contentType = contentTypeNodes.item(i).getTextContent();
            if (contentType.equals("application/xml")) hasXml = true;
            if (contentType.equals("application/gml+xml")) hasGml = true;
            if (contentType.equals("application/pdf")) hasPdf = true;
            if (contentType.equals("image/jpeg") || contentType.equals("image/png")) hasImage = true;
        }
        
        assertThat(hasXml).isTrue();
        assertThat(hasGml).isTrue();
        assertThat(hasPdf).isTrue();
        assertThat(hasImage).isTrue();
    }
    
    @Test
    @DisplayName("generateManifest slaat IO-bestanden over bij intrekking")
    void testGenerateManifestSkipsIoFilesForIntrekking() throws Exception {
        Set<String> testFiles = new HashSet<>();
        testFiles.add("besluit.xml");
        testFiles.add("IO-1234.xml");
        testFiles.add("IO-5678.xml");
        
        // Met isIntrekking=true moeten IO bestanden worden overgeslagen
        byte[] manifest = ManifestProcessor.generateManifest(null, testFiles, true);
        Document doc = TestUtils.parseXmlBytes(manifest);
        
        // Alleen besluit.xml moet aanwezig zijn
        NodeList bestandNodes = doc.getElementsByTagName("bestand");
        assertThat(bestandNodes.getLength()).isEqualTo(1);
        
        // Controleer dat het besluit.xml is
        NodeList bestandsnaamNodes = doc.getElementsByTagName("bestandsnaam");
        assertThat(bestandsnaamNodes.item(0).getTextContent()).isEqualTo("besluit.xml");
    }
    
    @Test
    @DisplayName("generateManifest behoudt IO-bestanden bij normale verwerking")
    void testGenerateManifestKeepsIoFilesForNormalProcessing() throws Exception {
        Set<String> testFiles = new HashSet<>();
        testFiles.add("besluit.xml");
        testFiles.add("IO-1234.xml");
        testFiles.add("IO-5678.xml");
        
        // Met isIntrekking=false moeten alle bestanden worden opgenomen
        byte[] manifest = ManifestProcessor.generateManifest(null, testFiles, false);
        Document doc = TestUtils.parseXmlBytes(manifest);
        
        NodeList bestandNodes = doc.getElementsByTagName("bestand");
        assertThat(bestandNodes.getLength()).isEqualTo(3);
    }
}


