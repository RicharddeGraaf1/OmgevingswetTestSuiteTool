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
        
        // 1. Haal FRBRWork en FRBRExpression op uit Regeling/Identificatie.xml
        ZipEntry identificatieEntry = zipFile.getEntry("Regeling/Identificatie.xml");
        if (identificatieEntry != null) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(zipFile.getInputStream(identificatieEntry));
            
            // Zoek eerst naar FRBRWork en FRBRExpression met namespace
            NodeList frbrWorkNodes = doc.getElementsByTagNameNS(STOP_DATA_NS, "FRBRWork");
            if (frbrWorkNodes.getLength() > 0) {
                data.frbrWork = frbrWorkNodes.item(0).getTextContent();
            }
            
            NodeList frbrExprNodes = doc.getElementsByTagNameNS(STOP_DATA_NS, "FRBRExpression");
            if (frbrExprNodes.getLength() > 0) {
                data.frbrExpression = frbrExprNodes.item(0).getTextContent();
            }
            
            // Als we ze niet hebben gevonden, probeer dan zonder namespace
            if (data.frbrWork == null) {
                frbrWorkNodes = doc.getElementsByTagName("FRBRWork");
                if (frbrWorkNodes.getLength() > 0) {
                    data.frbrWork = frbrWorkNodes.item(0).getTextContent();
                }
            }
            
            if (data.frbrExpression == null) {
                frbrExprNodes = doc.getElementsByTagName("FRBRExpression");
                if (frbrExprNodes.getLength() > 0) {
                    data.frbrExpression = frbrExprNodes.item(0).getTextContent();
                }
            }
            
            System.out.println("Debug - Regeling FRBRWork: " + data.frbrWork);
            System.out.println("Debug - Regeling FRBRExpression: " + data.frbrExpression);
        }
        
        // 2. Haal doel op uit Regeling/Momentopname.xml
        data.doel = getDoelFromMomentOpname(zipFile, "Regeling");
        
        // 3. Haal bevoegd gezag op uit Regeling/Metadata.xml
        ZipEntry metadataEntry = zipFile.getEntry("Regeling/Metadata.xml");
        if (metadataEntry != null) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(zipFile.getInputStream(metadataEntry));
            
            NodeList makerNodes = doc.getElementsByTagNameNS(STOP_DATA_NS, "maker");
            if (makerNodes.getLength() > 0) {
                String makerValue = makerNodes.item(0).getTextContent();
                // Extract code from format like "/tooi/id/gemeente/gm0297"
                String[] parts = makerValue.split("/");
                if (parts.length >= 4) {
                    data.bevoegdGezag = parts[parts.length - 1];
                }
            }
        }
        
        // 4. Verzamel informatie over informatieobjecten
        List<String> ioFolders = new ArrayList<>();
        zipFile.stream()
               .filter(entry -> entry.getName().matches("IO-\\d+/.*"))
               .forEach(entry -> {
                   String path = entry.getName();
                   String ioFolder = path.substring(0, path.indexOf('/', 3));
                   if (!ioFolders.contains(ioFolder)) {
                       ioFolders.add(ioFolder);
                   }
               });
        
        data.aantalInformatieObjecten = ioFolders.size();
        
        // 5. Bereken totale GML bestandsgrootte
        data.totaleGmlBestandsgrootte = zipFile.stream()
            .filter(entry -> entry.getName().endsWith(".gml"))
            .mapToLong(ZipEntry::getSize)
            .sum();
        
        // 6. Verzamel informatie per informatieobject
        for (String ioFolder : ioFolders) {
            AnalyseData.InformatieObjectData ioData = new AnalyseData.InformatieObjectData();
            ioData.folder = ioFolder;
            
            // Zoek het GML bestand in de IO folder
            Optional<ZipEntry> gmlEntry = zipFile.stream()
                .filter(entry -> entry.getName().startsWith(ioFolder + "/") && entry.getName().endsWith(".gml"))
                .map(entry -> (ZipEntry)entry)
                .findFirst();
                
            if (gmlEntry.isPresent()) {
                ioData.bestandsnaam = gmlEntry.get().getName().substring(gmlEntry.get().getName().lastIndexOf("/") + 1);
                // Bereken SHA512 hash van GML bestand
                try (InputStream is = zipFile.getInputStream(gmlEntry.get())) {
                    ioData.bestandHash = calculateSHA512(is);
                }
            }
            
            // Haal FRBRWork en FRBRExpression op uit IO/Identificatie.xml
            ZipEntry ioIdentificatieEntry = zipFile.getEntry(ioFolder + "/Identificatie.xml");
            if (ioIdentificatieEntry != null) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(zipFile.getInputStream(ioIdentificatieEntry));
                
                // Zoek eerst met namespace
                NodeList frbrWorkNodes = doc.getElementsByTagNameNS(STOP_DATA_NS, "FRBRWork");
                if (frbrWorkNodes.getLength() > 0) {
                    ioData.frbrWork = frbrWorkNodes.item(0).getTextContent();
                }
                
                NodeList frbrExprNodes = doc.getElementsByTagNameNS(STOP_DATA_NS, "FRBRExpression");
                if (frbrExprNodes.getLength() > 0) {
                    ioData.frbrExpression = frbrExprNodes.item(0).getTextContent();
                }
                
                // Als we ze niet hebben gevonden, probeer dan zonder namespace
                if (ioData.frbrWork == null) {
                    frbrWorkNodes = doc.getElementsByTagName("FRBRWork");
                    if (frbrWorkNodes.getLength() > 0) {
                        ioData.frbrWork = frbrWorkNodes.item(0).getTextContent();
                    }
                }
                
                if (ioData.frbrExpression == null) {
                    frbrExprNodes = doc.getElementsByTagName("FRBRExpression");
                    if (frbrExprNodes.getLength() > 0) {
                        ioData.frbrExpression = frbrExprNodes.item(0).getTextContent();
                    }
                }
                
                System.out.println("Debug - IO " + ioFolder + " FRBRWork: " + ioData.frbrWork);
                System.out.println("Debug - IO " + ioFolder + " FRBRExpression: " + ioData.frbrExpression);
            }
            
            // Haal officieleTitel op uit VersieMetadata.xml
            ZipEntry versieMetadataEntry = zipFile.getEntry(ioFolder + "/VersieMetadata.xml");
            if (versieMetadataEntry != null) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(zipFile.getInputStream(versieMetadataEntry));
                
                // Zoek eerst met namespace
                NodeList titelNodes = doc.getElementsByTagNameNS(STOP_DATA_NS, "officieleTitel");
                if (titelNodes.getLength() > 0) {
                    ioData.officieleTitel = titelNodes.item(0).getTextContent();
                } else {
                    // Probeer zonder namespace
                    titelNodes = doc.getElementsByTagName("officieleTitel");
                    if (titelNodes.getLength() > 0) {
                        ioData.officieleTitel = titelNodes.item(0).getTextContent();
                    }
                }
                
                System.out.println("Debug - IO " + ioFolder + " officieleTitel: " + ioData.officieleTitel);
            }
            
            // Haal ExtIoRef-eId op uit Regeling/Tekst.xml
            ZipEntry tekstEntry = zipFile.getEntry("Regeling/Tekst.xml");
            if (tekstEntry != null) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(zipFile.getInputStream(tekstEntry));
                
                // Zoek naar ExtIoRef elementen met de juiste namespace
                NodeList extIoRefNodes = doc.getElementsByTagNameNS(STOP_TEKST_NS, "ExtIoRef");
                for (int i = 0; i < extIoRefNodes.getLength(); i++) {
                    Element extIoRef = (Element) extIoRefNodes.item(i);
                    String refValue = extIoRef.getAttribute("ref");
                    if (refValue.equals(ioData.frbrExpression)) {
                        ioData.extIoRefEId = extIoRef.getAttribute("eId");
                        System.out.println("Debug - IO " + ioFolder + " ExtIoRef-eId gevonden: " + ioData.extIoRefEId);
                        break;
                    }
                }
            }
            
            data.informatieObjecten.add(ioData);
        }
        
        // 7. Verzamel alle ExtIoRef paren uit Regeling/Tekst.xml
        ZipEntry tekstEntry = zipFile.getEntry("Regeling/Tekst.xml");
        if (tekstEntry != null) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(zipFile.getInputStream(tekstEntry));
            
            // Zoek naar alle ExtIoRef elementen met de juiste namespace
            NodeList extIoRefNodes = doc.getElementsByTagNameNS(STOP_TEKST_NS, "ExtIoRef");
            for (int i = 0; i < extIoRefNodes.getLength(); i++) {
                Element extIoRef = (Element) extIoRefNodes.item(i);
                AnalyseData.ExtIoRefData extIoRefData = new AnalyseData.ExtIoRefData();
                extIoRefData.ref = extIoRef.getAttribute("ref");
                extIoRefData.eId = extIoRef.getAttribute("eId");
                data.extIoRefs.add(extIoRefData);
                System.out.println("Debug - ExtIoRef gevonden - ref: " + extIoRefData.ref + ", eId: " + extIoRefData.eId);
            }
        }
        
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
    
    private static String calculateSHA512(InputStream input) throws Exception {
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
    
    private static void cleanupRegelingFolder() {
        try {
            Path regelingPath = Paths.get("Regeling");
            if (Files.exists(regelingPath)) {
                Files.walk(regelingPath)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            }
        } catch (IOException e) {
            System.err.println("Fout bij verwijderen Regeling map: " + e.getMessage());
        }
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
        try {
            System.out.println("Debug: Start createBesluitXml");
            
            // Verwijder eerst eventuele oude Regeling map
            cleanupRegelingFolder();
            
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
            byte[] opdrachtXml = createOpdrachtXml(data.bevoegdGezag, datumTijd, tomorrow);
            
            System.out.println("Debug: Generated opdracht.xml content, size=" + opdrachtXml.length);
            
            // Genereer besluit.xml inhoud
            byte[] besluitXml = generateBesluitXml(data, zipFile, huidigJaartal, datumTijd, datum, tomorrow);
            
            return new BesluitResult(besluitXml, opdrachtXml);
        } catch (Exception e) {
            // Zorg ervoor dat we de map ook opruimen bij een fout
            cleanupRegelingFolder();
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
        Element root = doc.createElementNS(STOP_DATA_NS, "AanleveringBesluit");
        root.setAttribute("xmlns:data", STOP_DATA_NS);
        doc.appendChild(root);
        
        // Voeg BesluitVersie toe
        Element besluitVersie = doc.createElementNS(STOP_DATA_NS, "BesluitVersie");
        root.appendChild(besluitVersie);
        
        // 1. ExpressionIdentificatie
        Element expressionId = doc.createElementNS(STOP_DATA_NS, "ExpressionIdentificatie");
        besluitVersie.appendChild(expressionId);
        
        Element frbWork = doc.createElementNS(STOP_DATA_NS, "FRBRWork");
        frbWork.setTextContent(String.format("/akn/nl/bill/%s/%s/OTSTgegenereerd%s", 
            data.bevoegdGezag, huidigJaartal, datumTijd));
        expressionId.appendChild(frbWork);
        
        Element frbExpr = doc.createElementNS(STOP_DATA_NS, "FRBRExpression");
        frbExpr.setTextContent(String.format("/akn/nl/bill/%s/%s/OTSTgegenereerd%s/nld@%s;1", 
            data.bevoegdGezag, huidigJaartal, datumTijd, datum));
        expressionId.appendChild(frbExpr);
        
        Element soortWork = doc.createElementNS(STOP_DATA_NS, "soortWork");
        soortWork.setTextContent("/join/id/stop/work_003");
        expressionId.appendChild(soortWork);
        
        // 2. BesluitMetadata
        Element besluitMetadata = doc.createElementNS(STOP_DATA_NS, "BesluitMetadata");
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
                    // Sla soortRegeling over
                    if (!element.getTagName().equals("soortRegeling")) {
                        Element newElement = doc.createElementNS(STOP_DATA_NS, element.getTagName());
                        
                        // Pas officieleTitel aan
                        if (element.getTagName().equals("officieleTitel")) {
                            newElement.setTextContent(element.getTextContent() + " OTST" + datumTijd);
                        } else if (element.getTagName().equals("onderwerpen")) {
                            // Maak een nieuwe onderwerpen element
                            Element onderwerpen = doc.createElementNS(STOP_DATA_NS, "onderwerpen");
                            // Maak een onderwerp element voor elke inhoud
                            String[] onderwerpItems = element.getTextContent().trim().split("\\s+");
                            for (String item : onderwerpItems) {
                                if (!item.isEmpty()) {
                                    Element onderwerp = doc.createElementNS(STOP_DATA_NS, "onderwerp");
                                    onderwerp.setTextContent(item);
                                    onderwerpen.appendChild(onderwerp);
                                }
                            }
                            metadataElements.add(onderwerpen);
                        } else if (element.getTagName().equals("rechtsgebieden")) {
                            // Maak een nieuwe rechtsgebieden element
                            Element rechtsgebieden = doc.createElementNS(STOP_DATA_NS, "rechtsgebieden");
                            // Maak een rechtsgebied element voor elke inhoud
                            String[] rechtsgebiedItems = element.getTextContent().trim().split("\\s+");
                            for (String item : rechtsgebiedItems) {
                                if (!item.isEmpty()) {
                                    Element rechtsgebied = doc.createElementNS(STOP_DATA_NS, "rechtsgebied");
                                    rechtsgebied.setTextContent(item);
                                    rechtsgebieden.appendChild(rechtsgebied);
                                }
                            }
                            metadataElements.add(rechtsgebieden);
                        } else {
                            newElement.setTextContent(element.getTextContent().trim());
                            metadataElements.add(newElement);
                        }
                    }
                }
            }
            
            // Voeg soortProcedure toe
            Element soortProcedure = doc.createElementNS(STOP_DATA_NS, "soortProcedure");
            soortProcedure.setTextContent("/join/id/stop/proceduretype_definitief");
            metadataElements.add(soortProcedure);
            
            // Sorteer op elementnaam
            metadataElements.sort((a, b) -> a.getLocalName().compareTo(b.getLocalName()));
            
            // Voeg gesorteerde elementen toe aan BesluitMetadata
            for (Element element : metadataElements) {
                // Sla heeftCiteertitelInformatie over
                if (!element.getLocalName().equals("heeftCiteertitelInformatie")) {
                    besluitMetadata.appendChild(element);
                }
            }
        }
        
        // 3. Procedureverloop
        Element procedureverloop = doc.createElementNS(STOP_DATA_NS, "Procedureverloop");
        besluitVersie.appendChild(procedureverloop);
        
        // Voeg bekendOp toe
        Element bekendOp = doc.createElementNS(STOP_DATA_NS, "bekendOp");
        bekendOp.setTextContent(tomorrow.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        procedureverloop.appendChild(bekendOp);
        
        // Voeg procedurestappen toe
        Element procedurestappen = doc.createElementNS(STOP_DATA_NS, "procedurestappen");
        procedureverloop.appendChild(procedurestappen);
        
        // Voeg Procedurestap toe
        Element procedurestap = doc.createElementNS(STOP_DATA_NS, "Procedurestap");
        procedurestappen.appendChild(procedurestap);
        
        // Voeg soortStap toe
        Element soortStap = doc.createElementNS(STOP_DATA_NS, "soortStap");
        soortStap.setTextContent("/join/id/stop/procedure/stap_003");
        procedurestap.appendChild(soortStap);
        
        // Voeg voltooidOp toe
        Element voltooidOp = doc.createElementNS(STOP_DATA_NS, "voltooidOp");
        voltooidOp.setTextContent(tomorrow.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        procedurestap.appendChild(voltooidOp);
        
        // 4. ConsolidatieInformatie
        Element consolidatieInformatie = doc.createElementNS(STOP_DATA_NS, "ConsolidatieInformatie");
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
        Element instrumentversieRegeling = doc.createElementNS(STOP_DATA_NS, "instrumentversie");
        instrumentversieRegeling.setTextContent(data.frbrExpression);
        beoogdeRegeling.appendChild(instrumentversieRegeling);
        
        // eId onder BeoogdeRegeling
        Element eIdRegeling = doc.createElementNS(STOP_DATA_NS, "eId");
        eIdRegeling.setTextContent("art_besluit1");
        beoogdeRegeling.appendChild(eIdRegeling);
        
        // BeoogdInformatieObject voor elke IO
        for (AnalyseData.InformatieObjectData io : data.informatieObjecten) {
            Element beoogdInformatieObject = doc.createElementNS(STOP_DATA_NS, "BeoogdInformatieObject");
            beoogdeRegelgeving.appendChild(beoogdInformatieObject);
            
            // Doelen onder BeoogdInformatieObject
            Element doelenIO = doc.createElementNS(STOP_DATA_NS, "doelen");
            beoogdInformatieObject.appendChild(doelenIO);
            
            Element doelIO = doc.createElementNS(STOP_DATA_NS, "doel");
            doelIO.setTextContent(data.doel);
            doelenIO.appendChild(doelIO);
            
            // Instrumentversie onder BeoogdInformatieObject
            Element instrumentversieIO = doc.createElementNS(STOP_DATA_NS, "instrumentversie");
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
        Element besluitCompact = doc.createElementNS(STOP_DATA_NS, "BesluitCompact");
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
        Element regelingVersieInfo = doc.createElementNS(STOP_DATA_NS, "RegelingVersieInformatie");
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
                
                // Kopieer de inhoud
                NodeList children = sourceRoot.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    Node child = children.item(i);
                    if (child.getNodeType() == Node.ELEMENT_NODE) {
                        Element childElement = (Element) child;
                        if (childElement.getLocalName().equals("onderwerpen")) {
                            Element onderwerpen = doc.createElementNS(STOP_DATA_NS, "onderwerpen");
                            String[] onderwerpItems = childElement.getTextContent().trim().split("\\s+");
                            for (String item : onderwerpItems) {
                                if (!item.isEmpty()) {
                                    Element onderwerp = doc.createElementNS(STOP_DATA_NS, "onderwerp");
                                    onderwerp.setTextContent(item);
                                    onderwerpen.appendChild(onderwerp);
                                }
                            }
                            newElement.appendChild(onderwerpen);
                        } else if (childElement.getLocalName().equals("rechtsgebieden")) {
                            Element rechtsgebieden = doc.createElementNS(STOP_DATA_NS, "rechtsgebieden");
                            String[] rechtsgebiedItems = childElement.getTextContent().trim().split("\\s+");
                            for (String item : rechtsgebiedItems) {
                                if (!item.isEmpty()) {
                                    Element rechtsgebied = doc.createElementNS(STOP_DATA_NS, "rechtsgebied");
                                    rechtsgebied.setTextContent(item);
                                    rechtsgebieden.appendChild(rechtsgebied);
                                }
                            }
                            newElement.appendChild(rechtsgebieden);
                        } else {
                            Element newChild = doc.createElementNS(STOP_DATA_NS, childElement.getLocalName());
                            newChild.setTextContent(childElement.getTextContent().trim());
                            newElement.appendChild(newChild);
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

    public static byte[] createOpdrachtXml(String bevoegdGezag, String datumTijd, LocalDateTime datumBekendmaking) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        
        Document doc = builder.newDocument();
        
        // Voeg XML declaratie toe
        doc.setXmlVersion("1.0");
        doc.setXmlStandalone(true);
        
        // Root element met namespace
        Element root = doc.createElementNS("http://www.overheid.nl/2017/lvbb", "publicatieOpdracht");
        doc.appendChild(root);
        
        // idLevering met underscores
        Element idLevering = doc.createElement("idLevering");
        idLevering.setTextContent("OTST_" + bevoegdGezag + "_" + datumTijd);
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
        
        // datumBekendmaking
        Element datumBekendmakingElement = doc.createElement("datumBekendmaking");
        datumBekendmakingElement.setTextContent(datumBekendmaking.format(DateTimeFormatter.ISO_DATE));
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
} 