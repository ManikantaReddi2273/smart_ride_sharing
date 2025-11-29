const config = {
  // In development, use relative path to leverage Vite proxy
  // In production, use full API Gateway URL
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL || 
    (import.meta.env.DEV ? '/api' : 'http://localhost:8080/api'),
  storageSecret: import.meta.env.VITE_STORAGE_SECRET || 'smart-ride-sharing-secret',
}

export default config

