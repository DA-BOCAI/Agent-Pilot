import { createContext } from 'react'

type ToastMessage = {
  id: string
  message: string
  type: 'success' | 'error'
}

type ToastContextType = {
  showToast: (message: string, type?: 'success' | 'error') => void
  toasts: ToastMessage[]
  removeToast: (id: string) => void
}

export const ToastContext = createContext<ToastContextType | null>(null)
