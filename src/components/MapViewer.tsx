import { useEffect, useRef, useState } from 'react';
import { Box, Button, Stack } from '@mui/material';
import Map from 'ol/Map';
import View from 'ol/View';
import TileLayer from 'ol/layer/Tile';
import OSM from 'ol/source/OSM';
import { fromLonLat } from 'ol/proj';
import { GeoJSON } from 'ol/format';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import { Style, Fill, Stroke } from 'ol/style';

interface MapViewerProps {
  mapData: any;
  onChange: (mapData: any) => void;
  onNext: () => void;
  onBack: () => void;
}

const MapViewer = ({ mapData, onChange, onNext, onBack }: MapViewerProps) => {
  const mapRef = useRef<HTMLDivElement>(null);
  const [map, setMap] = useState<Map | null>(null);

  useEffect(() => {
    if (!mapRef.current) return;

    const initialMap = new Map({
      target: mapRef.current,
      layers: [
        new TileLayer({
          source: new OSM()
        })
      ],
      view: new View({
        center: fromLonLat([5.2913, 52.1326]), // Nederland centrum
        zoom: 7
      })
    });

    setMap(initialMap);

    return () => {
      initialMap.setTarget(undefined);
    };
  }, []);

  const handleFileUpload = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file || !map) return;

    const reader = new FileReader();
    reader.onload = (e) => {
      const gmlData = e.target?.result as string;
      const format = new GeoJSON();
      const features = format.readFeatures(gmlData);

      const vectorSource = new VectorSource({
        features: features
      });

      const vectorLayer = new VectorLayer({
        source: vectorSource,
        style: new Style({
          fill: new Fill({
            color: 'rgba(255, 255, 0, 0.2)'
          }),
          stroke: new Stroke({
            color: '#ffcc00',
            width: 2
          })
        })
      });

      map.addLayer(vectorLayer);
      map.getView().fit(vectorSource.getExtent(), {
        padding: [50, 50, 50, 50],
        maxZoom: 15
      });

      onChange({ gmlData, features: features });
    };

    reader.readAsText(file);
  };

  return (
    <Box>
      <Stack spacing={3}>
        <Box
          ref={mapRef}
          sx={{
            height: 400,
            width: '100%',
            border: '1px solid #ccc',
            borderRadius: 1
          }}
        />

        <Box>
          <input
            type="file"
            accept=".gml,.xml"
            onChange={handleFileUpload}
            style={{ marginBottom: '1rem' }}
          />
        </Box>

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
            disabled={!mapData}
          >
            Volgende
          </Button>
        </Box>
      </Stack>
    </Box>
  );
};

export default MapViewer; 