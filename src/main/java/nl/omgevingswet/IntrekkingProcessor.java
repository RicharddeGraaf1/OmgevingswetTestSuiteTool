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
import java.time.LocalDate;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;

public class IntrekkingProcessor {
    
    private static final String STOP_DATA_NS = "https://standaarden.overheid.nl/stop/imop/data/";
    private static final String STOP_TEKST_NS = "https://standaarden.overheid.nl/stop/imop/tekst/";
    
    // Map om gewijzigde OW-bestanden op te slaan
    private static Map<String, byte[]> modifiedOwFiles = new HashMap<>();
    
    public static class IntrekkingResult {
        public byte[] besluitXml;
        public byte[] opdrachtXml;
        public Map<String, byte[]> modifiedFiles;
        
        public IntrekkingResult(byte[] besluitXml, byte[] opdrachtXml, Map<String, byte[]> modifiedFiles) {
            this.besluitXml = besluitXml;
            this.opdrachtXml = opdrachtXml;
            this.modifiedFiles = modifiedFiles;
        }
    }
    
    public static IntrekkingResult createIntrekkingXml(ZipFile sourceZip, boolean isValidation) throws Exception {
        // Reset de map met gewijzigde bestanden
        modifiedOwFiles.clear();
        
        // Haal eerst de analyse data op
        BesluitProcessor.AnalyseData data = BesluitProcessor.analyseZip(sourceZip);
        
        // Verwerk alle OW-bestanden
        processOwFiles(sourceZip);
        
        // Haal de RegelingMetadata op uit de bron
        RegelingMetadata regelingMetadata = extractRegelingMetadata(sourceZip);
        
        // Genereer de XML bestanden
        byte[] besluitXml = generateBesluitXml(data, regelingMetadata);
        byte[] opdrachtXml = createOpdrachtXml(data, isValidation);
        
        return new IntrekkingResult(besluitXml, opdrachtXml, new HashMap<>(modifiedOwFiles));
    }
    
    private static void processOwFiles(ZipFile sourceZip) throws Exception {
        // Verzamel alle OW-bestanden
        List<? extends ZipEntry> owEntries = sourceZip.stream()
                .filter(entry -> entry.getName().toLowerCase().endsWith(".xml") &&
                               (entry.getName().startsWith("OW-bestanden/") || 
                                entry.getName().toLowerCase().contains("/ow/")))
                .collect(Collectors.toList());

        // Haal de bevoegdGezag op voor het doel
        BesluitProcessor.AnalyseData data = BesluitProcessor.analyseZip(sourceZip);
        String doelId = generateDoelId(data.bevoegdGezag);

        for (ZipEntry entry : owEntries) {
            // Parse het XML bestand
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setNamespaceAware(true);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(sourceZip.getInputStream(entry));
            doc.getDocumentElement().normalize();

            boolean modified = false;

            // Check of dit een manifest-OW bestand is
            if (entry.getName().toLowerCase().contains("manifest-ow")) {
                // Zoek het DoelID element
                NodeList doelIdNodes = doc.getElementsByTagNameNS("*", "DoelID");
                if (doelIdNodes.getLength() > 0) {
                    doelIdNodes.item(0).setTextContent(doelId);
                    modified = true;
                    System.out.println("Updated DoelID in manifest-OW to: " + doelId);
                }
            }

            // Zoek alle owObject elementen
            NodeList owObjects = doc.getElementsByTagNameNS("*", "owObject");
            boolean isRegeltekstenBestand = entry.getName().toLowerCase().contains("regeltekst");

            for (int i = 0; i < owObjects.getLength(); i++) {
                Element owObject = (Element) owObjects.item(i);
                
                // Krijg het eerste kind van owObject
                NodeList children = owObject.getChildNodes();
                for (int j = 0; j < children.getLength(); j++) {
                    Node child = children.item(j);
                    if (child.getNodeType() == Node.ELEMENT_NODE) {
                        Element objectElement = (Element) child;
                        
                        // Bepaal de juiste prefix voor het status element
                        String prefix = isRegeltekstenBestand || "Regeltekst".equals(objectElement.getLocalName()) ? "op" : "ow";

                        // Maak het status element (zonder expliciete namespace)
                        Element statusElement = doc.createElement(prefix + ":status");
                        statusElement.setTextContent("beëindigen");
                        
                        // Voeg het status element toe vóór het identificatie element
                        NodeList identElements = objectElement.getElementsByTagNameNS("*", "identificatie");
                        if (identElements.getLength() > 0) {
                            objectElement.insertBefore(statusElement, identElements.item(0));
                        } else {
                            // Als er geen identificatie element is, voeg het toe als eerste element
                            objectElement.insertBefore(statusElement, objectElement.getFirstChild());
                        }
                        modified = true;
                        break;
                    }
                }
            }

            if (modified) {
                // Schrijf het bestand naar de map met gewijzigde bestanden
                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                Transformer transformer = transformerFactory.newTransformer();
                
                // Verbeter de formatting
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
                transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
                transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, "");
                transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "");
                transformer.setOutputProperty(OutputKeys.CDATA_SECTION_ELEMENTS, "");
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
                
                // Gebruik een StringWriter om de output te controleren
                StringWriter writer = new StringWriter();
                transformer.transform(new DOMSource(doc), new StreamResult(writer));
                
                // Verwijder dubbele witregels, normaliseer line endings en verwijder DOCTYPE
                String xmlContent = writer.toString()
                    .replaceAll("<!DOCTYPE[^>]*>", "")  // Verwijder DOCTYPE declaratie
                    .replaceAll("(?m)^\\s*$[\n\r]{1,}", "\n")  // Vervang meerdere witregels door één
                    .replaceAll("(?m)^\\s+", "")              // Verwijder witregels aan het begin van elke regel
                    .replaceAll("(?m)\\s+$", "");            // Verwijder witregels aan het einde van elke regel
                
                // Bepaal de nieuwe bestandsnaam (alleen de bestandsnaam zonder pad voor bestanden uit OW-bestanden)
                String newEntryName = entry.getName().startsWith("OW-bestanden/") ? 
                    new File(entry.getName()).getName() : entry.getName();
                
                // Sla het gewijzigde bestand op in de map
                modifiedOwFiles.put(newEntryName, xmlContent.getBytes(StandardCharsets.UTF_8));
                System.out.println("OW-bestand gewijzigd en opgeslagen: " + newEntryName);
            }
        }
    }
    
    private static class RegelingMetadata {
        String eindverantwoordelijke;
        String maker;
        String officieleTitel;
        String citeerTitel;
        String soortBestuursorgaan;
        List<String> onderwerpen;
        String opvolging;
        Map<String, String> overigeMetadata = new HashMap<>();
    }
    
    private static RegelingMetadata extractRegelingMetadata(ZipFile sourceZip) throws Exception {
        RegelingMetadata metadata = new RegelingMetadata();
        metadata.onderwerpen = new ArrayList<>();
        metadata.overigeMetadata = new HashMap<>();
        
        System.out.println("DEBUG: Starting metadata extraction");
        
        // List all entries to debug
        System.out.println("DEBUG: All entries in zip:");
        sourceZip.stream().forEach(entry -> System.out.println("  - " + entry.getName()));
        
        // Find Regeling/Metadata.xml specifically
        Optional<? extends ZipEntry> regelingEntry = sourceZip.stream()
                .filter(entry -> entry.getName().equals("Regeling/Metadata.xml"))
                .findFirst();

        if (!regelingEntry.isPresent()) {
            // Try case-insensitive search if exact match fails
            regelingEntry = sourceZip.stream()
                    .filter(entry -> entry.getName().toLowerCase().contains("regeling") && 
                                   entry.getName().toLowerCase().contains("metadata.xml"))
                    .findFirst();
        }

        System.out.println("DEBUG: Found metadata file? " + regelingEntry.isPresent());
        if (regelingEntry.isPresent()) {
            System.out.println("DEBUG: Metadata file path: " + regelingEntry.get().getName());
            
            // Parse het XML bestand
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setNamespaceAware(true);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            
            // Read and print the XML content for debugging
            try (InputStream is = sourceZip.getInputStream(regelingEntry.get())) {
                byte[] content = is.readAllBytes();
                System.out.println("DEBUG: Raw XML content:");
                System.out.println(new String(content, StandardCharsets.UTF_8));
                
                // Parse the content
                Document doc = dBuilder.parse(new ByteArrayInputStream(content));
                doc.getDocumentElement().normalize();
                
                // Get RegelingMetadata element
                NodeList metadataList = doc.getElementsByTagNameNS(STOP_DATA_NS, "RegelingMetadata");
                System.out.println("DEBUG: Found RegelingMetadata elements: " + metadataList.getLength());
                
                if (metadataList.getLength() > 0) {
                    Element metadataElement = (Element) metadataList.item(0);
                    
                    // First handle soortBestuursorgaan and onderwerpen specifically
                    NodeList soortBestuursorgaanNodes = metadataElement.getElementsByTagNameNS(STOP_DATA_NS, "soortBestuursorgaan");
                    if (soortBestuursorgaanNodes.getLength() > 0) {
                        String soortBestuursorgaan = soortBestuursorgaanNodes.item(0).getTextContent();
                        metadata.soortBestuursorgaan = soortBestuursorgaan;
                        metadata.overigeMetadata.put("soortBestuursorgaan", 
                            "<data:soortBestuursorgaan>" + soortBestuursorgaan + "</data:soortBestuursorgaan>");
                        System.out.println("DEBUG: Found soortBestuursorgaan: " + soortBestuursorgaan);
                    }
                    
                    NodeList onderwerpenNodes = metadataElement.getElementsByTagNameNS(STOP_DATA_NS, "onderwerpen");
                    if (onderwerpenNodes.getLength() > 0) {
                        Element onderwerpenElement = (Element) onderwerpenNodes.item(0);
                        NodeList onderwerpNodes = onderwerpenElement.getElementsByTagNameNS(STOP_DATA_NS, "onderwerp");
                        System.out.println("DEBUG: Found onderwerp nodes: " + onderwerpNodes.getLength());
                        
                        StringBuilder onderwerpenXml = new StringBuilder("<data:onderwerpen>");
                        for (int i = 0; i < onderwerpNodes.getLength(); i++) {
                            String onderwerp = onderwerpNodes.item(i).getTextContent();
                            metadata.onderwerpen.add(onderwerp);
                            onderwerpenXml.append("<data:onderwerp>").append(onderwerp).append("</data:onderwerp>");
                            System.out.println("DEBUG: Added onderwerp: " + onderwerp);
                        }
                        onderwerpenXml.append("</data:onderwerpen>");
                        metadata.overigeMetadata.put("onderwerpen", onderwerpenXml.toString());
                    }
                    
                    // Handle other elements
                    NodeList children = metadataElement.getChildNodes();
                    for (int i = 0; i < children.getLength(); i++) {
                        Node node = children.item(i);
                        if (node.getNodeType() == Node.ELEMENT_NODE) {
                            Element element = (Element) node;
                            String localName = element.getLocalName();
                            String elementContent = element.getTextContent();
                            
                            if ("soortRegeling".equals(localName)) continue;
                            if ("soortBestuursorgaan".equals(localName)) continue;
                            if ("onderwerpen".equals(localName)) continue;
                            
                            System.out.println("DEBUG: Processing other element: " + localName + " = " + elementContent);
                            
                            switch (localName) {
                                case "eindverantwoordelijke":
                                    metadata.eindverantwoordelijke = elementContent;
                                    metadata.overigeMetadata.put(localName, nodeToString(element));
                                    break;
                                case "maker":
                                    metadata.maker = elementContent;
                                    metadata.overigeMetadata.put(localName, nodeToString(element));
                                    break;
                                case "officieleTitel":
                                    metadata.officieleTitel = elementContent;
                                    metadata.overigeMetadata.put(localName, nodeToString(element));
                                    break;
                                case "heeftCiteertitelInformatie":
                                    NodeList citeertitelNodes = element.getElementsByTagNameNS(STOP_DATA_NS, "citeertitel");
                                    if (citeertitelNodes.getLength() > 0) {
                                        metadata.citeerTitel = citeertitelNodes.item(0).getTextContent();
                                    }
                                    metadata.overigeMetadata.put(localName, nodeToString(element));
                                    break;
                                case "opvolging":
                                    metadata.opvolging = elementContent;
                                    metadata.overigeMetadata.put(localName, nodeToString(element));
                                    break;
                            }
                        }
                    }
                }
            }
        } else {
            System.out.println("DEBUG: No metadata file found, using default values");
            // Als we geen metadata kunnen vinden, gebruik standaard waarden
            BesluitProcessor.AnalyseData data = BesluitProcessor.analyseZip(sourceZip);
            String bevoegdGezagPath = "/tooi/id/gemeente/" + data.bevoegdGezag;
            
            metadata.eindverantwoordelijke = bevoegdGezagPath;
            metadata.maker = bevoegdGezagPath;
            metadata.officieleTitel = data.frbrWork;
            metadata.soortBestuursorgaan = "/tooi/def/thes/kern/c_411b319c";
            metadata.onderwerpen.add("/tooi/def/concept/c_1c12723d");
            
            metadata.overigeMetadata.put("eindverantwoordelijke", "<data:eindverantwoordelijke>" + bevoegdGezagPath + "</data:eindverantwoordelijke>");
            metadata.overigeMetadata.put("maker", "<data:maker>" + bevoegdGezagPath + "</data:maker>");
            metadata.overigeMetadata.put("officieleTitel", "<data:officieleTitel>" + data.frbrWork + "</data:officieleTitel>");
            metadata.overigeMetadata.put("soortBestuursorgaan", "<data:soortBestuursorgaan>/tooi/def/thes/kern/c_411b319c</data:soortBestuursorgaan>");
            metadata.overigeMetadata.put("onderwerpen", "<data:onderwerpen><data:onderwerp>/tooi/def/concept/c_1c12723d</data:onderwerp></data:onderwerpen>");
        }
        
        System.out.println("\nFinal metadata values:");
        System.out.println("soortBestuursorgaan: " + metadata.soortBestuursorgaan);
        System.out.println("onderwerpen: " + metadata.onderwerpen);
        System.out.println("overigeMetadata: " + metadata.overigeMetadata);
        
        return metadata;
    }
    
    private static String nodeToString(Node node) {
        try {
            StringWriter sw = new StringWriter();
            Transformer t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            t.setOutputProperty(OutputKeys.INDENT, "no");
            t.transform(new DOMSource(node), new StreamResult(sw));
            return sw.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }
    
    private static void addMetadataElement(Document doc, Element metadata, String key, String xmlContent) throws Exception {
        // Wrap the content in a root element to make it valid XML
        String wrappedXml = "<wrapper xmlns:data=\"" + STOP_DATA_NS + "\">" + xmlContent + "</wrapper>";
        
        // Create a new document builder for parsing
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        
        // Parse the wrapped content
        Document tempDoc = builder.parse(new ByteArrayInputStream(wrappedXml.getBytes("UTF-8")));
        
        // Get the first child of the wrapper (our actual content)
        NodeList children = tempDoc.getDocumentElement().getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Node importedNode = doc.importNode(child, true);
                
                // Special handling for citeerTitel and officieleTitel
                if ("heeftCiteertitelInformatie".equals(key)) {
                    NodeList citeertitelNodes = ((Element)importedNode).getElementsByTagNameNS(STOP_DATA_NS, "citeertitel");
                    for (int j = 0; j < citeertitelNodes.getLength(); j++) {
                        Node citeertitel = citeertitelNodes.item(j);
                        citeertitel.setTextContent(citeertitel.getTextContent() + " intrekking");
                    }
                } else if ("officieleTitel".equals(key)) {
                    importedNode.setTextContent(importedNode.getTextContent() + " intrekking");
                }
                
                // Set the correct namespace prefix
                if (importedNode.getNamespaceURI() != null) {
                    ((Element)importedNode).setPrefix("data");
                    NodeList descendants = ((Element)importedNode).getElementsByTagNameNS(STOP_DATA_NS, "*");
                    for (int j = 0; j < descendants.getLength(); j++) {
                        ((Element)descendants.item(j)).setPrefix("data");
                    }
                }
                
                metadata.appendChild(importedNode);
            }
        }
    }
    
    private static byte[] generateBesluitXml(BesluitProcessor.AnalyseData data, RegelingMetadata regelingMetadata) throws Exception {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        docFactory.setNamespaceAware(true);  // Enable namespace support
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document doc = docBuilder.newDocument();
        
        // Root element: AanleveringBesluit
        Element rootElement = doc.createElement("AanleveringBesluit");
        rootElement.setAttribute("xmlns", "https://standaarden.overheid.nl/lvbb/stop/aanlevering/");
        rootElement.setAttribute("xmlns:data", "https://standaarden.overheid.nl/stop/imop/data/");
        rootElement.setAttribute("xmlns:tekst", "https://standaarden.overheid.nl/stop/imop/tekst/");
        rootElement.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
        rootElement.setAttribute("schemaversie", "1.2.0");
        doc.appendChild(rootElement);
        
        // BesluitVersie
        Element besluitVersie = doc.createElement("BesluitVersie");
        rootElement.appendChild(besluitVersie);
        
        // ExpressionIdentificatie
        Element expressionId = doc.createElementNS(STOP_DATA_NS, "data:ExpressionIdentificatie");
        besluitVersie.appendChild(expressionId);
        
        // Update FRBRWork en FRBRExpression met /akn/nl/bill
        String frbrWork = data.frbrWork.replace("/akn/nl/act", "/akn/nl/bill").replaceAll("/intrekking$", "_intrekking");
        
        // Gebruik de FRBRWork als basis voor de FRBRExpression en voeg alleen het expressiedeel toe
        String frbrExpression = frbrWork + "/nld@2023-11-15;2";
        
        addElement(doc, expressionId, "data:FRBRWork", frbrWork);
        addElement(doc, expressionId, "data:FRBRExpression", frbrExpression);
        addElement(doc, expressionId, "data:soortWork", "/join/id/stop/work_003");
        
        // BesluitMetadata
        Element metadata = doc.createElementNS(STOP_DATA_NS, "data:BesluitMetadata");
        metadata.setAttribute("schemaversie", "1.3.0");
        besluitVersie.appendChild(metadata);
        
        // Debug output
        System.out.println("Generating BesluitMetadata with:");
        System.out.println("soortBestuursorgaan: " + regelingMetadata.soortBestuursorgaan);
        System.out.println("onderwerpen: " + regelingMetadata.onderwerpen);
        System.out.println("overigeMetadata: " + regelingMetadata.overigeMetadata);
        
        // Process each metadata element from the original RegelingMetadata
        for (Map.Entry<String, String> entry : regelingMetadata.overigeMetadata.entrySet()) {
            String key = entry.getKey();
            String xmlContent = entry.getValue();
            
            // Skip soortRegeling
            if ("soortRegeling".equals(key)) continue;
            
            try {
                // Wrap the content in a root element to make it valid XML
                String wrappedXml = "<wrapper xmlns:data=\"" + STOP_DATA_NS + "\">" + xmlContent + "</wrapper>";
                
                // Create a new document builder for parsing
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                DocumentBuilder builder = factory.newDocumentBuilder();
                
                // Parse the wrapped content
                Document tempDoc = builder.parse(new ByteArrayInputStream(wrappedXml.getBytes("UTF-8")));
                
                // Get the first child of the wrapper (our actual content)
                NodeList children = tempDoc.getDocumentElement().getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    Node child = children.item(i);
                    if (child.getNodeType() == Node.ELEMENT_NODE) {
                        Node importedNode = doc.importNode(child, true);
                        
                        // Special handling for citeerTitel and officieleTitel
                        if ("heeftCiteertitelInformatie".equals(key)) {
                            NodeList citeertitelNodes = ((Element)importedNode).getElementsByTagNameNS(STOP_DATA_NS, "citeertitel");
                            for (int j = 0; j < citeertitelNodes.getLength(); j++) {
                                Node citeertitel = citeertitelNodes.item(j);
                                citeertitel.setTextContent(citeertitel.getTextContent() + " intrekking");
                            }
                        } else if ("officieleTitel".equals(key)) {
                            importedNode.setTextContent(importedNode.getTextContent() + " intrekking");
                        }
                        
                        // Set the correct namespace prefix
                        if (importedNode.getNamespaceURI() != null) {
                            ((Element)importedNode).setPrefix("data");
                            NodeList descendants = ((Element)importedNode).getElementsByTagNameNS(STOP_DATA_NS, "*");
                            for (int j = 0; j < descendants.getLength(); j++) {
                                ((Element)descendants.item(j)).setPrefix("data");
                            }
                        }
                        
                        metadata.appendChild(importedNode);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error processing metadata element " + key + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        // Add soortProcedure if not present
        if (!regelingMetadata.overigeMetadata.containsKey("soortProcedure")) {
            Element soortProcedure = doc.createElementNS(STOP_DATA_NS, "data:soortProcedure");
            soortProcedure.setTextContent("/join/id/stop/proceduretype_definitief");
            metadata.appendChild(soortProcedure);
        }
        
        // Datum van morgen berekenen voor bekendOp
        LocalDate datumMorgen = LocalDate.now().plusDays(1);
        String datumMorgenString = datumMorgen.format(DateTimeFormatter.ISO_DATE);

        // Procedureverloop
        Element procedureverloop = doc.createElementNS(STOP_DATA_NS, "data:Procedureverloop");
        besluitVersie.appendChild(procedureverloop);
        
        Element bekendOp = doc.createElementNS(STOP_DATA_NS, "data:bekendOp");
        bekendOp.setTextContent(datumMorgenString);
        procedureverloop.appendChild(bekendOp);
        
        Element procedurestappen = doc.createElementNS(STOP_DATA_NS, "data:procedurestappen");
        procedureverloop.appendChild(procedurestappen);
        
        Element procedurestap = doc.createElementNS(STOP_DATA_NS, "data:Procedurestap");
        procedurestappen.appendChild(procedurestap);
        
        Element soortStap = doc.createElementNS(STOP_DATA_NS, "data:soortStap");
        soortStap.setTextContent("/join/id/stop/procedure/stap_003");  // Aangepast naar de juiste waarde
        procedurestap.appendChild(soortStap);
        
        Element voltooidOp = doc.createElementNS(STOP_DATA_NS, "data:voltooidOp");
        voltooidOp.setTextContent(datumMorgenString);
        procedurestap.appendChild(voltooidOp);
        
        // ConsolidatieInformatie (nu op het juiste niveau)
        Element consolidatieInfo = doc.createElement("data:ConsolidatieInformatie");
        besluitVersie.appendChild(consolidatieInfo);
        
        Element intrekkingen = doc.createElement("data:Intrekkingen");
        consolidatieInfo.appendChild(intrekkingen);
        
        Element intrekking = doc.createElement("data:Intrekking");
        intrekkingen.appendChild(intrekking);
        
        Element doelen = doc.createElement("data:doelen");
        intrekking.appendChild(doelen);
        
        Element doel = doc.createElement("data:doel");
        // Gebruik hetzelfde doel als in het manifest-OW
        String doelId = generateDoelId(data.bevoegdGezag);
        doel.setTextContent(doelId);
        doelen.appendChild(doel);
        
        Element instrument = doc.createElement("data:instrument");
        instrument.setTextContent(data.frbrWork);
        intrekking.appendChild(instrument);
        
        Element eId = doc.createElement("data:eId");
        eId.setTextContent("art_I");
        intrekking.appendChild(eId);

        // Tijdstempels toevoegen
        Element tijdstempels = doc.createElement("data:Tijdstempels");
        consolidatieInfo.appendChild(tijdstempels);
        
        Element tijdstempel = doc.createElement("data:Tijdstempel");
        tijdstempels.appendChild(tijdstempel);
        
        Element tijdstempelDoel = doc.createElement("data:doel");
        tijdstempelDoel.setTextContent(doelId);
        tijdstempel.appendChild(tijdstempelDoel);
        
        Element soortTijdstempel = doc.createElement("data:soortTijdstempel");
        soortTijdstempel.setTextContent("juridischWerkendVanaf");
        tijdstempel.appendChild(soortTijdstempel);
        
        Element datum = doc.createElement("data:datum");
        datum.setTextContent(datumMorgenString);
        tijdstempel.appendChild(datum);
        
        Element tijdstempelEId = doc.createElement("data:eId");
        tijdstempelEId.setTextContent("art_I");
        tijdstempel.appendChild(tijdstempelEId);
        
        // BesluitCompact
        Element besluitCompact = doc.createElementNS(STOP_TEKST_NS, "tekst:BesluitCompact");
        besluitVersie.appendChild(besluitCompact);
        
        // RegelingOpschrift
        Element regelingOpschrift = doc.createElementNS(STOP_TEKST_NS, "tekst:RegelingOpschrift");
        regelingOpschrift.setAttribute("eId", "longTitle");
        regelingOpschrift.setAttribute("wId", "__longTitle");
        besluitCompact.appendChild(regelingOpschrift);
        
        Element al = doc.createElementNS(STOP_TEKST_NS, "tekst:Al");
        al.setTextContent("Intrekkingsbesluit voor " + data.frbrWork);
        regelingOpschrift.appendChild(al);
        
        // Lichaam
        Element lichaam = doc.createElementNS(STOP_TEKST_NS, "tekst:Lichaam");
        lichaam.setAttribute("eId", "body");
        lichaam.setAttribute("wId", "body");
        besluitCompact.appendChild(lichaam);
        
        // Artikel I
        Element artikel = doc.createElementNS(STOP_TEKST_NS, "tekst:Artikel");
        artikel.setAttribute("eId", "art_I");
        artikel.setAttribute("wId", "__art_I");
        lichaam.appendChild(artikel);
        
        Element kop = doc.createElementNS(STOP_TEKST_NS, "tekst:Kop");
        artikel.appendChild(kop);
        
        Element label = doc.createElementNS(STOP_TEKST_NS, "tekst:Label");
        label.setTextContent("Artikel");
        kop.appendChild(label);
        
        Element nummer = doc.createElementNS(STOP_TEKST_NS, "tekst:Nummer");
        nummer.setTextContent("I");
        kop.appendChild(nummer);
        
        Element inhoud = doc.createElementNS(STOP_TEKST_NS, "tekst:Inhoud");
        artikel.appendChild(inhoud);
        
        Element alInhoud = doc.createElementNS(STOP_TEKST_NS, "tekst:Al");
        alInhoud.setTextContent("De regeling treedt uit werking per " + datumMorgenString);
        inhoud.appendChild(alInhoud);
        
        // Transform naar bytes
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "3");
        
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(doc), new StreamResult(output));
        
        return output.toByteArray();
    }
    
    private static void addElement(Document doc, Element parent, String name, String value) {
        Element element = doc.createElement(name);
        element.setTextContent(value);
        parent.appendChild(element);
    }
    
    private static byte[] createOpdrachtXml(BesluitProcessor.AnalyseData data, boolean isValidation) throws Exception {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document doc = docBuilder.newDocument();
        
        // Root element
        Element rootElement = doc.createElement(isValidation ? "validatieOpdracht" : "publicatieOpdracht");
        rootElement.setAttribute("xmlns", "http://www.overheid.nl/2017/lvbb");
        doc.appendChild(rootElement);
        
        // Bereken datum en tijd voor idLevering
        LocalDateTime now = LocalDateTime.now();
        String datum = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String tijd = now.format(DateTimeFormatter.ofPattern("HHmmss"));
        
        // IdLevering
        Element idLevering = doc.createElement("idLevering");
        String leveringId = String.format("OTST_%s_intr_%s_%s_%s",
            isValidation ? "val" : "pub",
            data.bevoegdGezag,
            datum,
            tijd);
        idLevering.setTextContent(leveringId);
        rootElement.appendChild(idLevering);
        
        // IdBevoegdGezag - gebruik de vaste waarde
        Element idBevoegdGezag = doc.createElement("idBevoegdGezag");
        idBevoegdGezag.setTextContent("00000001003214345000");
        rootElement.appendChild(idBevoegdGezag);
        
        // IdAanleveraar - gebruik de vaste waarde
        Element idAanleveraar = doc.createElement("idAanleveraar");
        idAanleveraar.setTextContent("00000001003214345000");
        rootElement.appendChild(idAanleveraar);
        
        // Publicatie element toevoegen met de juiste bestandsnaam
        Element publicatie = doc.createElement("publicatie");
        publicatie.setTextContent("intrekkingsbesluit.xml");
        rootElement.appendChild(publicatie);
        
        // Bereken eerstvolgende werkdag
        LocalDate datumBekend = getNextWorkingDay(LocalDate.now());
        
        // DatumBekendmaking
        Element datumBekendmaking = doc.createElement("datumBekendmaking");
        datumBekendmaking.setTextContent(datumBekend.format(DateTimeFormatter.ISO_DATE));
        rootElement.appendChild(datumBekendmaking);
        
        // Transform naar bytes
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
        
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(doc), new StreamResult(output));
        
        return output.toByteArray();
    }
    
    private static LocalDate getNextWorkingDay(LocalDate date) {
        LocalDate nextDay = date.plusDays(1);
        while (nextDay.getDayOfWeek().getValue() > 5) { // 5 = Friday
            nextDay = nextDay.plusDays(1);
        }
        return nextDay;
    }

    private static String generateDoelId(String bevoegdGezag) {
        LocalDateTime now = LocalDateTime.now();
        String datum = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String tijd = now.format(DateTimeFormatter.ofPattern("HHmmss"));
        return String.format("/join/id/proces/%s/%s/Intrekking_%s_%s", 
            bevoegdGezag, 
            now.getYear(),
            datum,
            tijd);
    }
} 