package nl.omgevingswet;

import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

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
    
    public static byte[] generateManifest(ZipFile zipFile) throws Exception {
        StringBuilder manifest = new StringBuilder();
        manifest.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        manifest.append("<manifest xmlns=\"http://www.overheid.nl/2017/lvbb\">\n");
        
        // Verzamel alle bestanden uit de ZIP
        List<String> files = new ArrayList<>();
        zipFile.stream()
               .filter(entry -> !entry.getName().endsWith("/")) // Skip directories
               .forEach(entry -> files.add(entry.getName()));
        
        // Sorteer de bestanden voor consistente output
        files.sort(String::compareTo);
        
        // Voeg elk bestand toe aan het manifest
        for (String file : files) {
            manifest.append("    <bestand>\n");
            manifest.append("        <bestandsnaam>").append(file).append("</bestandsnaam>\n");
            
            // Bepaal de content type op basis van de extensie
            String extension = file.substring(file.lastIndexOf('.') + 1).toLowerCase();
            String contentType = CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");
            
            manifest.append("        <contentType>").append(contentType).append("</contentType>\n");
            manifest.append("    </bestand>\n");
        }
        
        manifest.append("</manifest>");
        
        return manifest.toString().getBytes("UTF-8");
    }
} 