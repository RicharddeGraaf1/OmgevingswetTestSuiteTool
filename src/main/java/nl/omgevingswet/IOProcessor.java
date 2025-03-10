package nl.omgevingswet;

import java.util.*;
import java.util.zip.*;
import java.io.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import org.w3c.dom.*;
import nl.omgevingswet.BesluitProcessor.AnalyseData;

public class IOProcessor {
    
    public static class IOContent {
        public String ioNumber;
        public List<String> xmlContents;
        
        public IOContent(String number) {
            this.ioNumber = number;
            this.xmlContents = new ArrayList<>();
        }
    }
    
    public static Map<String, IOContent> processIOFolders(ZipFile zipFile) throws IOException {
        Map<String, IOContent> ioContents = new HashMap<>();
        
        // Verzamel alle entries die in IO-mappen zitten
        zipFile.stream()
               .filter(entry -> entry.getName().matches("IO-\\d+/.*\\.xml"))
               .forEach(entry -> {
                   try {
                       // Haal het IO-nummer uit het pad
                       String path = entry.getName();
                       String ioNumber = path.substring(3, path.indexOf('/', 3));
                       
                       // Maak een nieuwe IOContent als deze nog niet bestaat
                       IOContent content = ioContents.computeIfAbsent(
                           ioNumber,
                           IOContent::new
                       );
                       
                       // Lees de XML inhoud
                       String xmlContent = new String(zipFile.getInputStream(entry).readAllBytes());
                       content.xmlContents.add(xmlContent);
                       
                   } catch (IOException e) {
                       e.printStackTrace();
                   }
               });
        
        return ioContents;
    }
    
    public static byte[] createCombinedXML(IOContent content) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        
        // Maak het root document
        Document doc = builder.newDocument();
        Element root = doc.createElement("AanleveringInformatieObject");
        doc.appendChild(root);
        
        Element versie = doc.createElement("InformatieObjectVersie");
        root.appendChild(versie);
        
        // Voeg alle XML inhoud toe
        for (String xmlContent : content.xmlContents) {
            // Parse de XML string naar een Document
            Document sourceDoc = builder.parse(new ByteArrayInputStream(xmlContent.getBytes()));
            
            // Importeer en voeg toe aan het hoofddocument
            Node importedNode = doc.importNode(sourceDoc.getDocumentElement(), true);
            versie.appendChild(importedNode);
        }
        
        // Converteer naar bytes
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        
        // Configureer de output om dubbele linebreaks te voorkomen
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
        
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(doc), new StreamResult(output));
        
        // Verwijder overbodige whitespace en linebreaks
        String result = output.toString("UTF-8")
            .replaceAll(">[\\s\\r\\n]+<", ">\n<")  // Vervang meerdere whitespace/linebreaks tussen tags door één enkele newline
            .replaceAll("(?m)^[ \t]*\r?\n", "")    // Verwijder lege regels
            .trim();                                // Verwijder leading/trailing whitespace
        
        return result.getBytes("UTF-8");
    }

    public static byte[] createIOXml(BesluitProcessor.AnalyseData.InformatieObjectData ioData, ZipFile zipFile) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        
        Document doc = builder.newDocument();
        Element root = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/aanlever/", "AanleveringInformatieObject");
        doc.appendChild(root);
        
        Element versie = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/data/", "InformatieObjectVersie");
        root.appendChild(versie);
        
        // ExpressionIdentificatie
        Element expressionId = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/data/", "ExpressionIdentificatie");
        versie.appendChild(expressionId);
        
        Element frbWork = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/data/", "FRBRWork");
        frbWork.setTextContent(ioData.frbrWork);
        expressionId.appendChild(frbWork);
        
        Element frbExpr = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/data/", "FRBRExpression");
        frbExpr.setTextContent(ioData.frbrExpression);
        expressionId.appendChild(frbExpr);
        
        // InformatieObjectVersieMetadata
        Element versieMetadata = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/data/", "InformatieObjectVersieMetadata");
        versie.appendChild(versieMetadata);
        
        Element heeftBestanden = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/data/", "heeftBestanden");
        versieMetadata.appendChild(heeftBestanden);
        
        Element heeftBestand = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/data/", "heeftBestand");
        heeftBestanden.appendChild(heeftBestand);
        
        Element bestand = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/data/", "Bestand");
        heeftBestand.appendChild(bestand);
        
        Element bestandsnaam = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/data/", "bestandsnaam");
        bestandsnaam.setTextContent(ioData.bestandsnaam);
        bestand.appendChild(bestandsnaam);
        
        Element hash = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/data/", "hash");
        hash.setTextContent(ioData.bestandHash);
        bestand.appendChild(hash);

        // Parse en importeer de volledige InformatieObjectMetadata uit het originele Metadata.xml
        ZipEntry metadataEntry = zipFile.getEntry(ioData.folder + "/Metadata.xml");
        if (metadataEntry != null) {
            Document metadataDoc = builder.parse(zipFile.getInputStream(metadataEntry));
            Node metadataNode = metadataDoc.getDocumentElement();
            Node importedMetadata = doc.importNode(metadataNode, true);
            versie.appendChild(importedMetadata);
        }
        
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        
        // Configureer de transformer voor nette output
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
        
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(doc), new StreamResult(output));
        
        // Verwijder overbodige whitespace en linebreaks
        String result = output.toString("UTF-8")
            .replaceAll(">[\\s\\r\\n]+<", ">\n  <")  // Vervang meerdere whitespace/linebreaks tussen tags door één newline met indent
            .replaceAll("(?m)^[\\s\\r\\n]*$", "")    // Verwijder lege regels
            .replaceAll("\\s+/>", "/>")              // Verwijder whitespace voor zelf-sluitende tags
            .replaceAll("\\n\\s*\\n", "\n")          // Verwijder dubbele newlines
            .trim();                                  // Verwijder leading/trailing whitespace
        
        return result.getBytes("UTF-8");
    }
} 