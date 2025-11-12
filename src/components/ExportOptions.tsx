import React from 'react';
import { Box, Button, Stack, FormControl, FormLabel, RadioGroup, FormControlLabel, Radio } from '@mui/material';

export interface ExportOptionsProps {
  documentData: any;
  mapData: any;
  textData: any;
  onBack: () => void;
}

const ExportOptions = ({ documentData, mapData, textData, onBack }: ExportOptionsProps) => {
  const handleExport = (format: string) => {
    console.log('Exporting to:', format);
    console.log('Document data:', documentData);
    console.log('Map data:', mapData);
    console.log('Text data:', textData);
  };

  return (
    <Box>
      <Stack spacing={3}>
        <FormControl>
          <FormLabel>Exporteer als</FormLabel>
          <RadioGroup defaultValue="gml">
            <FormControlLabel value="gml" control={<Radio />} label="GML" />
            <FormControlLabel value="xml" control={<Radio />} label="XML" />
            <FormControlLabel value="json" control={<Radio />} label="JSON" />
          </RadioGroup>
        </FormControl>

        <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
          <Button
            variant="outlined"
            onClick={onBack}
          >
            Terug
          </Button>
          <Button
            variant="contained"
            onClick={() => handleExport('gml')}
            disabled={!documentData || !mapData || !textData}
          >
            Exporteren
          </Button>
        </Box>
      </Stack>
    </Box>
  );
};

export default ExportOptions; 