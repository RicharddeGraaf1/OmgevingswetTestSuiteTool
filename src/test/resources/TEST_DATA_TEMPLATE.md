# Test Data Template

## Nieuwe Test Case Toevoegen

### Stap 1: Bepaal de Test Category

Kies de juiste category:
- `publicatie/` - Voor publicatie opdrachten
- `validatie/` - Voor validatie opdrachten
- `intrekking/` - Voor intrekking opdrachten
- `doorlevering/` - Voor doorlevering opdrachten
- `edge-cases/invalid/` - Voor foutafhandeling tests
- `edge-cases/minimal/` - Voor minimale input tests

### Stap 2: Maak Input Bestand

Plaats je test ZIP bestand in: `<category>/input/<beschrijvende-naam>.zip`

**Naamgeving:**
```
<type>_<entiteit>_<scenario>_<versie>.zip

Voorbeelden:
✓ publicatie_gemeente_omgevingsplan_basis_v1.zip
✓ intrekking_provincie_visie_met_io_v2.zip
✓ validatie_waterschap_keur_minimaal_v1.zip
```

### Stap 3: Genereer Expected Output

1. Run de applicatie met je input:
   ```bash
   mvn javafx:run
   # Of via command line
   java -jar target/OmgevingswetTestSuiteTool-1.0-SNAPSHOT.jar
   ```

2. Verwerk het bestand en export de output

3. Kopieer de output naar: `<category>/expected/<beschrijvende-naam>/`

**Expected directory structuur:**
```
<category>/expected/<test-case-naam>/
├── besluit.xml          (of intrekkingsbesluit.xml)
├── opdracht.xml
├── manifest.xml
└── OW-bestanden/        (optioneel, voor intrekking)
    ├── regelteksten.xml
    └── ...
```

### Stap 4: Schrijf de Test

Maak een nieuwe test in de juiste test class:

```java
@Test
@DisplayName("Publicatie gemeente omgevingsplan basis scenario")
void testPublicatieGemeenteOmgevingsplanBasis() throws Exception {
    // Arrange
    String testCase = "publicatie_gemeente_omgevingsplan_basis_v1";
    ZipFile input = TestUtils.loadTestZipFile(
        "publicatie/input/" + testCase + ".zip");
    
    // Act
    BesluitProcessor.BesluitResult result = 
        BesluitProcessor.createBesluitXml(input, false);
    
    // Assert - Controleer dat output gegenereerd is
    assertThat(result).isNotNull();
    assertThat(result.besluitXml).isNotNull();
    assertThat(result.opdrachtXml).isNotNull();
    
    // Optioneel: Vergelijk met expected output
    Path expectedPath = Paths.get(
        "src/test/resources/publicatie/expected/" + testCase);
    
    if (Files.exists(expectedPath.resolve("besluit.xml"))) {
        byte[] expectedBesluit = Files.readAllBytes(
            expectedPath.resolve("besluit.xml"));
        
        // Parse en vergelijk (normaliseer verschillen in whitespace/datums)
        Document actualDoc = TestUtils.parseXmlBytes(result.besluitXml);
        Document expectedDoc = TestUtils.parseXmlBytes(expectedBesluit);
        
        // Vergelijk structuur (niet exacte content vanwege timestamps)
        assertThat(actualDoc.getDocumentElement().getLocalName())
            .isEqualTo(expectedDoc.getDocumentElement().getLocalName());
    }
    
    System.out.println("✓ Test case " + testCase + " geslaagd");
}
```

### Stap 5: Documenteer de Test Case

Maak een beschrijving in een `<test-case-naam>.md` bestand:

```markdown
# Test Case: Publicatie Gemeente Omgevingsplan Basis

## Doel
Test de basis publicatie functionaliteit voor een gemeente omgevingsplan zonder informatieobjecten.

## Input Karakteristieken
- Type: Gemeente omgevingsplan
- Bevoegd gezag: gm0344 (Gemeente Utrecht)
- Informatieobjecten: Geen
- OW-bestanden: Regelteksten, gebieden
- Bijzonderheden: Basis scenario zonder complexe features

## Verwachte Output
- besluit.xml met AanleveringBesluit root element
- opdracht.xml met publicatieOpdracht root element
- manifest.xml met alle bestanden

## Test Scenario
1. Laad input ZIP
2. Verwerk met BesluitProcessor
3. Controleer dat output gegenereerd is
4. Valideer structuur van besluit.xml
5. Valideer structuur van opdracht.xml

## Varianten
- Met informatieobjecten (GML)
- Met informatieobjecten (PDF)
- Met grote hoeveelheid OW-objecten
```

## Test Data Best Practices

### ✓ DO

1. **Gebruik realistische data**
   - Gebaseerd op echte use cases
   - Geanonimiseerd waar nodig

2. **Houd bestanden klein**
   - < 1MB voor reguliere tests
   - < 5MB voor performance tests
   - Gebruik Git LFS voor grotere bestanden

3. **Documenteer test cases**
   - Beschrijf wat er getest wordt
   - Leg bijzonderheden uit

4. **Versie beheer**
   - Gebruik versie nummers in bestandsnamen
   - Update expected output bij code wijzigingen

5. **Groepeer gerelateerde tests**
   - Maak subdirectories voor test suites
   - Gebruik consistente naamgeving

### ✗ DON'T

1. **Geen gevoelige data**
   - Geen echte persoonsnamen
   - Geen echte adressen
   - Geen echte organisatie details

2. **Geen grote binaire bestanden**
   - Comprimeer waar mogelijk
   - Gebruik minimale datasets

3. **Geen hardcoded datums**
   - Expected output met timestamps zal altijd falen
   - Normaliseer of negeer timestamps in assertions

4. **Geen duplicate data**
   - Hergebruik test bestanden waar mogelijk
   - Maak abstracte test cases

## Voorbeeld Test Suite

Voorbeeld van een complete test suite voor publicatie:

```
publicatie/
├── input/
│   ├── basis_gemeente_v1.zip
│   ├── basis_provincie_v1.zip
│   ├── met_gml_io_v1.zip
│   ├── met_pdf_io_v1.zip
│   └── performance_veel_objecten_v1.zip
└── expected/
    ├── basis_gemeente_v1/
    │   ├── besluit.xml
    │   ├── opdracht.xml
    │   └── manifest.xml
    ├── basis_provincie_v1/
    │   ├── besluit.xml
    │   ├── opdracht.xml
    │   └── manifest.xml
    └── ... (etc)
```

## Test Data Validatie

Voordat je test data commit:

```bash
# 1. Controleer bestandsgroottes
find src/test/resources -name "*.zip" -size +5M

# 2. Valideer XML bestanden
find src/test/resources -name "*.xml" -exec xmllint --noout {} \;

# 3. Run de tests
mvn test

# 4. Check wat gecommit wordt
git status
git diff --staged
```

## Hulp Nodig?

- Zie `README.md` voor directory structuur
- Zie `TESTING.md` voor test framework documentatie
- Vraag het team bij vragen over specifieke test cases


