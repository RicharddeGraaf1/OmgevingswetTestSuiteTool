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

    public static byte[] createIOXml(BesluitProcessor.AnalyseData.InformatieObjectData ioData, ZipFile zipFile, String regelingFrbrWork) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        
        Document doc = builder.newDocument();
        Element root = doc.createElement("AanleveringInformatieObject");
        root.setAttribute("xmlns", "https://standaarden.overheid.nl/lvbb/stop/aanlevering/");
        root.setAttribute("xmlns:geo", "https://standaarden.overheid.nl/stop/imop/geo/");
        root.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
        root.setAttribute("schemaversie", "1.2.0");
        root.setAttribute("xsi:schemaLocation", "https://standaarden.overheid.nl/lvbb/stop/aanlevering https://standaarden.overheid.nl/lvbb/1.2.0/lvbb-stop-aanlevering.xsd");
        doc.appendChild(root);
        
        Element versie = doc.createElement("InformatieObjectVersie");
        root.appendChild(versie);
        
        // ExpressionIdentificatie
        Element expressionId = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/data/", "ExpressionIdentificatie");
        expressionId.setPrefix("data");
        versie.appendChild(expressionId);
        
        Element frbWork = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/data/", "FRBRWork");
        frbWork.setPrefix("data");
        frbWork.setTextContent(ioData.frbrWork);
        expressionId.appendChild(frbWork);
        
        Element frbExpr = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/data/", "FRBRExpression");
        frbExpr.setPrefix("data");
        frbExpr.setTextContent(ioData.frbrExpression);
        expressionId.appendChild(frbExpr);
        
        Element soortWork = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/data/", "soortWork");
        soortWork.setPrefix("data");
        soortWork.setTextContent("/join/id/stop/work_010");
        expressionId.appendChild(soortWork);
        
        // InformatieObjectVersieMetadata
        Element versieMetadata = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/data/", "InformatieObjectVersieMetadata");
        versieMetadata.setPrefix("data");
        versie.appendChild(versieMetadata);
        
        // Voeg heeftGeboorteregeling toe
        Element heeftGeboorteregeling = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/data/", "heeftGeboorteregeling");
        heeftGeboorteregeling.setPrefix("data");
        heeftGeboorteregeling.setTextContent(regelingFrbrWork);
        versieMetadata.appendChild(heeftGeboorteregeling);
        
        Element heeftBestanden = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/data/", "heeftBestanden");
        heeftBestanden.setPrefix("data");
        versieMetadata.appendChild(heeftBestanden);
        
        Element heeftBestand = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/data/", "heeftBestand");
        heeftBestand.setPrefix("data");
        heeftBestanden.appendChild(heeftBestand);
        
        Element bestand = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/data/", "Bestand");
        bestand.setPrefix("data");
        heeftBestand.appendChild(bestand);
        
        Element bestandsnaam = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/data/", "bestandsnaam");
        bestandsnaam.setPrefix("data");
        bestandsnaam.setTextContent(ioData.bestandsnaam);
        bestand.appendChild(bestandsnaam);
        
        // Als het een GML bestand is, bereken de hash over de gewikkelde versie
        String hash = ioData.bestandHash;
        if (ioData.bestandsnaam != null && ioData.bestandsnaam.toLowerCase().endsWith(".gml")) {
            // Lees het originele GML bestand
            ZipEntry gmlEntry = zipFile.getEntry(ioData.folder + "/" + ioData.bestandsnaam);
            if (gmlEntry != null) {
                byte[] gmlContent = zipFile.getInputStream(gmlEntry).readAllBytes();
                // Wrap de GML inhoud
                byte[] wrappedGml = wrapGmlContent(gmlContent);
                // Bereken de hash over de gewikkelde versie
                try (ByteArrayInputStream bis = new ByteArrayInputStream(wrappedGml)) {
                    hash = BesluitProcessor.calculateSHA512(bis);
                }
            }
        }
        
        Element hashElement = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/data/", "hash");
        hashElement.setPrefix("data");
        hashElement.setTextContent(hash);
        bestand.appendChild(hashElement);

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

    public static byte[] wrapGmlContent(byte[] gmlContent) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        
        Document doc = builder.newDocument();
        
        // Root element met alle benodigde namespaces
        Element root = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/geo/", "GeoInformatieObjectVaststelling");
        root.setPrefix("geo");
        root.setAttribute("xmlns:geo", "https://standaarden.overheid.nl/stop/imop/geo/");
        root.setAttribute("xmlns:basisgeo", "http://www.geostandaarden.nl/basisgeometrie/1.0");
        root.setAttribute("xmlns:gio", "https://standaarden.overheid.nl/stop/imop/gio/");
        root.setAttribute("xmlns:gml", "http://www.opengis.net/gml/3.2");
        root.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
        root.setAttribute("xsi:schemaLocation", "https://standaarden.overheid.nl/stop/imop/geo/ https://standaarden.overheid.nl/stop/1.3.0/imop-geo.xsd");
        root.setAttribute("schemaversie", "1.3.0");
        doc.appendChild(root);
        
        // Context element
        Element context = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/geo/", "context");
        context.setPrefix("geo");
        root.appendChild(context);
        
        // GeografischeContext element
        Element geoContext = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/gio/", "GeografischeContext");
        geoContext.setPrefix("gio");
        context.appendChild(geoContext);
        
        // achtergrondVerwijzing element
        Element achtergrondVerwijzing = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/gio/", "achtergrondVerwijzing");
        achtergrondVerwijzing.setPrefix("gio");
        achtergrondVerwijzing.setTextContent("cbs");
        geoContext.appendChild(achtergrondVerwijzing);
        
        // achtergrondActualiteit element met huidige datum
        Element achtergrondActualiteit = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/gio/", "achtergrondActualiteit");
        achtergrondActualiteit.setPrefix("gio");
        java.time.LocalDate today = java.time.LocalDate.now();
        achtergrondActualiteit.setTextContent(today.toString());
        geoContext.appendChild(achtergrondActualiteit);
        
        // vastgesteldeVersie element
        Element vastgesteldeVersie = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/geo/", "vastgesteldeVersie");
        vastgesteldeVersie.setPrefix("geo");
        root.appendChild(vastgesteldeVersie);
        
        // Parse en importeer de GML inhoud
        Document gmlDoc = builder.parse(new ByteArrayInputStream(gmlContent));
        Node gmlNode = gmlDoc.getDocumentElement();
        Node importedGml = doc.importNode(gmlNode, true);
        vastgesteldeVersie.appendChild(importedGml);
        
        // Converteer naar bytes
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
        
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(doc), new StreamResult(output));
        
        // Verwijder overbodige whitespace en linebreaks
        String result = output.toString("UTF-8")
            .replaceAll(">[\\s\\r\\n]+<", ">\n<")  // Vervang meerdere whitespace/linebreaks tussen tags door één newline
            .replaceAll("(?m)^[ \t]*\r?\n", "")    // Verwijder lege regels
            .replaceAll("\\s+/>", "/>")            // Verwijder whitespace voor zelf-sluitende tags
            .replaceAll("\\n\\s*\\n", "\n")        // Verwijder dubbele newlines
            .trim();                                // Verwijder leading/trailing whitespace
        
        return result.getBytes("UTF-8");
    }
} 