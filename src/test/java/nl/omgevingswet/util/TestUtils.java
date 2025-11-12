package nl.omgevingswet.util;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

/**
 * Utility klasse voor het werken met test data
 */
public class TestUtils {
    
    /**
     * Laadt een test ZIP bestand uit de resources directory
     */
    public static ZipFile loadTestZipFile(String resourcePath) throws IOException {
        InputStream is = TestUtils.class.getClassLoader().getResourceAsStream(resourcePath);
        if (is == null) {
            throw new FileNotFoundException("Test resource niet gevonden: " + resourcePath);
        }
        
        // Maak een tijdelijk bestand
        Path tempFile = Files.createTempFile("test-zip-", ".zip");
        tempFile.toFile().deleteOnExit();
        
        // Kopieer de inhoud naar het tijdelijke bestand
        Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
        
        return new ZipFile(tempFile.toFile());
    }
    
    /**
     * Parse een XML string naar een Document
     */
    public static Document parseXmlString(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));
    }
    
    /**
     * Parse een byte array naar een Document
     */
    public static Document parseXmlBytes(byte[] xmlBytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xmlBytes));
    }
    
    /**
     * Haalt de tekst waarde van een element op uit een Document
     */
    public static String getElementTextContent(Document doc, String tagName) {
        NodeList nodeList = doc.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent();
        }
        return null;
    }
    
    /**
     * Haalt de tekst waarde van een element op met namespace
     */
    public static String getElementTextContentNS(Document doc, String namespace, String tagName) {
        NodeList nodeList = doc.getElementsByTagNameNS(namespace, tagName);
        if (nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent();
        }
        return null;
    }
    
    /**
     * Controleert of een ZIP bestand een specifiek entry bevat
     */
    public static boolean zipContainsFile(ZipFile zipFile, String fileName) {
        return zipFile.getEntry(fileName) != null;
    }
    
    /**
     * Vergelijkt twee XML documenten op structuur (ignoreert formatting)
     */
    public static boolean compareXmlStructure(Document doc1, Document doc2) {
        return normalizeXml(doc1).equals(normalizeXml(doc2));
    }
    
    private static String normalizeXml(Document doc) {
        try {
            javax.xml.transform.TransformerFactory tf = javax.xml.transform.TransformerFactory.newInstance();
            javax.xml.transform.Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "no");
            
            java.io.StringWriter writer = new java.io.StringWriter();
            transformer.transform(new javax.xml.transform.dom.DOMSource(doc), 
                                new javax.xml.transform.stream.StreamResult(writer));
            return writer.toString().replaceAll("\\s+", " ").trim();
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * Laadt een bestand uit een ZIP entry
     */
    public static byte[] readZipEntry(ZipFile zipFile, String entryName) throws IOException {
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null) {
            throw new FileNotFoundException("Entry niet gevonden in ZIP: " + entryName);
        }
        
        try (InputStream is = zipFile.getInputStream(entry);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            return baos.toByteArray();
        }
    }
}


