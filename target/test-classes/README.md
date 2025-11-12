# Test Resources

Deze directory bevat test data voor de verschillende functionaliteiten van de Omgevingswet Test Suite Tool.

## Directory Structuur

```
src/test/resources/
├── publicatie/
│   ├── input/       - ZIP bestanden voor publicatie tests
│   └── expected/    - Verwachte output bestanden
├── validatie/
│   ├── input/       - ZIP bestanden voor validatie tests
│   └── expected/    - Verwachte output bestanden
├── intrekking/
│   ├── input/       - ZIP bestanden voor intrekking tests
│   └── expected/    - Verwachte output bestanden
├── doorlevering/
│   ├── input/       - ZIP bestanden voor doorlevering tests
│   └── expected/    - Verwachte output bestanden
└── edge-cases/
    ├── invalid/     - Ongeldige ZIP bestanden voor error handling tests
    └── minimal/     - Minimale ZIP bestanden om edge cases te testen

```

## Gebruik

### 1. Test Data Toevoegen

Voor een nieuwe test case:

1. Maak een beschrijvende naam voor de test case (bijv. `gemeente-omgevingsplan-2024`)
2. Plaats het input ZIP bestand in de juiste `input/` directory
3. Plaats de verwachte output bestanden in de corresponderende `expected/` directory

**Naamgeving conventie:**
```
<type>_<beschrijving>_<versie>.zip

Voorbeelden:
- publicatie_gemeente_basisplan_v1.zip
- intrekking_provincie_visie_v2.zip
- validatie_waterschap_verordening_v1.zip
```

### 2. Test Data Gebruiken in Tests

```java
@Test
void testPublicatieGemeenteOmgevingsplan() throws Exception {
    // Laad input
    ZipFile input = TestUtils.loadTestZipFile(
        "publicatie/input/gemeente_omgevingsplan_2024.zip");
    
    // Verwerk
    BesluitProcessor.BesluitResult result = 
        BesluitProcessor.createBesluitXml(input, false);
    
    // Vergelijk met verwachte output
    byte[] expectedBesluit = Files.readAllBytes(
        Paths.get("src/test/resources/publicatie/expected/besluit.xml"));
    
    assertThat(result.besluitXml).isEqualTo(expectedBesluit);
}
```

### 3. Expected Output Bestanden

Voor elke test case kunnen de volgende output bestanden worden verwacht:

**Publicatie/Validatie:**
- `besluit.xml` - Het gegenereerde besluit
- `opdracht.xml` - De gegenereerde opdracht
- `manifest.xml` - Het gegenereerde manifest

**Intrekking:**
- `intrekkingsbesluit.xml` - Het intrekkingsbesluit
- `opdracht.xml` - De intrekkingsopdracht
- `manifest.xml` - Het manifest
- `OW-bestanden/` - Gewijzigde OW bestanden met status "beëindigen"

**Doorlevering:**
- `besluit.xml` - Het doorlevering besluit
- `opdracht.xml` - De doorlevering opdracht
- Eventuele gewijzigde bestanden

## Test Scenarios

### Publicatie Tests

**Basis scenario's:**
- ✓ Gemeente omgevingsplan zonder informatieobjecten
- ✓ Gemeente omgevingsplan met GML informatieobjecten
- ✓ Gemeente omgevingsplan met PDF bijlagen
- ✓ Provincie omgevingsverordening
- ✓ Waterschap keur

**Edge cases:**
- ✓ Minimale ZIP met alleen vereiste bestanden
- ✓ Grote ZIP met veel informatieobjecten (performance test)
- ✓ ZIP met speciale karakters in bestandsnamen

### Validatie Tests

- ✓ Validatie opdracht voor publicatie
- ✓ Validatie opdracht voor intrekking
- ✓ Validatie met validatiefouten

### Intrekking Tests

**Basis scenario's:**
- ✓ Intrekking van regeling zonder informatieobjecten
- ✓ Intrekking van regeling met informatieobjecten
- ✓ Intrekking met OW-bestanden aanpassingen

**Edge cases:**
- ✓ Intrekking van reeds ingetrokken regeling (foutafhandeling)

### Doorlevering Tests

- ✓ Doorlevering naar LVBB
- ✓ Doorlevering met wijzigingen

### Error Handling Tests (edge-cases/invalid/)

**Ongeldige ZIP bestanden:**
- ✗ ZIP zonder Regeling/Identificatie.xml
- ✗ ZIP zonder Regeling/Metadata.xml
- ✗ ZIP met corrupte XML bestanden
- ✗ Lege ZIP bestand
- ✗ ZIP met ontbrekende vereiste velden

**Verwachting:** Deze tests moeten falen met duidelijke error berichten

## Test Data Onderhoud

### Regels

1. **Geen grote bestanden committen** - Houd ZIP bestanden onder 5MB
2. **Gebruik realistische data** - Maar anonimiseer gevoelige informatie
3. **Documenteer test cases** - Maak een beschrijving in de test zelf
4. **Update expected output** - Als de code wijzigt, update de verwachte output

### Test Data Genereren

Voor het genereren van nieuwe test data:

```bash
# Gebruik de applicatie om output te genereren
mvn javafx:run

# Of gebruik de command line tools
java -jar target/OmgevingswetTestSuiteTool-1.0-SNAPSHOT.jar \
    --input InputVoorbeeld/test.zip \
    --output src/test/resources/publicatie/expected/
```

### Versie Controle

**Commit:**
- ✓ Kleine test ZIP bestanden (< 5MB)
- ✓ Expected XML output bestanden
- ✓ README en documentatie

**NIET committen:**
- ✗ Grote test bestanden (> 5MB)
- ✗ Tijdelijke testdata
- ✗ Gegenereerde output van lokale tests

**Gebruik .gitignore:**
```
# In src/test/resources/.gitignore
**/temp/
**/output/
**/*.large.zip
```

## Troubleshooting

### Test Faalt: "FileNotFoundException"

**Probleem:** Test bestand niet gevonden

**Oplossing:**
1. Controleer dat het bestand in de juiste directory staat
2. Controleer de bestandsnaam (let op hoofdletters!)
3. Run `mvn clean test` om resources opnieuw te laden

### Test Faalt: "AssertionError - output komt niet overeen"

**Probleem:** Gegenereerde output wijkt af van verwachte output

**Oplossing:**
1. Bekijk het verschil tussen actual en expected
2. Is de wijziging correct? → Update expected output
3. Is het een bug? → Fix de code

### Resources niet gevonden tijdens test

**Probleem:** `getResourceAsStream()` retourneert `null`

**Oplossing:**
```java
// Gebruik absolute path vanaf src/test/resources
InputStream is = getClass().getClassLoader()
    .getResourceAsStream("publicatie/input/test.zip");

// Of gebruik TestUtils helper
ZipFile zip = TestUtils.loadTestZipFile(
    "publicatie/input/test.zip");
```

## Voorbeelden

### Minimale Test Case

Plaats in `edge-cases/minimal/input/minimal.zip`:
```
minimal.zip
├── Regeling/
│   ├── Identificatie.xml    (minimale identificatie)
│   ├── Metadata.xml          (minimale metadata)
│   ├── Momentopname.xml      (minimaal)
│   └── Tekst.xml             (minimale tekst)
└── manifest.xml
```

### Complete Test Case

Plaats in `publicatie/input/volledig.zip`:
```
volledig.zip
├── Regeling/
│   ├── Identificatie.xml
│   ├── Metadata.xml
│   ├── VersieMetadata.xml
│   ├── Momentopname.xml
│   └── Tekst.xml
├── IO-xxxx/
│   ├── Identificatie.xml
│   ├── Metadata.xml
│   ├── VersieMetadata.xml
│   ├── Momentopname.xml
│   └── bestand.gml
├── OW-bestanden/
│   ├── regelteksten.xml
│   ├── gebieden.xml
│   └── manifest-ow.xml
└── manifest.xml
```

## Contact

Voor vragen over test data structuur, zie `TESTING.md` of vraag het team.


