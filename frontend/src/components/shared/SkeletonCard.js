import React from 'react';
import { Card, CardContent, Skeleton, Stack, Box } from '@mui/material';

export default function SkeletonCard({ rows = 3 }) {
  return (
    <Card variant="outlined">
      <CardContent>
        <Stack spacing={1.5}>
          <Skeleton variant="text" width="40%" height={32} />
          <Skeleton variant="rectangular" width="100%" height={20} />
          {Array.from({ length: rows }).map((_, index) => (
            <Box key={index} sx={{ display: 'flex', gap: 2, alignItems: 'center' }}>
              <Skeleton variant="circular" width={40} height={40} />
              <Box sx={{ flexGrow: 1 }}>
                <Skeleton variant="text" width="60%" />
                <Skeleton variant="text" width="40%" />
              </Box>
            </Box>
          ))}
        </Stack>
      </CardContent>
    </Card>
  );
}
