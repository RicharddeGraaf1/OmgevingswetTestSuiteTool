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

public class BesluitProcessor {
    
    private static final String[] REGELING_FILES = {
        "Regeling/Identificatie.xml",
        "Regeling/VersieMetadata.xml",
        "Regeling/Metadata.xml"
    };

    private static final String STOP_DATA_NS = "https://standaarden.overheid.nl/stop/imop/data/";
    
    public static byte[] createBesluitXml(ZipFile zipFile) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);  // Enable namespace support
        DocumentBuilder builder = factory.newDocumentBuilder();
        
        // Maak het root document
        Document doc = builder.newDocument();
        Element root = doc.createElementNS(STOP_DATA_NS, "AanleveringBesluit");
        root.setAttribute("xmlns:data", STOP_DATA_NS);
        doc.appendChild(root);
        
        // Voeg BesluitVersie toe
        Element besluitVersie = doc.createElementNS(STOP_DATA_NS, "BesluitVersie");
        root.appendChild(besluitVersie);
        
        // Haal bevoegd gezag code en metadata op uit Metadata.xml
        String bevoegdGezagCode = "";
        Document metadataDoc = null;
        ZipEntry metadataEntry = zipFile.getEntry("Regeling/Metadata.xml");
        if (metadataEntry != null) {
            metadataDoc = builder.parse(zipFile.getInputStream(metadataEntry));
            NodeList makerNodes = metadataDoc.getElementsByTagName("maker");
            if (makerNodes.getLength() > 0) {
                String makerValue = makerNodes.item(0).getTextContent();
                // Extract code from format like "/tooi/id/gemeente/gm0297"
                String[] parts = makerValue.split("/");
                if (parts.length > 0) {
                    bevoegdGezagCode = parts[parts.length - 1];
                }
            }
        }
        
        // Genereer datum/tijd onderdelen
        LocalDateTime now = LocalDateTime.now();
        String huidigJaartal = String.valueOf(now.getYear());
        String datumTijd = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String datum = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        
        // 1. ExpressionIdentificatie
        Element expressionId = doc.createElementNS(STOP_DATA_NS, "ExpressionIdentificatie");
        besluitVersie.appendChild(expressionId);
        
        Element frbWork = doc.createElementNS(STOP_DATA_NS, "FRBRWork");
        frbWork.setTextContent(String.format("/akn/nl/bill/%s/%s/OTSTgegenereerd%s", 
            bevoegdGezagCode, huidigJaartal, datumTijd));
        expressionId.appendChild(frbWork);
        
        Element frbExpr = doc.createElementNS(STOP_DATA_NS, "FRBRExpression");
        frbExpr.setTextContent(String.format("/akn/nl/bill/%s/%s/OTSTgegenereerd%s/nld@%s;1", 
            bevoegdGezagCode, huidigJaartal, datumTijd, datum));
        expressionId.appendChild(frbExpr);
        
        Element soortWork = doc.createElementNS(STOP_DATA_NS, "soortWork");
        soortWork.setTextContent("/join/id/stop/work_003");
        expressionId.appendChild(soortWork);
        
        // 2. BesluitMetadata
        Element besluitMetadata = doc.createElementNS(STOP_DATA_NS, "BesluitMetadata");
        besluitVersie.appendChild(besluitMetadata);
        
        if (metadataDoc != null) {
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
                        } else {
                            newElement.setTextContent(element.getTextContent());
                        }
                        
                        metadataElements.add(newElement);
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
                besluitMetadata.appendChild(element);
            }
        }
        
        // 3. Procedureverloop
        Element procedureverloop = doc.createElementNS(STOP_DATA_NS, "Procedureverloop");
        besluitVersie.appendChild(procedureverloop);
        
        // Bereken datum van morgen
        LocalDateTime tomorrow = now.plusDays(1);
        String datumVanMorgen = tomorrow.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        
        // Voeg bekendOp toe
        Element bekendOp = doc.createElementNS(STOP_DATA_NS, "bekendOp");
        bekendOp.setTextContent(datumVanMorgen);
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
        voltooidOp.setTextContent(datumVanMorgen);
        procedurestap.appendChild(voltooidOp);
        
        // 4. ConsolidatieInformatie
        Element consolidatieInformatie = doc.createElementNS(STOP_DATA_NS, "ConsolidatieInformatie");
        besluitVersie.appendChild(consolidatieInformatie);
        
        // 5. BesluitCompact
        Element besluitCompact = doc.createElementNS(STOP_DATA_NS, "BesluitCompact");
        besluitVersie.appendChild(besluitCompact);
        
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
                        Element newChild = doc.createElementNS(STOP_DATA_NS, childElement.getLocalName());
                        newChild.setTextContent(childElement.getTextContent());
                        newElement.appendChild(newChild);
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
} 