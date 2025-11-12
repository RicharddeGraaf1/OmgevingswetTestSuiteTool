# Performance Validatie Tests

Deze directory bevat test resources voor performance/stress testing van de validatie pipeline.

## Structuur

```
performance-validatie/
├── input/
│   ├── pv29_input.zip       # Medium dataset met 36 GML bestanden
│   ├── ws0621_input.zip     # Zeer grote dataset met 86 GML bestanden
│   └── ws0665_input.zip     # Grote dataset met 44 GML bestanden
└── expected/
    ├── pv29_output/         # Expected output voor pv29
    ├── ws0621_output/       # Expected output voor ws0621 (wasID verwijderd)
    └── ws0665_output/       # Expected output voor ws0665
```

## Test Datasets

### WS0665 (Groot)
De WS0665 dataset is een grote real-world test case die gebruikt wordt om:
- Performance te meten bij verwerking van grote aantallen GML bestanden
- Geheugengebruik te controleren bij meerdere opeenvolgende runs
- Consistentie van hash berekening te valideren over vele bestanden

**Kenmerken:**
- 44 GML bestanden (nl.imow-ws0665.gebied.*.gml)
- Meerdere IO mappen (44)
- Totaal ~350KB aan GML data
- Complexe geografische objecten met gebiedsaanwijzingen

### WS0621 (Zeer Groot)
De WS0621 dataset is de grootste test case, gebruikt voor stress testing:
- Performance bij zeer grote datasets (86 GML bestanden)
- Test wasID verwijdering uit GML bestanden
- Schaalbaarheid en geheugengebruik

**Kenmerken:**
- 86 GML bestanden (geo_*.gml)
- Meerdere IO mappen (86)
- Test voor wasID element verwijdering
- Totaal groter dan WS0665

### PV29 (Medium)
De PV29 dataset is een middelgrote provinciale test case:
- Performance bij middelgrote datasets
- Provinciale omgevingsplannen

**Kenmerken:**
- 36 GML bestanden
- Meerdere IO mappen (36)
- Provinciale context (pv29)

## Tests Uitvoeren

### Alleen Fast Tests (standaard)
```bash
mvn test
# Of expliciet:
mvn test -Dtest.groups=fast
```

### Alleen Performance Tests  
```bash
mvn test -Dtest.groups=performance
```

### Alle Tests
```bash
mvn test -Dtest.groups=fast,performance
```

## Test Cases

De `PerformanceValidatieIntegrationTest` bevat:

### WS0665 Tests (4 tests)
1. **testWS0665LargeValidatieTransformatie**
   - Test grote dataset verwerking (44 GML bestanden)
   - Meet performance metrics (laden, verwerken, totaal)
   - Timeout: 30 seconden

2. **testWS0665ExpectedOutput**
   - Valideert expected output directory
   - Controleert hash waarden in alle IO.xml bestanden  
   - Verifieert bestandsnaam casing en consistentie
   - Check op dubbele GeoInformatieObjectVaststelling

3. **testWS0665StressTest**
   - 5 opeenvolgende runs
   - Meet performance consistentie
   - Controleert geheugen/resource leaks
   - Timeout: 60 seconden

4. **testWS0665InputStructure**
   - Valideert input ZIP structuur
   - Controleert aanwezigheid van alle verwachte bestanden
   - Berekent totale GML grootte

### WS0621 Tests (2 tests)
5. **testWS0621ExtraLargeValidatieTransformatie**
   - Test zeer grote dataset verwerking (86 GML bestanden)
   - Langere timeout: 60 seconden
   - Hogere performance limieten (< 15s)

6. **testWS0621NoWasIDInOutput**
   - Specifieke test voor wasID verwijdering
   - Controleert alle 86 GML bestanden
   - Valideert dat geen wasID elementen aanwezig zijn

### PV29 Tests (1 test)
7. **testPV29LargeValidatieTransformatie**
   - Test medium dataset verwerking (36 GML bestanden)
   - Provinciale context
   - Timeout: 30 seconden

## Performance Targets

### WS0665 (44 GML)
- **Verwerking**: < 5 seconden
- **Gemiddeld per GML**: ~100ms
- **Variatie tussen runs**: < 50% van gemiddelde
- **Totaal**: < 10 seconden (warning bij overschrijding)

### WS0621 (86 GML)
- **Verwerking**: < 10 seconden
- **Gemiddeld per GML**: ~120ms
- **Totaal**: < 15 seconden (warning bij overschrijding)

### PV29 (36 GML)
- **Verwerking**: < 5 seconden
- **Gemiddeld per GML**: ~100ms
- **Totaal**: < 10 seconden (warning bij overschrijding)

## Toevoegen van Nieuwe Test Data

1. Plaats input ZIP in `input/`
2. Genereer expected output en plaats in `expected/<naam>_output/`
3. Voeg nieuwe test toe aan `PerformanceValidatieIntegrationTest.java`
4. Tag test met `@Tag("performance")`
5. Gebruik `@Timeout` voor maximale runtime

## Notes

- Performance tests zijn **optioneel** en draaien niet standaard
- Gebruik voor grote datasets die te traag zijn voor reguliere CI/CD
- Ideaal om te draaien vóór releases of na significante wijzigingen
- Expected output moet correct zijn (inclusief hash waarden!)

## wasID Verwijdering

Bij het verwerken van GML bestanden die al een `GeoInformatieObjectVaststelling` wrapper hebben:
- De `IOProcessor` detecteert automatisch bestaande wrappers
- Als een `<wasID>` element aanwezig is, wordt deze verwijderd
- Dit voorkomt validatiefouten bij verder verwerking
- WS0621 dataset bevat specifieke tests hiervoor (`testWS0621NoWasIDInOutput`)

