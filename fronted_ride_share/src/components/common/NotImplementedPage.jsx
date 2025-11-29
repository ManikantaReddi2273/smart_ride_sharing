import { Box, Card, CardContent, Stack, Typography } from '@mui/material'
import ConstructionRoundedIcon from '@mui/icons-material/ConstructionRounded'

import PageContainer from './PageContainer'

const NotImplementedPage = ({ title = 'Not Yet Implemented' }) => {
  return (
    <PageContainer>
      <Card>
        <CardContent>
          <Stack spacing={3} alignItems="center" py={6}>
            <Box
              sx={{
                width: 80,
                height: 80,
                borderRadius: '50%',
                display: 'grid',
                placeItems: 'center',
                bgcolor: 'rgba(15, 139, 141, 0.12)',
                color: 'primary.main',
              }}
            >
              <ConstructionRoundedIcon sx={{ fontSize: 40 }} />
            </Box>
            <Typography variant="h5" fontWeight={600} textAlign="center">
              {title}
            </Typography>
            <Typography variant="body1" color="text.secondary" textAlign="center" maxWidth={400}>
              This feature is currently under development and will be available soon.
            </Typography>
          </Stack>
        </CardContent>
      </Card>
    </PageContainer>
  )
}

export default NotImplementedPage

