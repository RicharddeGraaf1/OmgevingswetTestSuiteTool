import React from 'react';
import { Box, Button, Stack } from '@mui/material';
import ReactQuill from 'react-quill';
import 'react-quill/dist/quill.snow.css';

export interface TextEditorProps {
  textData: any;
  onChange: (data: any) => void;
  onNext: () => void;
  onBack: () => void;
}

const TextEditor = ({ textData, onChange, onNext, onBack }: TextEditorProps) => {
  const modules = {
    toolbar: [
      [{ 'header': [1, 2, 3, false] }],
      ['bold', 'italic', 'underline', 'strike'],
      [{ 'list': 'ordered'}, { 'list': 'bullet' }],
      ['link', 'image'],
      ['clean']
    ],
  };

  return (
    <Box>
      <Stack spacing={3}>
        <ReactQuill
          value={textData?.content || ''}
          onChange={(content) => onChange({ ...textData, content })}
          modules={modules}
          style={{ height: '300px', marginBottom: '50px' }}
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
            disabled={!textData?.content}
          >
            Volgende
          </Button>
        </Box>
      </Stack>
    </Box>
  );
};

export default TextEditor; 