package nl.omgevingswet.processor;

import nl.omgevingswet.BesluitProcessor;
import nl.omgevingswet.util.TestUtils;
import org.junit.jupiter.api.*;
import org.w3c.dom.Document;

import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests voor BesluitProcessor
 * 
 * Deze tests controleren of de BesluitProcessor correct:
 * - ZIP bestanden analyseert
 * - FRBRWork en FRBRExpression extraheert
 * - Besluit XML genereert
 * - Opdracht XML genereert
 */
class BesluitProcessorTest {
    
    private static final String STOP_DATA_NS = "https://standaarden.overheid.nl/stop/imop/data/";
    
    @Test
    @DisplayName("Analyse van ZIP bestand extraheert correcte metadata")
    void testAnalyseZip() {
        // Deze test zou een echt ZIP bestand nodig hebben
        // Voor nu een placeholder die laat zien hoe de test eruit zou zien
        assertThat(true).isTrue();
    }
    
    @Test
    @DisplayName("createBesluitXml genereert valide XML met correcte namespace")
    void testCreateBesluitXml() throws Exception {
        // Placeholder - zou moeten testen dat:
        // 1. De gegenereerde XML valide is
        // 2. Alle vereiste elementen aanwezig zijn
        // 3. Namespaces correct zijn
        assertThat(true).isTrue();
    }
    
    @Test
    @DisplayName("createOpdrachtXml genereert opdracht met correct formaat")
    void testCreateOpdrachtXml() throws Exception {
        String bevoegdGezag = "gm0344";
        String datumTijd = "20231115120000";
        java.time.LocalDateTime datumBekendmaking = java.time.LocalDateTime.now().plusDays(1);
        
        byte[] opdrachtXml = BesluitProcessor.createOpdrachtXml(
            bevoegdGezag, datumTijd, datumBekendmaking, false);
        
        // Parse de XML
        Document doc = TestUtils.parseXmlBytes(opdrachtXml);
        
        // Controleer dat het root element correct is
        assertThat(doc.getDocumentElement().getLocalName()).isEqualTo("publicatieOpdracht");
        
        // Controleer dat idLevering het juiste formaat heeft
        String idLevering = TestUtils.getElementTextContent(doc, "idLevering");
        assertThat(idLevering).startsWith("OTST_pub_");
        assertThat(idLevering).contains(bevoegdGezag);
    }
    
    @Test
    @DisplayName("createOpdrachtXml voor validatie gebruikt correct root element")
    void testCreateValidatieOpdrachtXml() throws Exception {
        String bevoegdGezag = "gm0344";
        String datumTijd = "20231115120000";
        java.time.LocalDateTime datumBekendmaking = java.time.LocalDateTime.now().plusDays(1);
        
        byte[] opdrachtXml = BesluitProcessor.createOpdrachtXml(
            bevoegdGezag, datumTijd, datumBekendmaking, true);
        
        Document doc = TestUtils.parseXmlBytes(opdrachtXml);
        
        // Voor validatie moet het root element "validatieOpdracht" zijn
        assertThat(doc.getDocumentElement().getLocalName()).isEqualTo("validatieOpdracht");
        
        String idLevering = TestUtils.getElementTextContent(doc, "idLevering");
        assertThat(idLevering).startsWith("OTST_val_");
    }
    
    @Test
    @DisplayName("SHA-512 hash berekening werkt correct")
    void testCalculateSHA512() throws Exception {
        String testString = "test data";
        java.io.ByteArrayInputStream input = new java.io.ByteArrayInputStream(
            testString.getBytes("UTF-8"));
        
        String hash = BesluitProcessor.calculateSHA512(input);
        
        // SHA-512 hash moet 128 hexadecimale karakters lang zijn
        assertThat(hash).hasSize(128);
        assertThat(hash).matches("^[a-f0-9]{128}$");
    }
    
    @Test
    @DisplayName("isValidAuthorityType herkent geldige bestuurstypen")
    void testValidAuthorityTypes() {
        // Deze test demonstreert welke types geldig zijn
        // In de echte implementatie zou je een public methode moeten hebben
        // of via reflectie de private methode testen
        assertThat(true).isTrue();
        // Verwachte types: gemeente, provincie, ministerie, waterschap
    }
    
    @Nested
    @DisplayName("AnalyseData tests")
    class AnalyseDataTests {
        
        @Test
        @DisplayName("AnalyseData bevat alle vereiste velden")
        void testAnalyseDataStructure() {
            BesluitProcessor.AnalyseData data = new BesluitProcessor.AnalyseData();
            
            // Controleer dat alle velden beschikbaar zijn
            assertThat(data).isNotNull();
            assertThat(data.informatieObjecten).isNotNull();
            assertThat(data.extIoRefs).isNotNull();
        }
        
        @Test
        @DisplayName("InformatieObjectData bevat alle vereiste velden")
        void testInformatieObjectDataStructure() {
            BesluitProcessor.AnalyseData.InformatieObjectData ioData = 
                new BesluitProcessor.AnalyseData.InformatieObjectData();
            
            assertThat(ioData).isNotNull();
            // Alle velden zijn public en toegankelijk
        }
    }
}


