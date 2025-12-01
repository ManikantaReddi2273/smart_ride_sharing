import { useState } from 'react'
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Rating,
  Box,
  Typography,
  Alert,
  CircularProgress,
} from '@mui/material'
import { Star } from '@mui/icons-material'

/**
 * Review Dialog Component
 * Allows users to submit a review with rating and comment
 */
const ReviewDialog = ({ open, onClose, onSubmit, loading, error, title, subtitle }) => {
  const [rating, setRating] = useState(0)
  const [comment, setComment] = useState('')
  const [errors, setErrors] = useState({})

  const handleSubmit = () => {
    // Validation
    const newErrors = {}
    if (rating === 0) {
      newErrors.rating = 'Please select a rating'
    }
    if (rating < 1 || rating > 5) {
      newErrors.rating = 'Rating must be between 1 and 5'
    }

    setErrors(newErrors)

    if (Object.keys(newErrors).length === 0) {
      onSubmit({ rating, comment: comment.trim() || null })
      // Reset form after successful submission
      setTimeout(() => {
        setRating(0)
        setComment('')
        setErrors({})
      }, 500)
    }
  }

  const handleClose = () => {
    if (!loading) {
      setRating(0)
      setComment('')
      setErrors({})
      onClose()
    }
  }

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle>{title || 'Rate Your Experience'}</DialogTitle>
      <DialogContent>
        {subtitle && (
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {subtitle}
          </Typography>
        )}

        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {typeof error === 'string' ? error : error?.message || 'Failed to submit review'}
          </Alert>
        )}

        <Box sx={{ mb: 3 }}>
          <Typography component="legend" gutterBottom>
            Rating *
          </Typography>
          <Rating
            name="rating"
            value={rating}
            onChange={(event, newValue) => {
              setRating(newValue || 0)
              setErrors({ ...errors, rating: null })
            }}
            size="large"
            icon={<Star fontSize="inherit" />}
            emptyIcon={<Star fontSize="inherit" style={{ opacity: 0.3 }} />}
          />
          {errors.rating && (
            <Typography variant="caption" color="error" sx={{ display: 'block', mt: 0.5 }}>
              {errors.rating}
            </Typography>
          )}
        </Box>

        <TextField
          fullWidth
          multiline
          rows={4}
          label="Comment (Optional)"
          placeholder="Share your experience..."
          value={comment}
          onChange={(e) => setComment(e.target.value)}
          variant="outlined"
          sx={{ mb: 2 }}
        />

        <Typography variant="caption" color="text.secondary">
          * Required fields
        </Typography>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={loading}>
          Cancel
        </Button>
        <Button
          onClick={handleSubmit}
          variant="contained"
          disabled={loading || rating === 0}
          startIcon={loading ? <CircularProgress size={16} /> : null}
        >
          {loading ? 'Submitting...' : 'Submit Review'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

export default ReviewDialog
