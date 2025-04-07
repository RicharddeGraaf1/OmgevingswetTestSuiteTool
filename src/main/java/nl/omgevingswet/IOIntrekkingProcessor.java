package nl.omgevingswet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Klasse voor het verwerken van intrekkingen van informatieobjecten.
 * Deze klasse is verantwoordelijk voor het genereren van de benodigde XML bestanden
 * voor het intrekken van informatieobjecten in het Omgevingswet systeem.
 */
public class IOIntrekkingProcessor {

    /**
     * Verwerkt de intrekking van informatieobjecten en genereert de benodigde XML bestanden.
     * 
     * @param sourceZip De bron ZIP met de informatieobjecten die ingetrokken moeten worden
     * @param targetZip De doel ZIP waar de gegenereerde bestanden in geplaatst worden
     * @param data De analyse data van de bron ZIP
     * @param addedFiles Set met bestanden die al toegevoegd zijn aan de doel ZIP
     * @param reportContent StringBuilder voor het rapport
     * @return Het aantal verwerkte informatieobjecten
     * @throws Exception Als er een fout optreedt tijdens het verwerken
     */
    public static int processIOIntrekkingen(ZipFile sourceZip, ZipOutputStream targetZip, 
                                           BesluitProcessor.AnalyseData data, 
                                           java.util.Set<String> addedFiles,
                                           StringBuilder reportContent) throws Exception {
        
        int ioFoldersProcessed = 0;
        
        // Verzamel alle IO-mappen die ingetrokken moeten worden
        List<String> ioNumbers = new ArrayList<>();
        for (ZipEntry entry : sourceZip.stream().collect(java.util.stream.Collectors.toList())) {
            String entryName = entry.getName();
            if (entryName.matches("IO-\\d+/.*")) {
                String ioNumber = entryName.substring(3, entryName.indexOf('/', 3));
                if (!ioNumbers.contains(ioNumber)) {
                    ioNumbers.add(ioNumber);
                }
            }
        }
        
        // Verwerk elke IO-map
        for (String ioNumber : ioNumbers) {
            // Zoek de IO data
            BesluitProcessor.AnalyseData.InformatieObjectData ioData = null;
            for (BesluitProcessor.AnalyseData.InformatieObjectData io : data.informatieObjecten) {
                if (io.folder.equals("IO-" + ioNumber)) {
                    ioData = io;
                    break;
                }
            }
            
            if (ioData != null) {
                // Genereer IO intrekking XML bestand
                String xmlFileName = "IO-" + ioNumber + "_intrekking.xml";
                if (!addedFiles.contains(xmlFileName)) {
                    byte[] ioXml = createIOIntrekkingXml(ioData, data.frbrWork);
                    ZipEntry newEntry = new ZipEntry(xmlFileName);
                    targetZip.putNextEntry(newEntry);
                    targetZip.write(ioXml);
                    targetZip.closeEntry();
                    addedFiles.add(xmlFileName);
                    reportContent.append("IO intrekking XML gegenereerd: ").append(xmlFileName).append("\n");
                    ioFoldersProcessed++;
                }
            }
        }
        
        // Update rapport
        if (ioFoldersProcessed > 0) {
            reportContent.append("Aantal verwerkte IO intrekkingen: ").append(ioFoldersProcessed).append("\n");
        }
        
        return ioFoldersProcessed;
    }
    
    /**
     * Genereert een XML bestand voor het intrekken van een informatieobject.
     * 
     * @param ioData De data van het informatieobject dat ingetrokken moet worden
     * @param frbrWork De FRBR Work ID van het besluit
     * @return De gegenereerde XML als byte array
     * @throws Exception Als er een fout optreedt tijdens het genereren
     */
    private static byte[] createIOIntrekkingXml(BesluitProcessor.AnalyseData.InformatieObjectData ioData, String frbrWork) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();
        
        // Maak het root element
        Element root = doc.createElement("intrekkingInformatieobject");
        root.setAttribute("xmlns", "http://www.omgevingswet.nl/ow/1.0");
        root.setAttribute("xmlns:xlink", "http://www.w3.org/1999/xlink");
        doc.appendChild(root);
        
        // Voeg de metadata toe
        Element metadata = doc.createElement("metadata");
        root.appendChild(metadata);
        
        // Voeg de identificatie toe
        Element identificatie = doc.createElement("identificatie");
        metadata.appendChild(identificatie);
        
        // Voeg de FRBR Work toe
        Element frbrWorkElement = doc.createElement("frbrWork");
        frbrWorkElement.setTextContent(frbrWork);
        identificatie.appendChild(frbrWorkElement);
        
        // Voeg de FRBR Expression toe
        Element frbrExpression = doc.createElement("frbrExpression");
        frbrExpression.setTextContent(ioData.frbrExpression);
        identificatie.appendChild(frbrExpression);
        
        // Voeg de eId toe
        Element eId = doc.createElement("eId");
        eId.setTextContent(ioData.extIoRefEId);
        identificatie.appendChild(eId);
        
        // Voeg de officiële titel toe als die beschikbaar is
        if (ioData.officieleTitel != null && !ioData.officieleTitel.isEmpty()) {
            Element officieleTitel = doc.createElement("officieleTitel");
            officieleTitel.setTextContent(ioData.officieleTitel);
            metadata.appendChild(officieleTitel);
        }
        
        // Voeg de intrekking datum toe (huidige datum)
        Element intrekkingDatum = doc.createElement("intrekkingDatum");
        java.time.LocalDate today = java.time.LocalDate.now();
        intrekkingDatum.setTextContent(today.toString());
        metadata.appendChild(intrekkingDatum);
        
        // Voeg de intrekking reden toe
        Element intrekkingReden = doc.createElement("intrekkingReden");
        intrekkingReden.setTextContent("Intrekking van informatieobject op basis van besluit");
        metadata.appendChild(intrekkingReden);
        
        // Converteer het document naar een byte array
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(doc), new StreamResult(outputStream));
        
        return outputStream.toByteArray();
    }
} 