import { createTheme } from "@mui/material/styles";

const lightPalette = {
    mode: "light",
    background: {
        default: "#f8fafc",
        paper: "#ffffff",
    },
    text: {
        primary: "#111827",
        secondary: "#6b7280",
    },
};

const darkPalette = {
    mode: "dark",
    background: {
        default: "#0f172a",
        paper: "#1f2937",
    },
    text: {
        primary: "#e2e8f0",
        secondary: "#9ca3af",
    },
};

export const muiTheme = createTheme({
    cssVariables: {
        colorSchemeSelector: "class",
    },
    colorSchemes: {
        light: { palette: lightPalette },
        dark: { palette: darkPalette },
    },
    palette: lightPalette,
    components: {
        MuiPaper: {
            styleOverrides: {
                root: {
                    backgroundImage: "none",
                },
            },
        },
        MuiOutlinedInput: {
            styleOverrides: {
                root: {
                    backgroundColor: "var(--mui-palette-background-paper)",
                    color: "var(--mui-palette-text-primary)",
                },
                notchedOutline: {
                    borderColor: "var(--mui-palette-divider)",
                },
            },
        },
        MuiInputLabel: {
            styleOverrides: {
                root: {
                    color: "var(--mui-palette-text-secondary)",
                },
            },
        },
        MuiDataGrid: {
            styleOverrides: {
                root: {
                    backgroundColor: "var(--mui-palette-background-paper)",
                    color: "var(--mui-palette-text-primary)",
                    borderColor: "var(--mui-palette-divider)",
                },
                columnHeaders: {
                    backgroundColor: "var(--mui-palette-background-default)",
                    color: "var(--mui-palette-text-primary)",
                    borderColor: "var(--mui-palette-divider)",
                },
                cell: {
                    borderColor: "var(--mui-palette-divider)",
                    color: "var(--mui-palette-text-primary)",
                },
                row: {
                    backgroundColor: "var(--mui-palette-background-paper)",
                    "&:hover": {
                        backgroundColor: "var(--mui-palette-action-hover)",
                    },
                },
                footerContainer: {
                    backgroundColor: "var(--mui-palette-background-default)",
                    color: "var(--mui-palette-text-primary)",
                    borderColor: "var(--mui-palette-divider)",
                },
            },
        },
        MuiPaginationItem: {
            styleOverrides: {
                root: {
                    color: "var(--mui-palette-text-primary)",
                    "&.Mui-selected": {
                        backgroundColor: "var(--mui-palette-primary-main)",
                        color: "#ffffff",
                    },
                },
            },
        },
        MuiTooltip: {
            styleOverrides: {
                tooltip: {
                    backgroundColor: "#374151",
                    color: "#e2e8f0",
                },
            },
        },
    },
});
