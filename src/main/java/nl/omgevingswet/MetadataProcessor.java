package nl.omgevingswet;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.*;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.io.File;

public class MetadataProcessor {
    
    public static class GMLInfo {
        public int count;
        public long totalSize;
        public List<String> fileNames;
        
        public GMLInfo() {
            count = 0;
            totalSize = 0;
            fileNames = new ArrayList<>();
        }
    }
    
    public static Map<String, String> processMetadata(byte[] xmlContent) {
        Map<String, String> metadata = new HashMap<>();
        
        try {
            System.out.println("Processing Metadata.xml content:");
            System.out.println(new String(xmlContent));
            
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(xmlContent));

            // Setup XPath
            XPath xpath = XPathFactory.newInstance().newXPath();
            xpath.setNamespaceContext(new javax.xml.namespace.NamespaceContext() {
                @Override
                public String getNamespaceURI(String prefix) {
                    switch (prefix) {
                        case "data":
                            return "https://standaarden.overheid.nl/stop/imop/data/";
                        default:
                            return javax.xml.XMLConstants.NULL_NS_URI;
                    }
                }

                @Override
                public String getPrefix(String uri) {
                    return null;
                }

                @Override
                public java.util.Iterator<String> getPrefixes(String uri) {
                    return null;
                }
            });

            // Zoek naar het maker element en citeerTitel
            processMetadataXml(document, xpath, metadata);
            System.out.println("Extracted metadata from Metadata.xml: " + metadata);
            
        } catch (Exception e) {
            System.err.println("Error processing metadata: " + e.getMessage());
            e.printStackTrace();
        }
        
        return metadata;
    }

    public static Map<String, String> processIdentificatie(byte[] xmlContent) {
        Map<String, String> metadata = new HashMap<>();
        
        try {
            System.out.println("Processing Identificatie.xml content:");
            System.out.println(new String(xmlContent));
            
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(xmlContent));

            // Setup XPath
            XPath xpath = XPathFactory.newInstance().newXPath();
            xpath.setNamespaceContext(new javax.xml.namespace.NamespaceContext() {
                @Override
                public String getNamespaceURI(String prefix) {
                    switch (prefix) {
                        case "data":
                            return "https://standaarden.overheid.nl/stop/imop/data/";
                        default:
                            return javax.xml.XMLConstants.NULL_NS_URI;
                    }
                }

                @Override
                public String getPrefix(String uri) {
                    return null;
                }

                @Override
                public java.util.Iterator<String> getPrefixes(String uri) {
                    return null;
                }
            });
            
            // Zoek FRBRWork
            String workValue = xpath.evaluate("//data:FRBRWork", document);
            if (workValue != null && !workValue.trim().isEmpty()) {
                metadata.put("FRBRWork", workValue.trim());
            }

            // Zoek FRBRExpression
            String expressionValue = xpath.evaluate("//data:FRBRExpression", document);
            if (expressionValue != null && !expressionValue.trim().isEmpty()) {
                metadata.put("FRBRExpression", expressionValue.trim());
            }

            System.out.println("Extracted metadata from Identificatie.xml: " + metadata);

        } catch (Exception e) {
            System.err.println("Error processing identificatie: " + e.getMessage());
            e.printStackTrace();
        }
        
        return metadata;
    }

    public static GMLInfo analyzeGMLFiles(ZipFile zipFile) {
        GMLInfo info = new GMLInfo();
        
        try {
            zipFile.stream()
                   .filter(entry -> entry.getName().toLowerCase().endsWith(".gml"))
                   .forEach(entry -> {
                       info.count++;
                       info.totalSize += entry.getSize();
                       info.fileNames.add(new File(entry.getName()).getName());
                   });
            
            System.out.println("Found " + info.count + " GML files with total size: " + info.totalSize + " bytes");
            info.fileNames.forEach(name -> System.out.println("GML file: " + name));
        } catch (Exception e) {
            System.err.println("Error analyzing GML files: " + e.getMessage());
            e.printStackTrace();
        }
        
        return info;
    }

    private static void processMetadataXml(Document document, XPath xpath, Map<String, String> metadata) throws XPathExpressionException {
        // Zoek maker element
        NodeList makerNodes = (NodeList) xpath.evaluate("//data:maker", document, XPathConstants.NODESET);
        System.out.println("Found " + makerNodes.getLength() + " maker nodes");
        
        if (makerNodes.getLength() > 0) {
            Node makerNode = makerNodes.item(0);
            String makerValue = makerNode.getTextContent();
            System.out.println("Maker value: " + makerValue);
            
            if (makerValue != null && !makerValue.isEmpty()) {
                String[] parts = makerValue.split("/");
                if (parts.length >= 4) {
                    String type = parts[parts.length - 2];
                    String code = parts[parts.length - 1];
                    if (isValidAuthorityType(type)) {
                        metadata.put("bevoegdgezag-code", code);
                        metadata.put("bevoegdgezag-type", type);
                    }
                }
            }
        }

        // Zoek citeerTitel
        String citeerTitel = xpath.evaluate("//data:CiteertitelInformatie/data:citeertitel", document);
        System.out.println("CiteerTitel value: " + citeerTitel);
        if (citeerTitel != null && !citeerTitel.trim().isEmpty()) {
            metadata.put("citeerTitel", citeerTitel.trim());
        }
    }
    
    private static boolean isValidAuthorityType(String type) {
        return type.equals("gemeente") || 
               type.equals("provincie") || 
               type.equals("ministerie") || 
               type.equals("waterschap");
    }
} 