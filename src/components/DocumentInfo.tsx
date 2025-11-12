import React from 'react';
import { Box, Button, Stack, TextField } from '@mui/material';
import { format } from 'date-fns';

export interface DocumentInfoProps {
  documentData: any;
  onChange: (data: any) => void;
  onNext: () => void;
  onBack: () => void;
}

const DocumentInfo = ({ documentData, onChange, onNext, onBack }: DocumentInfoProps) => {
  const handleChange = (field: string) => (event: React.ChangeEvent<HTMLInputElement>) => {
    onChange({
      ...documentData,
      [field]: event.target.value
    });
  };

  return (
    <Box>
      <Stack spacing={3}>
        <TextField
          label="Document ID"
          value={documentData?.id || ''}
          onChange={handleChange('id')}
          fullWidth
        />
        <TextField
          label="Titel"
          value={documentData?.title || ''}
          onChange={handleChange('title')}
          fullWidth
        />
        <TextField
          label="Beschrijving"
          value={documentData?.description || ''}
          onChange={handleChange('description')}
          multiline
          rows={4}
          fullWidth
        />
        <TextField
          label="Auteur"
          value={documentData?.author || ''}
          onChange={handleChange('author')}
          fullWidth
        />
        <TextField
          label="Datum"
          type="date"
          value={documentData?.date || format(new Date(), 'yyyy-MM-dd')}
          onChange={handleChange('date')}
          fullWidth
          InputLabelProps={{
            shrink: true,
          }}
        />

        <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
          <Button
            variant="outlined"
            onClick={onBack}
          >
            Terug
          </Button>
          <Button
            variant="contained"
            onClick={onNext}
            disabled={!documentData?.id || !documentData?.title}
          >
            Volgende
          </Button>
        </Box>
      </Stack>
    </Box>
  );
};

export default DocumentInfo; 