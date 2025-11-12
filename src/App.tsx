import React, { useState } from 'react';
import { 
  Container, 
  Box, 
  Typography, 
  Paper,
  Stepper,
  Step,
  StepLabel,
  CssBaseline,
  ThemeProvider,
  createTheme
} from '@mui/material';
import MapViewer from './components/MapViewer';
import TextEditor from './components/TextEditor';
import DocumentInfo from './components/DocumentInfo';
import ExportOptions from './components/ExportOptions';

const theme = createTheme({
  palette: {
    mode: 'light',
  },
});

const steps = ['Document Info', 'Map Viewer', 'Text Editor', 'Export Options'];

function App() {
  const [activeStep, setActiveStep] = useState(0);
  const [mapData, setMapData] = useState<any>(null);
  const [documentData, setDocumentData] = useState<any>(null);
  const [textData, setTextData] = useState<any>(null);

  const handleMapChange = (data: any) => {
    setMapData(data);
    console.log('Map data updated:', data);
  };

  const handleDocumentChange = (data: any) => {
    setDocumentData(data);
    console.log('Document data updated:', data);
  };

  const handleTextChange = (data: any) => {
    setTextData(data);
    console.log('Text data updated:', data);
  };

  const handleNext = () => {
    setActiveStep((prevStep) => prevStep + 1);
  };

  const handleBack = () => {
    setActiveStep((prevStep) => prevStep - 1);
  };

  const renderStepContent = (step: number) => {
    switch (step) {
      case 0:
        return (
          <DocumentInfo
            documentData={documentData}
            onChange={handleDocumentChange}
            onNext={handleNext}
            onBack={handleBack}
          />
        );
      case 1:
        return (
          <MapViewer
            mapData={mapData}
            onChange={handleMapChange}
            onNext={handleNext}
            onBack={handleBack}
          />
        );
      case 2:
        return (
          <TextEditor
            textData={textData}
            onChange={handleTextChange}
            onNext={handleNext}
            onBack={handleBack}
          />
        );
      case 3:
        return (
          <ExportOptions
            documentData={documentData}
            mapData={mapData}
            textData={textData}
            onBack={handleBack}
          />
        );
      default:
        return null;
    }
  };

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <Container maxWidth="lg" sx={{ py: 4 }}>
        <Box sx={{ my: 4 }}>
          <Typography variant="h4" component="h1" gutterBottom>
            Omgevingswet Applicatie
          </Typography>
          
          <Stepper activeStep={activeStep} sx={{ mb: 4 }}>
            {steps.map((label) => (
              <Step key={label}>
                <StepLabel>{label}</StepLabel>
              </Step>
            ))}
          </Stepper>

          <Paper sx={{ p: 3 }}>
            {renderStepContent(activeStep)}
          </Paper>
        </Box>
      </Container>
    </ThemeProvider>
  );
}

export default App; 