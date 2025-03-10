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
        "Regeling/Metadata.xml",
        "Regeling/MomentOpname.xml"
    };

    private static final String STOP_DATA_NS = "https://standaarden.overheid.nl/stop/imop/data/";
    
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
        ZipEntry entry = zipFile.getEntry(identificatiePath);
        if (entry != null) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(zipFile.getInputStream(entry));
            
            // Zoek het FRBRExpression element
            NodeList frbrNodes = doc.getElementsByTagNameNS(STOP_DATA_NS, "FRBRExpression");
            if (frbrNodes.getLength() > 0) {
                return frbrNodes.item(0).getTextContent();
            }
        }
        return null;
    }
    
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
            NodeList makerNodes = metadataDoc.getElementsByTagNameNS(STOP_DATA_NS, "maker");
            if (makerNodes.getLength() > 0) {
                String makerValue = makerNodes.item(0).getTextContent();
                // Extract code from format like "/tooi/id/gemeente/gm0297"
                String[] parts = makerValue.split("/");
                if (parts.length >= 4) {
                    bevoegdGezagCode = parts[parts.length - 1]; // Alleen de laatste code, bijvoorbeeld "gm0297"
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
                // Sla heeftCiteertitelInformatie over
                if (!element.getLocalName().equals("heeftCiteertitelInformatie")) {
                    besluitMetadata.appendChild(element);
                }
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
        
        // Haal het doel op uit Regeling/Momentopname.xml
        String doelValue = getDoelFromMomentOpname(zipFile, "Regeling");
        System.out.println("Opgehaald doel uit Momentopname.xml: " + doelValue); // Debug output
        
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
        doelRegeling.setTextContent(doelValue);
        doelenRegeling.appendChild(doelRegeling);
        
        // Instrumentversie onder BeoogdeRegeling
        Element instrumentversieRegeling = doc.createElementNS(STOP_DATA_NS, "instrumentversie");
        String frbrRegelingValue = getFRBRExpressionFromIdentificatie(zipFile, "Regeling");
        if (frbrRegelingValue != null) {
            instrumentversieRegeling.setTextContent(frbrRegelingValue);
        }
        beoogdeRegeling.appendChild(instrumentversieRegeling);
        
        // eId onder BeoogdeRegeling
        Element eIdRegeling = doc.createElementNS(STOP_DATA_NS, "eId");
        eIdRegeling.setTextContent("art_besluit1");
        beoogdeRegeling.appendChild(eIdRegeling);
        
        // BeoogdInformatieObject voor elke IO
        // Verzamel alle IO folders
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
        
        // Maak een BeoogdInformatieObject voor elke IO
        for (String ioFolder : ioFolders) {
            Element beoogdInformatieObject = doc.createElementNS(STOP_DATA_NS, "BeoogdInformatieObject");
            beoogdeRegelgeving.appendChild(beoogdInformatieObject);
            
            // Doelen onder BeoogdInformatieObject
            Element doelenIO = doc.createElementNS(STOP_DATA_NS, "doelen");
            beoogdInformatieObject.appendChild(doelenIO);
            
            Element doelIO = doc.createElementNS(STOP_DATA_NS, "doel");
            doelIO.setTextContent(doelValue);
            doelenIO.appendChild(doelIO);
            
            // Instrumentversie onder BeoogdInformatieObject
            Element instrumentversieIO = doc.createElementNS(STOP_DATA_NS, "instrumentversie");
            String frbrIOValue = getFRBRExpressionFromIdentificatie(zipFile, ioFolder);
            if (frbrIOValue != null) {
                instrumentversieIO.setTextContent(frbrIOValue);
            }
            beoogdInformatieObject.appendChild(instrumentversieIO);
            
            // eId onder BeoogdInformatieObject
            Element eIdIO = doc.createElementNS(STOP_DATA_NS, "eId");
            eIdIO.setTextContent("art_besluit1");
            beoogdInformatieObject.appendChild(eIdIO);
        }
        
        // Tijdstempels
        Element tijdstempels = doc.createElementNS(STOP_DATA_NS, "Tijdstempels");
        consolidatieInformatie.appendChild(tijdstempels);
        
        Element tijdstempel = doc.createElementNS(STOP_DATA_NS, "Tijdstempel");
        tijdstempels.appendChild(tijdstempel);
        
        // Doel onder Tijdstempel
        Element doelTijdstempel = doc.createElementNS(STOP_DATA_NS, "doel");
        doelTijdstempel.setTextContent(doelValue);
        tijdstempel.appendChild(doelTijdstempel);
        
        // SoortTijdstempel
        Element soortTijdstempel = doc.createElementNS(STOP_DATA_NS, "soortTijdstempel");
        soortTijdstempel.setTextContent("juridischWerkendVanaf");
        tijdstempel.appendChild(soortTijdstempel);
        
        // Datum
        Element datumTijdstempel = doc.createElementNS(STOP_DATA_NS, "datum");
        datumTijdstempel.setTextContent(datumVanMorgen);
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
        wijzigArtikel.setAttribute("wId", bevoegdGezagCode + "__art_besluit1");
        
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
        artikel.setAttribute("wId", bevoegdGezagCode + "__art_besluit2");
        
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
        alInhoud.setTextContent("Dit besluit treedt in werking per " + datumVanMorgen);
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
        wijzigBijlage.setAttribute("wId", bevoegdGezagCode + "__cmp_besluit");
        
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