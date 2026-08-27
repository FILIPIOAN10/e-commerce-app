import React from "react";
import { createRoot } from 'react-dom/client';
import './index.css'
import './i18n'
import App from './App.jsx'
import store from './store/reducers/store.js'
import { Provider } from 'react-redux'
import { ThemeProvider } from './context/ThemeContext.jsx'
import { HelmetProvider } from "react-helmet-async";

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <HelmetProvider>
      <Provider store={store}>
        <ThemeProvider>
          <App />
        </ThemeProvider>
      </Provider>
    </HelmetProvider>
  </React.StrictMode>,
)
