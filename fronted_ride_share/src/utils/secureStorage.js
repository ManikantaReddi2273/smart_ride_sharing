import CryptoJS from 'crypto-js'

import config from '../config'

export const AUTH_STORAGE_KEY = 'ride-sharing/auth'

const isBrowser = typeof window !== 'undefined'

const encrypt = (value) =>
  CryptoJS.AES.encrypt(JSON.stringify(value), config.storageSecret).toString()

const decrypt = (cipher) => {
  const bytes = CryptoJS.AES.decrypt(cipher, config.storageSecret)
  const decrypted = bytes.toString(CryptoJS.enc.Utf8)
  return JSON.parse(decrypted)
}

export const secureStorage = {
  set: (key, value) => {
    if (!isBrowser) return
    try {
      localStorage.setItem(key, encrypt(value))
    } catch (error) {
      console.error('Unable to write secure storage', error)
    }
  },
  get: (key, fallback = null) => {
    if (!isBrowser) return fallback
    try {
      const payload = localStorage.getItem(key)
      if (!payload) return fallback
      return decrypt(payload)
    } catch (error) {
      console.error('Unable to read secure storage', error)
      return fallback
    }
  },
  remove: (key) => {
    if (!isBrowser) return
    localStorage.removeItem(key)
  },
}

