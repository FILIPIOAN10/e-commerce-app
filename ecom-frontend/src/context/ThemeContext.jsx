import { useEffect, useState } from "react";
import { ThemeProvider as MUIThemeProvider } from "@mui/material/styles";
import { muiTheme } from "../theme/muiTheme";
import { ThemeContext } from "./ThemeContext.js";

export const ThemeProvider = ({ children }) => {
    const [isDark, setIsDark] = useState(() => {
        const stored = localStorage.getItem("theme");
        if (stored) return stored === "dark";
        return false;
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

