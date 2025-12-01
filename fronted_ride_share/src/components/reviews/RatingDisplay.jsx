import { Box, Typography, Rating, Chip } from '@mui/material'
import { Star } from '@mui/icons-material'

/**
 * Rating Display Component
 * Shows average rating with star display
 */
const RatingDisplay = ({ rating, totalReviews, size = 'small', showCount = true }) => {
  if (rating === null || rating === undefined) {
    return (
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
        <Typography variant="body2" color="text.secondary">
          No ratings yet
        </Typography>
      </Box>
    )
  }

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
      <Rating
        value={rating}
        precision={0.1}
        readOnly
        size={size}
        icon={<Star fontSize="inherit" />}
        emptyIcon={<Star fontSize="inherit" style={{ opacity: 0.3 }} />}
      />
      <Typography variant="body2" sx={{ fontWeight: 500 }}>
        {rating.toFixed(1)}
      </Typography>
      {showCount && totalReviews !== null && totalReviews !== undefined && (
        <Typography variant="caption" color="text.secondary">
          ({totalReviews} {totalReviews === 1 ? 'review' : 'reviews'})
        </Typography>
      )}
    </Box>
  )
}

/**
 * Rating Chip Component
 * Shows rating as a chip
 */
export const RatingChip = ({ rating, totalReviews }) => {
  if (rating === null || rating === undefined) {
    return <Chip label="No ratings" size="small" color="default" />
  }

  return (
    <Chip
      icon={<Star />}
      label={`${rating.toFixed(1)} (${totalReviews || 0})`}
      size="small"
      color="primary"
      sx={{ fontWeight: 500 }}
    />
  )
}

export default RatingDisplay
