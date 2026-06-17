import React from 'react'
import ReactDOM from 'react-dom/client'
import { Provider } from 'react-redux'
import { BrowserRouter } from 'react-router-dom'
import { Toaster } from 'react-hot-toast'
import { HelmetProvider } from 'react-helmet-async'
import App from './App'
import { store } from './store'
import './i18n'
import './index.css'

ReactDOM.createRoot(document.getElementById('root')).render(
    <React.StrictMode>
        <HelmetProvider>
        <Provider store={store}>
            <BrowserRouter>
                <App />
                <Toaster
                    position="top-right"
                    toastOptions={{
                        style: {
                            fontFamily: '"DM Sans", sans-serif',
                            borderRadius: '12px',
                            background: '#1a2a68',
                            color: '#fff',
                        },
                        success: { iconTheme: { primary: '#fbbf24', secondary: '#1a2a68' } },
                    }}
                />
            </BrowserRouter>
        </Provider>
        </HelmetProvider>
    </React.StrictMode>
)