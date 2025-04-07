package nl.omgevingswet;

import javax.xml.parsers.*;
import org.w3c.dom.*;
import java.util.zip.*;
import java.util.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import java.io.*;

public class ManifestProcessor {
    private static final Map<String, String> CONTENT_TYPES = new HashMap<>();
    
    static {
        // XML bestanden
        CONTENT_TYPES.put("xml", "application/xml");
        CONTENT_TYPES.put("gml", "application/gml+xml");
        
        // Afbeeldingen
        CONTENT_TYPES.put("jpg", "image/jpeg");
        CONTENT_TYPES.put("jpeg", "image/jpeg");
        CONTENT_TYPES.put("png", "image/png");
        
        // PDF
        CONTENT_TYPES.put("pdf", "application/pdf");
    }
    
    public static byte[] generateManifest(ZipFile sourceZip, Set<String> addedFiles, boolean isIntrekking) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        // Maak root element met namespace
        Element root = doc.createElement("manifest");
        root.setAttribute("xmlns", "http://www.overheid.nl/2017/lvbb");
        doc.appendChild(root);

        // Voeg alleen bestanden toe die daadwerkelijk in de resultaat ZIP zitten
        for (String fileName : addedFiles) {
            // Sla IO bestanden over bij intrekking
            if (isIntrekking && fileName.matches("IO-\\d+\\.xml")) {
                continue;
            }
            
            // Maak bestand element
            Element bestand = doc.createElement("bestand");
            
            // Voeg bestandsnaam toe
            Element bestandsnaam = doc.createElement("bestandsnaam");
            bestandsnaam.setTextContent(fileName);
            bestand.appendChild(bestandsnaam);
            
            // Bepaal en voeg contentType toe
            Element contentType = doc.createElement("contentType");
            String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
            contentType.setTextContent(CONTENT_TYPES.getOrDefault(extension, "application/octet-stream"));
            bestand.appendChild(contentType);
            
            root.appendChild(bestand);
        }

        // Converteer naar bytes
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(doc), new StreamResult(output));
        return output.toByteArray();
    }
} 