package nl.omgevingswet;

import java.util.*;
import java.util.zip.*;
import java.io.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import org.w3c.dom.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.security.MessageDigest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.util.Comparator;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipOutputStream;

public class BesluitProcessor {
    
    private static final String[] REGELING_FILES = {
        "Regeling/Identificatie.xml",
        "Regeling/VersieMetadata.xml",
        "Regeling/Metadata.xml",
        "Regeling/MomentOpname.xml"
    };

    private static final String STOP_DATA_NS = "https://standaarden.overheid.nl/stop/imop/data/";
    private static final String STOP_TEKST_NS = "https://standaarden.overheid.nl/stop/imop/tekst/";
    private static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";
    private static final String XLINK_NS = "http://www.w3.org/1999/xlink";
    
    public static class AnalyseData {
        public String frbrWork;
        public String frbrExpression;
        public String doel;
        public String bevoegdGezag;
        public int aantalInformatieObjecten;
        public long totaleGmlBestandsgrootte;
        public List<InformatieObjectData> informatieObjecten = new ArrayList<>();
        public List<ExtIoRefData> extIoRefs = new ArrayList<>();
        
        public static class InformatieObjectData {
            public String folder;
            public String frbrWork;
            public String frbrExpression;
            public String extIoRefEId;
            public String bestandsnaam;
            public String bestandHash;
            public String officieleTitel;
        }
        
        public static class ExtIoRefData {
            public String ref;
            public String eId;
        }
    }
    
    public static AnalyseData analyseZip(ZipFile zipFile) throws Exception {
        AnalyseData data = new AnalyseData();
        
        // Debug logging
        System.out.println("Start analyseZip methode");
        
        // Haal FRBRWork en FRBRExpression op uit Regeling/Identificatie.xml
        ZipEntry identificatieEntry = zipFile.getEntry("Regeling/Identificatie.xml");
        if (identificatieEntry != null) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document doc = factory.newDocumentBuilder().parse(zipFile.getInputStream(identificatieEntry));
            
            // Zoek eerst met namespace
            NodeList workNodes = doc.getElementsByTagNameNS(STOP_DATA_NS, "FRBRWork");
            if (workNodes.getLength() > 0) {
                data.frbrWork = workNodes.item(0).getTextContent();
            } else {
                // Probeer zonder namespace
                workNodes = doc.getElementsByTagName("FRBRWork");
                if (workNodes.getLength() > 0) {
                    data.frbrWork = workNodes.item(0).getTextContent();
                }
            }
            
            // Zoek eerst met namespace
            NodeList expressionNodes = doc.getElementsByTagNameNS(STOP_DATA_NS, "FRBRExpression");
            if (expressionNodes.getLength() > 0) {
                data.frbrExpression = expressionNodes.item(0).getTextContent();
            } else {
                // Probeer zonder namespace
                expressionNodes = doc.getElementsByTagName("FRBRExpression");
                if (expressionNodes.getLength() > 0) {
                    data.frbrExpression = expressionNodes.item(0).getTextContent();
                }
            }
        }
        
        // Haal doel op uit Regeling/Momentopname.xml
        ZipEntry momentopnameEntry = zipFile.getEntry("Regeling/Momentopname.xml");
        if (momentopnameEntry != null) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document doc = factory.newDocumentBuilder().parse(zipFile.getInputStream(momentopnameEntry));
            
            // Zoek eerst met namespace
            NodeList doelNodes = doc.getElementsByTagNameNS(STOP_DATA_NS, "doel");
            if (doelNodes.getLength() > 0) {
                data.doel = doelNodes.item(0).getTextContent();
            } else {
                // Probeer zonder namespace
                doelNodes = doc.getElementsByTagName("doel");
                if (doelNodes.getLength() > 0) {
                    data.doel = doelNodes.item(0).getTextContent();
                }
            }
        }
        
        // Haal bevoegd gezag op uit Regeling/Metadata.xml
        ZipEntry metadataEntry = zipFile.getEntry("Regeling/Metadata.xml");
        if (metadataEntry != null) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(zipFile.getInputStream(metadataEntry));
            
            // Debug logging
            System.out.println("Zoeken naar bevoegd gezag in Metadata.xml");
            
            // Zoek eerst met namespace
            NodeList makerNodes = doc.getElementsByTagNameNS(STOP_DATA_NS, "maker");
            if (makerNodes.getLength() > 0) {
                System.out.println("Gevonden maker node met namespace");
                Node makerNode = makerNodes.item(0);
                String makerValue = makerNode.getTextContent();
                System.out.println("Maker value: " + makerValue);
                
                if (makerValue != null && !makerValue.isEmpty()) {
                    String[] parts = makerValue.split("/");
                    if (parts.length >= 4) {
                        String type = parts[parts.length - 2];
                        String code = parts[parts.length - 1];
                        if (isValidAuthorityType(type)) {
                            data.bevoegdGezag = code;
                            System.out.println("Extracted bevoegd gezag code: " + data.bevoegdGezag);
                        }
                    }
                }
            } else {
                // Probeer zonder namespace
                makerNodes = doc.getElementsByTagName("maker");
                if (makerNodes.getLength() > 0) {
                    Node makerNode = makerNodes.item(0);
                    String makerValue = makerNode.getTextContent();
                    System.out.println("Maker value (no namespace): " + makerValue);
                    
                    if (makerValue != null && !makerValue.isEmpty()) {
                        String[] parts = makerValue.split("/");
                        if (parts.length >= 4) {
                            String type = parts[parts.length - 2];
                            String code = parts[parts.length - 1];
                            if (isValidAuthorityType(type)) {
                                data.bevoegdGezag = code;
                                System.out.println("Extracted bevoegd gezag code: " + data.bevoegdGezag);
                            }
                        }
                    }
                }
            }
        }
        
        // Verzamel informatie over informatieobjecten
        Set<String> ioFolders = new HashSet<>();
        long totaleGmlBestandsgrootte = 0;
        
        // Debug logging
        System.out.println("Start zoeken naar informatieobjecten");
        
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            
            // Debug logging voor elke entry
            System.out.println("Checking entry: " + name);
            
            // Check of het een informatieobject map is
            if (name.startsWith("IO-")) {
                // Haal de mapnaam op (alles tot de eerste /)
                int slashIndex = name.indexOf("/");
                if (slashIndex > 0) {
                    String ioFolder = name.substring(0, slashIndex);
                    ioFolders.add(ioFolder);
                    System.out.println("Gevonden informatieobject map: " + ioFolder);
                    
                    // Tel GML bestanden en hun grootte
                    if (name.endsWith(".gml")) {
                        totaleGmlBestandsgrootte += entry.getSize();
                        System.out.println("Gevonden GML bestand: " + name + " (grootte: " + entry.getSize() + " bytes)");
                    }
                }
            }
        }
        
        // Debug logging
        System.out.println("Aantal gevonden informatieobject mappen: " + ioFolders.size());
        System.out.println("Totale GML bestandsgrootte: " + totaleGmlBestandsgrootte);
        
        data.aantalInformatieObjecten = ioFolders.size();
        data.totaleGmlBestandsgrootte = totaleGmlBestandsgrootte;
        
        // Verzamel informatie voor elk informatieobject
        for (String ioFolder : ioFolders) {
            System.out.println("Verwerken van informatieobject: " + ioFolder);
            AnalyseData.InformatieObjectData ioData = new AnalyseData.InformatieObjectData();
            ioData.folder = ioFolder;
            
            // Haal FRBRWork en FRBRExpression op uit IO/Identificatie.xml
            ZipEntry ioIdentificatieEntry = zipFile.getEntry(ioFolder + "/Identificatie.xml");
            if (ioIdentificatieEntry != null) {
                System.out.println("Gevonden Identificatie.xml voor " + ioFolder);
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                Document doc = factory.newDocumentBuilder().parse(zipFile.getInputStream(ioIdentificatieEntry));
                
                // Zoek eerst met namespace
                NodeList workNodes = doc.getElementsByTagNameNS(STOP_DATA_NS, "FRBRWork");
                if (workNodes.getLength() > 0) {
                    ioData.frbrWork = workNodes.item(0).getTextContent();
                    System.out.println("Gevonden FRBRWork met namespace: " + ioData.frbrWork);
                } else {
                    // Probeer zonder namespace
                    workNodes = doc.getElementsByTagName("FRBRWork");
                    if (workNodes.getLength() > 0) {
                        ioData.frbrWork = workNodes.item(0).getTextContent();
                        System.out.println("Gevonden FRBRWork zonder namespace: " + ioData.frbrWork);
                    }
                }
                
                // Zoek eerst met namespace
                NodeList expressionNodes = doc.getElementsByTagNameNS(STOP_DATA_NS, "FRBRExpression");
                if (expressionNodes.getLength() > 0) {
                    ioData.frbrExpression = expressionNodes.item(0).getTextContent();
                    System.out.println("Gevonden FRBRExpression met namespace: " + ioData.frbrExpression);
                } else {
                    // Probeer zonder namespace
                    expressionNodes = doc.getElementsByTagName("FRBRExpression");
                    if (expressionNodes.getLength() > 0) {
                        ioData.frbrExpression = expressionNodes.item(0).getTextContent();
                        System.out.println("Gevonden FRBRExpression zonder namespace: " + ioData.frbrExpression);
                    }
                }
            } else {
                System.out.println("Geen Identificatie.xml gevonden voor " + ioFolder);
            }
            
            // Haal officiële titel op uit IO/VersieMetadata.xml
            ZipEntry versieMetadataEntry = zipFile.getEntry(ioFolder + "/VersieMetadata.xml");
            if (versieMetadataEntry != null) {
                System.out.println("Gevonden VersieMetadata.xml voor " + ioFolder);
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                Document doc = factory.newDocumentBuilder().parse(zipFile.getInputStream(versieMetadataEntry));
                
                // Zoek eerst met namespace
                NodeList titelNodes = doc.getElementsByTagNameNS(STOP_DATA_NS, "officieleTitel");
                if (titelNodes.getLength() > 0) {
                    ioData.officieleTitel = titelNodes.item(0).getTextContent();
                    System.out.println("Gevonden officieleTitel met namespace: " + ioData.officieleTitel);
                } else {
                    // Probeer zonder namespace
                    titelNodes = doc.getElementsByTagName("officieleTitel");
                    if (titelNodes.getLength() > 0) {
                        ioData.officieleTitel = titelNodes.item(0).getTextContent();
                        System.out.println("Gevonden officieleTitel zonder namespace: " + ioData.officieleTitel);
                    }
                }
            } else {
                System.out.println("Geen VersieMetadata.xml gevonden voor " + ioFolder);
            }
            
            // Haal ExtIoRef-eId op uit Regeling/Tekst.xml
            ZipEntry tekstEntry = zipFile.getEntry("Regeling/Tekst.xml");
            if (tekstEntry != null) {
                System.out.println("Gevonden Tekst.xml, zoeken naar ExtIoRef voor " + ioFolder);
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                Document doc = factory.newDocumentBuilder().parse(zipFile.getInputStream(tekstEntry));
                
                // Zoek naar ExtIoRef met matching ref of eId
                NodeList extIoRefNodes = doc.getElementsByTagNameNS(STOP_TEKST_NS, "ExtIoRef");
                System.out.println("Aantal gevonden ExtIoRef nodes: " + extIoRefNodes.getLength());
                
                for (int i = 0; i < extIoRefNodes.getLength(); i++) {
                    Node extIoRefNode = extIoRefNodes.item(i);
                    if (extIoRefNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element extIoRefElement = (Element) extIoRefNode;
                        String ref = extIoRefElement.getAttribute("ref");
                        String eId = extIoRefElement.getAttribute("eId");
                        System.out.println("Checking ExtIoRef - ref: " + ref + ", eId: " + eId);
                        
                        // Check of de ref overeenkomt met de FRBRExpression van het informatieobject
                        if (ref.equals(ioData.frbrExpression)) {
                            ioData.extIoRefEId = eId;
                            System.out.println("Gevonden ExtIoRef-eId via FRBRExpression match: " + ioData.extIoRefEId);
                            break;
                        }
                    }
                }
                
                // Als we nog steeds geen eId hebben gevonden, probeer dan te zoeken op de ref die overeenkomt met de FRBRWork
                if (ioData.extIoRefEId == null) {
                    System.out.println("Geen FRBRExpression match gevonden, zoeken op FRBRWork");
                    for (int i = 0; i < extIoRefNodes.getLength(); i++) {
                        Node extIoRefNode = extIoRefNodes.item(i);
                        if (extIoRefNode.getNodeType() == Node.ELEMENT_NODE) {
                            Element extIoRefElement = (Element) extIoRefNode;
                            String ref = extIoRefElement.getAttribute("ref");
                            System.out.println("Checking ExtIoRef ref: " + ref);
                            if (ref.equals(ioData.frbrWork)) {
                                ioData.extIoRefEId = extIoRefElement.getAttribute("eId");
                                System.out.println("Gevonden ExtIoRef-eId via FRBRWork match: " + ioData.extIoRefEId);
                                break;
                            }
                        }
                    }
                }
            } else {
                System.out.println("Geen Tekst.xml gevonden voor " + ioFolder);
            }
            
            // Zoek naar bestanden in de IO map
            entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith(ioFolder + "/")) {
                    String fileName = name.substring(name.lastIndexOf("/") + 1);
                    if (fileName.endsWith(".gml")) {
                        ioData.bestandsnaam = fileName;
                        System.out.println("Gevonden GML bestand voor " + ioFolder + ": " + ioData.bestandsnaam);
                        // Bereken hash voor GML (wordt later opnieuw berekend over gewikkelde versie)
                        try (InputStream is = zipFile.getInputStream(entry)) {
                            ioData.bestandHash = calculateSHA512(is);
                        }
                        break;
                    } else if (fileName.endsWith(".pdf")) {
                        ioData.bestandsnaam = fileName;
                        System.out.println("Gevonden PDF bestand voor " + ioFolder + ": " + ioData.bestandsnaam);
                        // Bereken hash voor PDF
                        try (InputStream is = zipFile.getInputStream(entry)) {
                            ioData.bestandHash = calculateSHA512(is);
                        }
                        break;
                    }
                }
            }
            
            data.informatieObjecten.add(ioData);
            System.out.println("Informatieobject toegevoegd aan analyse data: " + ioFolder);
        }
        
        // Debug logging
        System.out.println("Einde analyseZip methode");
        System.out.println("Totaal aantal informatieobjecten: " + data.informatieObjecten.size());
        
        return data;
    }
    
    private static String getDoelFromMomentOpname(ZipFile zipFile, String folder) throws Exception {
        String momentOpnamePath = folder + "/Momentopname.xml";
        ZipEntry entry = zipFile.getEntry(momentOpnamePath);
        if (entry != null) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(zipFile.getInputStream(entry));
            
            // Print de XML structuur voor debug
            System.out.println("Debug - XML structuur van " + momentOpnamePath + ":");
            printNodeStructure(doc.getDocumentElement(), "");
            
            // Zoek het doel element met de juiste namespace
            NodeList doelNodes = doc.getElementsByTagNameNS(STOP_DATA_NS, "doel");
            if (doelNodes.getLength() > 0) {
                String doel = doelNodes.item(0).getTextContent();
                System.out.println("Gevonden doel met namespace: " + doel); // Debug output
                return doel;
            }
            
            // Als we hier komen, probeer dan met de prefix
            NodeList doelNodesWithPrefix = doc.getElementsByTagName("data:doel");
            if (doelNodesWithPrefix.getLength() > 0) {
                String doel = doelNodesWithPrefix.item(0).getTextContent();
                System.out.println("Gevonden doel met prefix: " + doel); // Debug output
                return doel;
            }
            
            // Als we nog steeds niets hebben gevonden, probeer direct in de root
            Element root = doc.getDocumentElement();
            NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) child;
                    if (element.getLocalName().equals("doel")) {
                        String doel = element.getTextContent();
                        System.out.println("Gevonden doel in root: " + doel); // Debug output
                        return doel;
                    }
                }
            }
        }
        return null;
    }
    
    private static void printNodeStructure(Node node, String indent) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            System.out.println(indent + "Element: " + node.getNodeName());
            NodeList children = node.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                printNodeStructure(children.item(i), indent + "  ");
            }
        }
    }
    
    private static String getFRBRExpressionFromIdentificatie(ZipFile zipFile, String folder) throws Exception {
        String identificatiePath = folder + "/Identificatie.xml";
        ZipEntry identificatieEntry = zipFile.getEntry(identificatiePath);
        if (identificatieEntry == null) {
            throw new Exception("Identificatie.xml niet gevonden in " + folder);
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(zipFile.getInputStream(identificatieEntry));

        // Zoek naar FRBRExpression in de XML
        NodeList frbrNodes = doc.getElementsByTagName("FRBRExpression");
        if (frbrNodes.getLength() > 0) {
            return frbrNodes.item(0).getTextContent();
        }

        return null;
    }
    
    public static String calculateSHA512(InputStream input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-512");
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        byte[] hash = digest.digest();
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public static class BesluitResult {
        public final byte[] besluitXml;
        public final byte[] opdrachtXml;
        
        public BesluitResult(byte[] besluitXml, byte[] opdrachtXml) {
            this.besluitXml = besluitXml;
            this.opdrachtXml = opdrachtXml;
        }
    }

    public static BesluitResult createBesluitXml(ZipFile zipFile) throws Exception {
        return createBesluitXml(zipFile, false);
    }

    public static BesluitResult createBesluitXml(ZipFile zipFile, boolean isValidation) throws Exception {
        try {
            System.out.println("Debug: Start createBesluitXml");
            
            // Haal eerst alle analyse data op
            AnalyseData data = analyseZip(zipFile);
        
        // Genereer datum/tijd onderdelen
        LocalDateTime now = LocalDateTime.now();
        String huidigJaartal = String.valueOf(now.getYear());
        String datumTijd = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String datum = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        
            // Bereken datum van morgen voor bekendmaking
            LocalDateTime tomorrow = now.plusDays(1);
            
            System.out.println("Debug: Creating opdracht.xml with bevoegdGezag=" + data.bevoegdGezag);
            
            // Maak opdracht.xml aan
            byte[] opdrachtXml = createOpdrachtXml(data.bevoegdGezag, datumTijd, tomorrow, isValidation);
            
            System.out.println("Debug: Generated opdracht.xml content, size=" + opdrachtXml.length);
            
            // Genereer besluit.xml inhoud
            byte[] besluitXml = generateBesluitXml(data, zipFile, huidigJaartal, datumTijd, datum, tomorrow);
            
            return new BesluitResult(besluitXml, opdrachtXml);
        } catch (Exception e) {
            throw e;
        }
    }

    private static byte[] generateBesluitXml(AnalyseData data, ZipFile zipFile, 
            String huidigJaartal, String datumTijd, String datum, LocalDateTime tomorrow) throws Exception {
        // Maak het root document
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        
        Document doc = builder.newDocument();
        // Maak root element met namespace
        Element root = doc.createElement("AanleveringBesluit");
        root.setAttribute("xmlns", "https://standaarden.overheid.nl/lvbb/stop/aanlevering/");
        root.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
        root.setAttribute("schemaversie", "1.2.0");
        root.setAttribute("xsi:schemaLocation", "https://standaarden.overheid.nl/lvbb/stop/aanlevering https://standaarden.overheid.nl/lvbb/1.2.0/lvbb-stop-aanlevering.xsd");
        doc.appendChild(root);
        
        // Voeg BesluitVersie toe (geen namespace nodig, erft over van root)
        Element besluitVersie = doc.createElement("BesluitVersie");
        root.appendChild(besluitVersie);
        
        // 1. ExpressionIdentificatie (expliciete namespace nodig)
        Element expressionId = doc.createElementNS(STOP_DATA_NS, "ExpressionIdentificatie");
        expressionId.setPrefix("data");
        besluitVersie.appendChild(expressionId);
        
        Element frbWork = doc.createElementNS(STOP_DATA_NS, "FRBRWork");
        frbWork.setPrefix("data");
        frbWork.setTextContent(String.format("/akn/nl/bill/%s/%s/OTSTgegenereerd%s", 
            data.bevoegdGezag, huidigJaartal, datumTijd));
        expressionId.appendChild(frbWork);
        
        Element frbExpr = doc.createElementNS(STOP_DATA_NS, "FRBRExpression");
        frbExpr.setPrefix("data");
        frbExpr.setTextContent(String.format("/akn/nl/bill/%s/%s/OTSTgegenereerd%s/nld@%s;1", 
            data.bevoegdGezag, huidigJaartal, datumTijd, datum));
        expressionId.appendChild(frbExpr);
        
        Element soortWork = doc.createElementNS(STOP_DATA_NS, "soortWork");
        soortWork.setPrefix("data");
        soortWork.setTextContent("/join/id/stop/work_003");
        expressionId.appendChild(soortWork);
        
        // 2. BesluitMetadata (expliciete namespace nodig)
        Element besluitMetadata = doc.createElementNS(STOP_DATA_NS, "BesluitMetadata");
        besluitMetadata.setPrefix("data");
        besluitVersie.appendChild(besluitMetadata);
        
        // Haal metadata op uit Metadata.xml
        ZipEntry metadataEntry = zipFile.getEntry("Regeling/Metadata.xml");
        if (metadataEntry != null) {
            Document metadataDoc = builder.parse(zipFile.getInputStream(metadataEntry));
            
            // Kopieer en sorteer alle elementen van RegelingMetadata
            List<Element> metadataElements = new ArrayList<>();
            NodeList children = metadataDoc.getDocumentElement().getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) child;
                    // Voor BesluitMetadata, sla soortRegeling en overheidsdomeinen over
                    if (!element.getLocalName().equals("soortRegeling") && 
                        !element.getLocalName().equals("overheidsdomeinen")) {
                        if (element.getLocalName().equals("onderwerpen") || 
                            element.getLocalName().equals("rechtsgebieden")) {
                            // Maak nieuwe elementen met namespace prefix
                            Element parentElement = doc.createElementNS(STOP_DATA_NS, element.getLocalName());
                            parentElement.setPrefix("data");
                            NodeList subElements = element.getChildNodes();
                            for (int j = 0; j < subElements.getLength(); j++) {
                                Node subNode = subElements.item(j);
                                if (subNode.getNodeType() == Node.ELEMENT_NODE) {
                                    Element subElement = (Element) subNode;
                                    Element newSubElement = doc.createElementNS(STOP_DATA_NS, subElement.getLocalName());
                                    newSubElement.setPrefix("data");
                                    newSubElement.setTextContent(subElement.getTextContent().trim());
                                    parentElement.appendChild(newSubElement);
                                }
                            }
                            metadataElements.add(parentElement);
                        } else {
                            // Voor alle andere elementen, importeer de volledige structuur
                            Node importedNode = doc.importNode(element, true);
                            Element importedElement = (Element) importedNode;
                            // Zorg ervoor dat de namespace prefix correct is
                            if (importedElement.getNamespaceURI() != null) {
                                importedElement.setPrefix("data");
                            }
                            metadataElements.add(importedElement);
                        }
                    }
                }
            }
            
            // Voeg soortProcedure toe
            Element soortProcedure = doc.createElementNS(STOP_DATA_NS, "soortProcedure");
            soortProcedure.setPrefix("data");
            soortProcedure.setTextContent("/join/id/stop/proceduretype_definitief");
            metadataElements.add(soortProcedure);
            
            // Voeg informatieobjectRefs alleen toe als er IO's zijn
            if (!data.informatieObjecten.isEmpty()) {
                Element informatieobjectRefs = doc.createElementNS(STOP_DATA_NS, "informatieobjectRefs");
                informatieobjectRefs.setPrefix("data");
                
                // Voeg voor elk informatieobject een informatieobjectRef toe
                for (AnalyseData.InformatieObjectData io : data.informatieObjecten) {
                    Element informatieobjectRef = doc.createElementNS(STOP_DATA_NS, "informatieobjectRef");
                    informatieobjectRef.setPrefix("data");
                    informatieobjectRef.setTextContent(io.frbrExpression);
                    informatieobjectRefs.appendChild(informatieobjectRef);
                }
                metadataElements.add(informatieobjectRefs);
            }
            
            // Sorteer op elementnaam
            metadataElements.sort((a, b) -> a.getTagName().compareTo(b.getTagName()));
            
            // Voeg gesorteerde elementen toe aan BesluitMetadata
            for (Element element : metadataElements) {
                besluitMetadata.appendChild(element);
            }
        }
        
        // 3. Procedureverloop (expliciete namespace nodig)
        Element procedureverloop = doc.createElementNS(STOP_DATA_NS, "Procedureverloop");
        procedureverloop.setPrefix("data");
        besluitVersie.appendChild(procedureverloop);
        
        // Voeg bekendOp toe
        Element bekendOp = doc.createElementNS(STOP_DATA_NS, "bekendOp");
        bekendOp.setPrefix("data");
        bekendOp.setTextContent(tomorrow.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        procedureverloop.appendChild(bekendOp);
        
        // Voeg procedurestappen toe
        Element procedurestappen = doc.createElementNS(STOP_DATA_NS, "procedurestappen");
        procedurestappen.setPrefix("data");
        procedureverloop.appendChild(procedurestappen);
        
        // Voeg Procedurestap toe
        Element procedurestap = doc.createElementNS(STOP_DATA_NS, "Procedurestap");
        procedurestap.setPrefix("data");
        procedurestappen.appendChild(procedurestap);
        
        // Voeg soortStap toe
        Element soortStap = doc.createElementNS(STOP_DATA_NS, "soortStap");
        soortStap.setPrefix("data");
        soortStap.setTextContent("/join/id/stop/procedure/stap_003");
        procedurestap.appendChild(soortStap);
        
        // Voeg voltooidOp toe
        Element voltooidOp = doc.createElementNS(STOP_DATA_NS, "voltooidOp");
        voltooidOp.setPrefix("data");
        voltooidOp.setTextContent(tomorrow.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        procedurestap.appendChild(voltooidOp);
        
        // 4. ConsolidatieInformatie (expliciete namespace nodig)
        Element consolidatieInformatie = doc.createElementNS(STOP_DATA_NS, "ConsolidatieInformatie");
        consolidatieInformatie.setPrefix("data");
        besluitVersie.appendChild(consolidatieInformatie);
        
        // BeoogdeRegelgeving
        Element beoogdeRegelgeving = doc.createElementNS(STOP_DATA_NS, "BeoogdeRegelgeving");
        consolidatieInformatie.appendChild(beoogdeRegelgeving);
        
        // BeoogdeRegeling
        Element beoogdeRegeling = doc.createElementNS(STOP_DATA_NS, "BeoogdeRegeling");
        beoogdeRegelgeving.appendChild(beoogdeRegeling);
        
        // Doelen onder BeoogdeRegeling
        Element doelenRegeling = doc.createElementNS(STOP_DATA_NS, "doelen");
        beoogdeRegeling.appendChild(doelenRegeling);
        
        // Doel onder BeoogdeRegeling
        Element doelRegeling = doc.createElementNS(STOP_DATA_NS, "doel");
        doelRegeling.setTextContent(data.doel);
        doelenRegeling.appendChild(doelRegeling);
        
        // Instrumentversie onder BeoogdeRegeling
        Element instrumentversieRegeling = doc.createElementNS(STOP_DATA_NS, "instrumentVersie");
        instrumentversieRegeling.setTextContent(data.frbrExpression);
        beoogdeRegeling.appendChild(instrumentversieRegeling);
        
        // eId onder BeoogdeRegeling
        Element eIdRegeling = doc.createElementNS(STOP_DATA_NS, "eId");
        eIdRegeling.setTextContent("art_besluit1");
        beoogdeRegeling.appendChild(eIdRegeling);
        
        // BeoogdInformatieObject voor elke IO
        for (AnalyseData.InformatieObjectData io : data.informatieObjecten) {
            Element beoogdInformatieObject = doc.createElementNS(STOP_DATA_NS, "BeoogdInformatieobject");
            beoogdeRegelgeving.appendChild(beoogdInformatieObject);
            
            // Doelen onder BeoogdInformatieObject
            Element doelenIO = doc.createElementNS(STOP_DATA_NS, "doelen");
            beoogdInformatieObject.appendChild(doelenIO);
            
            Element doelIO = doc.createElementNS(STOP_DATA_NS, "doel");
            doelIO.setTextContent(data.doel);
            doelenIO.appendChild(doelIO);
            
            // Instrumentversie onder BeoogdInformatieObject
            Element instrumentversieIO = doc.createElementNS(STOP_DATA_NS, "instrumentVersie");
            instrumentversieIO.setTextContent(io.frbrExpression);
            beoogdInformatieObject.appendChild(instrumentversieIO);
            
            // eId als element onder BeoogdInformatieObject
            Element eIdIO = doc.createElementNS(STOP_DATA_NS, "eId");
            eIdIO.setTextContent("!main#" + io.extIoRefEId);
            beoogdInformatieObject.appendChild(eIdIO);
        }
        
        // Tijdstempels
        Element tijdstempels = doc.createElementNS(STOP_DATA_NS, "Tijdstempels");
        consolidatieInformatie.appendChild(tijdstempels);
        
        Element tijdstempel = doc.createElementNS(STOP_DATA_NS, "Tijdstempel");
        tijdstempels.appendChild(tijdstempel);
        
        // Doel onder Tijdstempel
        Element doelTijdstempel = doc.createElementNS(STOP_DATA_NS, "doel");
        doelTijdstempel.setTextContent(data.doel);
        tijdstempel.appendChild(doelTijdstempel);
        
        // SoortTijdstempel
        Element soortTijdstempel = doc.createElementNS(STOP_DATA_NS, "soortTijdstempel");
        soortTijdstempel.setTextContent("juridischWerkendVanaf");
        tijdstempel.appendChild(soortTijdstempel);
        
        // Datum
        Element datumTijdstempel = doc.createElementNS(STOP_DATA_NS, "datum");
        datumTijdstempel.setTextContent(tomorrow.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        tijdstempel.appendChild(datumTijdstempel);
        
        // eId onder Tijdstempel
        Element eIdTijdstempel = doc.createElementNS(STOP_DATA_NS, "eId");
        eIdTijdstempel.setTextContent("art_besluit2");
        tijdstempel.appendChild(eIdTijdstempel);
        
        // 5. BesluitCompact
        Element besluitCompact = doc.createElementNS(STOP_TEKST_NS, "BesluitCompact");
        besluitCompact.setPrefix("tekst");
        besluitVersie.appendChild(besluitCompact);
        
        // Voeg namespace toe voor tekst
        besluitCompact.setAttribute("xmlns:tekst", "https://standaarden.overheid.nl/stop/imop/tekst/");
        
        // RegelingOpschrift
        Element regelingOpschrift = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/tekst/", "RegelingOpschrift");
        regelingOpschrift.setAttribute("eId", "longTitle");
        regelingOpschrift.setAttribute("wId", "longTitle");
        Element alOpschrift = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/tekst/", "Al");
        alOpschrift.setTextContent("Officiele titel van de aanlevering");
        regelingOpschrift.appendChild(alOpschrift);
        besluitCompact.appendChild(regelingOpschrift);
        
        // Aanhef
        Element aanhef = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/tekst/", "Aanhef");
        aanhef.setAttribute("eId", "formula_1");
        aanhef.setAttribute("wId", "formula_1");
        Element alAanhef = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/tekst/", "Al");
        alAanhef.setTextContent("Aanhef van het besluit");
        aanhef.appendChild(alAanhef);
        besluitCompact.appendChild(aanhef);
        
        // Lichaam
        Element lichaam = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/tekst/", "Lichaam");
        lichaam.setAttribute("eId", "body");
        lichaam.setAttribute("wId", "body");
        
        // WijzigArtikel
        Element wijzigArtikel = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/tekst/", "WijzigArtikel");
        wijzigArtikel.setAttribute("eId", "art_besluit1");
        wijzigArtikel.setAttribute("wId", data.bevoegdGezag + "__art_besluit1");
        
        // Kop voor WijzigArtikel
        Element kopWijzig = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/tekst/", "Kop");
        Element labelWijzig = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/tekst/", "Label");
        labelWijzig.setTextContent("Artikel");
        Element nummerWijzig = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/tekst/", "Nummer");
        nummerWijzig.setTextContent("I");
        kopWijzig.appendChild(labelWijzig);
        kopWijzig.appendChild(nummerWijzig);
        wijzigArtikel.appendChild(kopWijzig);
        
        // Wat voor WijzigArtikel
        Element watWijzig = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/tekst/", "Wat");
        watWijzig.setTextContent("Wijzigingen zoals opgenomen in ");
        Element intRef = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/tekst/", "IntRef");
        intRef.setAttribute("ref", "cmp_besluit");
        intRef.setTextContent("Bijlage A");
        watWijzig.appendChild(intRef);
        watWijzig.appendChild(doc.createTextNode(" worden vastgesteld."));
        wijzigArtikel.appendChild(watWijzig);
        
        lichaam.appendChild(wijzigArtikel);
        
        // Artikel II
        Element artikel = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/tekst/", "Artikel");
        artikel.setAttribute("eId", "art_besluit2");
        artikel.setAttribute("wId", data.bevoegdGezag + "__art_besluit2");
        
        // Kop voor Artikel
        Element kopArtikel = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/tekst/", "Kop");
        Element labelArtikel = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/tekst/", "Label");
        labelArtikel.setTextContent("Artikel");
        Element nummerArtikel = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/tekst/", "Nummer");
        nummerArtikel.setTextContent("II");
        kopArtikel.appendChild(labelArtikel);
        kopArtikel.appendChild(nummerArtikel);
        artikel.appendChild(kopArtikel);
        
        // Inhoud voor Artikel
        Element inhoud = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/tekst/", "Inhoud");
        Element alInhoud = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/tekst/", "Al");
        alInhoud.setTextContent("Dit besluit treedt in werking per " + tomorrow.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        inhoud.appendChild(alInhoud);
        artikel.appendChild(inhoud);
        
        lichaam.appendChild(artikel);
        besluitCompact.appendChild(lichaam);
        
        // Sluiting
        Element sluiting = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/tekst/", "Sluiting");
        sluiting.setAttribute("eId", "formula_2");
        sluiting.setAttribute("wId", "formula_2");
        Element alSluiting = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/tekst/", "Al");
        alSluiting.setTextContent("Sluiting van het besluit");
        sluiting.appendChild(alSluiting);
        Element ondertekening = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/tekst/", "Ondertekening");
        Element alOndertekening = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/tekst/", "Al");
        alOndertekening.setTextContent("Ondertekening van het besluit");
        ondertekening.appendChild(alOndertekening);
        sluiting.appendChild(ondertekening);
        besluitCompact.appendChild(sluiting);
        
        // WijzigBijlage
        Element wijzigBijlage = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/tekst/", "WijzigBijlage");
        wijzigBijlage.setAttribute("eId", "cmp_besluit");
        wijzigBijlage.setAttribute("wId", data.bevoegdGezag + "__cmp_besluit");
        
        // Kop voor WijzigBijlage
        Element kopBijlage = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/tekst/", "Kop");
        Element labelBijlage = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/tekst/", "Label");
        labelBijlage.setTextContent("Bijlage");
        Element nummerBijlage = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/tekst/", "Nummer");
        nummerBijlage.setTextContent("A");
        Element opschriftBijlage = doc.createElementNS("https://standaarden.overheid.nl/stop/imop/tekst/", "Opschrift");
        opschriftBijlage.setTextContent("Bijlage bij artikel I");
        kopBijlage.appendChild(labelBijlage);
        kopBijlage.appendChild(nummerBijlage);
        kopBijlage.appendChild(opschriftBijlage);
        wijzigBijlage.appendChild(kopBijlage);
        
        // Lees en voeg de inhoud van Tekst.xml toe
        ZipEntry tekstEntry = zipFile.getEntry("Regeling/Tekst.xml");
        if (tekstEntry != null) {
            Document tekstDoc = builder.parse(zipFile.getInputStream(tekstEntry));
            // Importeer het root element zelf
            Node importedRoot = doc.importNode(tekstDoc.getDocumentElement(), true);
            
            // Voeg wordt en componentnaam attributen toe
            ((Element)importedRoot).setAttribute("wordt", data.frbrExpression);
            ((Element)importedRoot).setAttribute("componentnaam", "main");
            
            wijzigBijlage.appendChild(importedRoot);
        }
        
        besluitCompact.appendChild(wijzigBijlage);
        
        // Voeg RegelingVersieInformatie toe
        Element regelingVersieInfo = doc.createElement("RegelingVersieInformatie");
        root.appendChild(regelingVersieInfo);
        
        // Verwerk alle bestanden in de juiste volgorde
        for (String fileName : REGELING_FILES) {
            ZipEntry entry = zipFile.getEntry(fileName);
            if (entry != null) {
                // Parse de XML inhoud
                Document sourceDoc = builder.parse(zipFile.getInputStream(entry));
                
                // Converteer het element naar een element met de juiste namespace
                Element sourceRoot = sourceDoc.getDocumentElement();
                Element newElement = doc.createElementNS(STOP_DATA_NS, sourceRoot.getLocalName());
                newElement.setPrefix("data");
                
                // Kopieer de inhoud
                NodeList children = sourceRoot.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    Node child = children.item(i);
                    if (child.getNodeType() == Node.ELEMENT_NODE) {
                        Element childElement = (Element) child;
                        if (childElement.getLocalName().equals("onderwerpen") || 
                            childElement.getLocalName().equals("rechtsgebieden") || 
                            childElement.getLocalName().equals("overheidsdomeinen")) {
                            // Maak nieuwe elementen met namespace prefix
                            Element parentElement = doc.createElementNS(STOP_DATA_NS, childElement.getLocalName());
                            parentElement.setPrefix("data");
                            NodeList subElements = childElement.getChildNodes();
                            for (int j = 0; j < subElements.getLength(); j++) {
                                Node subNode = subElements.item(j);
                                if (subNode.getNodeType() == Node.ELEMENT_NODE) {
                                    Element subElement = (Element) subNode;
                                    // Voor overheidsdomeinen, maak overheidsdomein elementen
                                    String subElementName = childElement.getLocalName().equals("overheidsdomeinen") ? 
                                        "overheidsdomein" : subElement.getLocalName();
                                    Element newSubElement = doc.createElementNS(STOP_DATA_NS, subElementName);
                                    newSubElement.setPrefix("data");
                                    newSubElement.setTextContent(subElement.getTextContent().trim());
                                    parentElement.appendChild(newSubElement);
                                } else if (subNode.getNodeType() == Node.TEXT_NODE && 
                                         childElement.getLocalName().equals("overheidsdomeinen")) {
                                    // Verwerk tekst nodes voor overheidsdomeinen
                                    String[] domains = subNode.getTextContent().trim().split("\\s+");
                                    for (String domain : domains) {
                                        if (!domain.isEmpty()) {
                                            Element overheidsdomein = doc.createElementNS(STOP_DATA_NS, "overheidsdomein");
                                            overheidsdomein.setPrefix("data");
                                            overheidsdomein.setTextContent(domain);
                                            parentElement.appendChild(overheidsdomein);
                                        }
                                    }
                                }
                            }
                            newElement.appendChild(parentElement);
                        } else {
                            // Voor alle andere elementen, importeer de volledige structuur
                            Node importedNode = doc.importNode(childElement, true);
                            Element importedElement = (Element) importedNode;
                            // Zorg ervoor dat de namespace prefix correct is
                            if (importedElement.getNamespaceURI() != null) {
                                importedElement.setPrefix("data");
                            }
                            newElement.appendChild(importedElement);
                        }
                    }
                }
                
                regelingVersieInfo.appendChild(newElement);
            }
        }
        
        // Converteer naar bytes
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        
        // Configureer de output
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

    private static LocalDateTime getNextWorkingDay(LocalDateTime date) {
        LocalDateTime nextDay = date.plusDays(1);
        while (nextDay.getDayOfWeek().getValue() > 5) { // 5 = Friday
            nextDay = nextDay.plusDays(1);
        }
        return nextDay;
    }

    public static byte[] createOpdrachtXml(String bevoegdGezag, String datumTijd, LocalDateTime datumBekendmaking, boolean isValidation) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        
        Document doc = builder.newDocument();
        
        // Voeg XML declaratie toe
        doc.setXmlVersion("1.0");
        doc.setXmlStandalone(true);
        
        // Root element met namespace
        Element root = doc.createElementNS("http://www.overheid.nl/2017/lvbb", isValidation ? "validatieOpdracht" : "publicatieOpdracht");
        doc.appendChild(root);
        
        // idLevering met underscores
        Element idLevering = doc.createElement("idLevering");
        // Split datumTijd (format: yyyyMMddHHmmss) in datum (yyyyMMdd) en tijd (HHmmss)
        String leveringDatum = datumTijd.substring(0, 8);
        String leveringTijd = datumTijd.substring(8);
        idLevering.setTextContent("OTST_" + (isValidation ? "val_" : "pub_") + bevoegdGezag + "_" + leveringDatum + "_" + leveringTijd);
        root.appendChild(idLevering);
        
        // idBevoegdGezag
        Element idBevoegdGezag = doc.createElement("idBevoegdGezag");
        idBevoegdGezag.setTextContent("00000001003214345000");
        root.appendChild(idBevoegdGezag);
        
        // idAanleveraar
        Element idAanleveraar = doc.createElement("idAanleveraar");
        idAanleveraar.setTextContent("00000001003214345000");
        root.appendChild(idAanleveraar);
        
        // publicatie
        Element publicatie = doc.createElement("publicatie");
        publicatie.setTextContent("besluit.xml");
        root.appendChild(publicatie);
        
        // Voeg datumBekendmaking altijd toe
        Element datumBekendmakingElement = doc.createElement("datumBekendmaking");
        // Bereken de eerstvolgende werkdag
        LocalDateTime nextWorkingDay = getNextWorkingDay(LocalDateTime.now());
        datumBekendmakingElement.setTextContent(nextWorkingDay.format(DateTimeFormatter.ISO_DATE));
        root.appendChild(datumBekendmakingElement);
        
        // Converteer naar bytes
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        
        // Configureer de output
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.VERSION, "1.0");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "3");
        transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
        
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(doc), new StreamResult(output));
        
        return output.toString("UTF-8").getBytes("UTF-8");
    }

    private static boolean isValidAuthorityType(String type) {
        return type.equals("gemeente") || 
               type.equals("provincie") || 
               type.equals("ministerie") || 
               type.equals("waterschap");
    }
} 