import { createContext, useContext, useEffect, useState } from "react";
import { ThemeProvider as MUIThemeProvider } from "@mui/material/styles";
import { muiTheme } from "../theme/muiTheme";

const ThemeContext = createContext();

export const ThemeProvider = ({ children }) => {
    const [isDark, setIsDark] = useState(() => {
        const stored = localStorage.getItem("theme");
        if (stored) return stored === "dark";
        return window.matchMedia("(prefers-color-scheme: dark)").matches;
    });

    useEffect(() => {
        const root = document.documentElement;
        if (isDark) {
            root.classList.add("dark");
        } else {
            root.classList.remove("dark");
        }
        localStorage.setItem("theme", isDark ? "dark" : "light");
    }, [isDark]);

    const toggleTheme = () => setIsDark((prev) => !prev);

    return (
        <MUIThemeProvider theme={muiTheme}>
            <ThemeContext.Provider value={{ isDark, toggleTheme }}>
                {children}
            </ThemeContext.Provider>
        </MUIThemeProvider>
    );
};

export const useTheme = () => {
    const context = useContext(ThemeContext);
    if (!context) throw new Error("useTheme must be used within ThemeProvider");
    return context;
};
