package nl.omgevingswet;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipAnalyzer {
    public static void analyzeZip(String zipPath) {
        try (ZipFile zipFile = new ZipFile(zipPath)) {
            System.out.println("Analyzing ZIP file: " + zipPath);
            System.out.println("Number of entries: " + zipFile.size());
            System.out.println("\nEntries:");
            zipFile.stream().forEach(entry -> {
                System.out.println("- " + entry.getName() + " (size: " + entry.getSize() + " bytes)");
            });
        } catch (IOException e) {
            System.err.println("Error analyzing ZIP file: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        analyzeZip("InputVoorbeeld/response.zip");
        System.out.println("\nAnalyzing output ZIP:");
        analyzeZip("OutputVoorbeeld/output.zip");
    }
} 