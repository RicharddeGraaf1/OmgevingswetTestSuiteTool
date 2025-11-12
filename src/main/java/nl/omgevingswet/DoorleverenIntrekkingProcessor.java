package nl.omgevingswet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipFile;
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
import java.util.Map;
import java.util.HashMap;

public class DoorleverenIntrekkingProcessor {
    public static class DoorleverenIntrekkingResult {
        public final byte[] besluitXml;
        public final byte[] opdrachtXml;
        public final Map<String, byte[]> modifiedFiles;

        public DoorleverenIntrekkingResult(byte[] besluitXml, byte[] opdrachtXml, Map<String, byte[]> modifiedFiles) {
            this.besluitXml = besluitXml;
            this.opdrachtXml = opdrachtXml;
            this.modifiedFiles = modifiedFiles;
        }
    }

    public static DoorleverenIntrekkingResult createDoorleveringIntrekkingXml(ZipFile sourceZip, boolean isValidation) throws Exception {
        // Haal de analyse data op
        BesluitProcessor.AnalyseData data = BesluitProcessor.analyseZip(sourceZip);
        
        // Maak een nieuwe XML document voor besluit.xml
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document besluitDoc = builder.newDocument();
        
        // Maak root element
        Element root = besluitDoc.createElement("besluit");
        root.setAttribute("xmlns", "http://www.omgevingswet.nl/ow/1.0");
        root.setAttribute("xmlns:xlink", "http://www.w3.org/1999/xlink");
        besluitDoc.appendChild(root);
        
        // Voeg identificatie toe
        Element identificatie = besluitDoc.createElement("identificatie");
        root.appendChild(identificatie);
        
        Element frbrWork = besluitDoc.createElement("frbrWork");
        frbrWork.setTextContent(data.frbrWork);
        identificatie.appendChild(frbrWork);
        
        Element frbrExpression = besluitDoc.createElement("frbrExpression");
        frbrExpression.setTextContent(data.frbrExpression);
        identificatie.appendChild(frbrExpression);
        
        // Voeg doel toe
        Element doel = besluitDoc.createElement("doel");
        doel.setTextContent(data.doel);
        root.appendChild(doel);
        
        // Voeg bevoegd gezag toe
        Element bevoegdGezag = besluitDoc.createElement("bevoegdGezag");
        bevoegdGezag.setTextContent(data.bevoegdGezag);
        root.appendChild(bevoegdGezag);
        
        // Voeg intrekking toe
        Element intrekking = besluitDoc.createElement("intrekking");
        root.appendChild(intrekking);
        
        // Voeg informatieobjecten toe
        Element informatieobjecten = besluitDoc.createElement("informatieobjecten");
        root.appendChild(informatieobjecten);
        
        for (BesluitProcessor.AnalyseData.InformatieObjectData io : data.informatieObjecten) {
            Element informatieobject = besluitDoc.createElement("informatieobject");
            informatieobjecten.appendChild(informatieobject);
            
            Element ioIdentificatie = besluitDoc.createElement("identificatie");
            informatieobject.appendChild(ioIdentificatie);
            
            Element ioFrbrWork = besluitDoc.createElement("frbrWork");
            ioFrbrWork.setTextContent(io.frbrWork);
            ioIdentificatie.appendChild(ioFrbrWork);
            
            Element ioFrbrExpression = besluitDoc.createElement("frbrExpression");
            ioFrbrExpression.setTextContent(io.frbrExpression);
            ioIdentificatie.appendChild(ioFrbrExpression);
            
            if (io.officieleTitel != null) {
                Element officieleTitel = besluitDoc.createElement("officieleTitel");
                officieleTitel.setTextContent(io.officieleTitel);
                informatieobject.appendChild(officieleTitel);
            }
        }
        
        // Maak een nieuwe XML document voor opdracht.xml
        Document opdrachtDoc = builder.newDocument();
        
        // Maak root element
        Element opdrachtRoot = opdrachtDoc.createElement("opdracht");
        opdrachtRoot.setAttribute("xmlns", "http://www.omgevingswet.nl/ow/1.0");
        opdrachtRoot.setAttribute("xmlns:xlink", "http://www.w3.org/1999/xlink");
        opdrachtDoc.appendChild(opdrachtRoot);
        
        // Voeg identificatie toe
        Element opdrachtIdentificatie = opdrachtDoc.createElement("identificatie");
        opdrachtRoot.appendChild(opdrachtIdentificatie);
        
        Element opdrachtFrbrWork = opdrachtDoc.createElement("frbrWork");
        opdrachtFrbrWork.setTextContent(data.frbrWork);
        opdrachtIdentificatie.appendChild(opdrachtFrbrWork);
        
        Element opdrachtFrbrExpression = opdrachtDoc.createElement("frbrExpression");
        opdrachtFrbrExpression.setTextContent(data.frbrExpression);
        opdrachtIdentificatie.appendChild(opdrachtFrbrExpression);
        
        // Voeg doel toe
        Element opdrachtDoel = opdrachtDoc.createElement("doel");
        opdrachtDoel.setTextContent(data.doel);
        opdrachtRoot.appendChild(opdrachtDoel);
        
        // Voeg bevoegd gezag toe
        Element opdrachtBevoegdGezag = opdrachtDoc.createElement("bevoegdGezag");
        opdrachtBevoegdGezag.setTextContent(data.bevoegdGezag);
        opdrachtRoot.appendChild(opdrachtBevoegdGezag);
        
        // Converteer beide documenten naar byte arrays
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        
        ByteArrayOutputStream besluitOutput = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(besluitDoc), new StreamResult(besluitOutput));
        
        ByteArrayOutputStream opdrachtOutput = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(opdrachtDoc), new StreamResult(opdrachtOutput));
        
        // Maak een map voor gewijzigde bestanden
        Map<String, byte[]> modifiedFiles = new HashMap<>();
        
        return new DoorleverenIntrekkingResult(
            besluitOutput.toByteArray(),
            opdrachtOutput.toByteArray(),
            modifiedFiles
        );
    }
} 