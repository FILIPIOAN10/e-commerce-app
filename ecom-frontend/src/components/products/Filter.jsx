import { useEffect, useState, useRef, useCallback } from "react";
import { Button, FormControl, IconButton, InputLabel,MenuItem,Select, Tooltip } from "@mui/material";
import { FiArrowDown, FiArrowUp, FiRefreshCw, FiSearch, FiClock, FiX } from "react-icons/fi";
import { useLocation, useNavigate, useSearchParams } from "react-router-dom";
import api from "../../api/api";

const SEARCH_HISTORY_KEY = "productSearchHistory";
const MAX_HISTORY = 10;
const MAX_HISTORY_DISPLAY = 5;
const MAX_SUGGESTIONS = 10;
const SEARCH_DEBOUNCE_MS = 700;
const SUGGESTION_DEBOUNCE_MS = 200;

const loadSearchHistory = () => {
    try {
        const saved = localStorage.getItem(SEARCH_HISTORY_KEY);
        return saved ? JSON.parse(saved) : [];
    } catch {
        return [];
    }
};

const saveSearchHistory = (history) => {
    try {
        localStorage.setItem(SEARCH_HISTORY_KEY, JSON.stringify(history));
    } catch {
        // ignore storage errors
    }
};

const Filter = ({categories}) => {

        const [searchParams] = useSearchParams();
        const params = new URLSearchParams(searchParams);
        const pathName= useLocation().pathname;
        const navigate = useNavigate();

        const [category, setCategory] = useState("all");
        const [sortOrder,setSortOrder] = useState("asc");
        const [searchTerm,setSearchTerm] = useState("");
        const [suggestions, setSuggestions] = useState([]);
        const [showSuggestions, setShowSuggestions] = useState(false);
        const [highlightedIndex, setHighlightedIndex] = useState(-1);
        const [recentSearches, setRecentSearches] = useState(() => loadSearchHistory());
        const searchRef = useRef(null);

        const addToSearchHistory = useCallback((term) => {
            const trimmed = term.trim();
            if (!trimmed) return;
            setRecentSearches((prev) => {
                const next = [trimmed, ...prev.filter((item) => item.toLowerCase() !== trimmed.toLowerCase())].slice(0, MAX_HISTORY);
                saveSearchHistory(next);
                return next;
            });
        }, []);

        const removeFromSearchHistory = useCallback((term) => {
            setRecentSearches((prev) => {
                const next = prev.filter((item) => item !== term);
                saveSearchHistory(next);
                return next;
            });
        }, []);

        const clearSearchHistory = useCallback(() => {
            setRecentSearches([]);
            saveSearchHistory([]);
            setSuggestions([]);
            setShowSuggestions(false);
        }, []);

        const commitSearch = useCallback((term) => {
            const trimmed = term.trim();
            if (!trimmed) return;
            const currentKeyword = searchParams.get("keyword") || "";
            if (trimmed !== currentKeyword) {
                const newParams = new URLSearchParams(searchParams);
                newParams.set("keyword", trimmed);
                navigate(`${pathName}?${newParams.toString()}`);
            }
            if (trimmed.length >= 2) {
                addToSearchHistory(trimmed);
            }
            setSearchTerm(trimmed);
            setShowSuggestions(false);
            setHighlightedIndex(-1);
        }, [addToSearchHistory, navigate, pathName, searchParams]);

        useEffect( () => {
            const currentCategory = searchParams.get("category") || "all";
            const currentSortOrder = searchParams.get("sortBy") || "asc";
            const currentSearchTerm = searchParams.get("keyword") || "";

            setCategory (currentCategory);
            setSortOrder (currentSortOrder);
            setSearchTerm(currentSearchTerm);
        }, [searchParams]);


        useEffect ( () => {
            const handler = setTimeout( () => {
                const trimmed = searchTerm.trim();
                const currentKeyword = searchParams.get("keyword") || "";
                if (trimmed === currentKeyword) return;

                const newParams = new URLSearchParams(searchParams);
                if (trimmed){
                    newParams.set("keyword",trimmed);
                } else {
                    newParams.delete("keyword");
                }
                navigate(`${pathName}?${newParams.toString()}`);
                if (trimmed.length >= 2) {
                    addToSearchHistory(trimmed);
                }
            },SEARCH_DEBOUNCE_MS);
            return () =>{
                clearTimeout(handler);
            };
        }, [searchParams,searchTerm,navigate,pathName, addToSearchHistory]);

        useEffect(() => {
            const trimmed = searchTerm.trim();

            const updateSuggestions = (items) => setSuggestions(items);

            if (!trimmed) {
                updateSuggestions(recentSearches.slice(0, MAX_HISTORY_DISPLAY).map((text) => ({ text, type: "history" })));
                return;
            }

            if (trimmed.length < 2) {
                const filtered = recentSearches
                    .filter((term) => term.toLowerCase().includes(trimmed.toLowerCase()))
                    .slice(0, MAX_HISTORY_DISPLAY);
                updateSuggestions(filtered.map((text) => ({ text, type: "history" })));
                return;
            }

            const filteredHistory = recentSearches
                .filter((term) => term.toLowerCase().includes(trimmed.toLowerCase()))
                .slice(0, MAX_HISTORY_DISPLAY);

            // Show recent searches instantly
            updateSuggestions(filteredHistory.map((text) => ({ text, type: "history" })));

            const fetchSuggestions = setTimeout(() => {
                api.get(`/public/products/autocomplete?q=${encodeURIComponent(trimmed)}`)
                    .then(({ data }) => {
                        const apiItems = (data || [])
                            .filter((item) => !filteredHistory.some((h) => h.toLowerCase() === item.toLowerCase()))
                            .slice(0, MAX_SUGGESTIONS - filteredHistory.length)
                            .map((text) => ({ text, type: "api" }));
                        const merged = [...filteredHistory.map((text) => ({ text, type: "history" })), ...apiItems];
                        updateSuggestions(merged);
                    })
                    .catch(() => {
                        updateSuggestions(filteredHistory.map((text) => ({ text, type: "history" })));
                    });
            }, SUGGESTION_DEBOUNCE_MS);
            return () => clearTimeout(fetchSuggestions);
        }, [searchTerm, recentSearches]);

        useEffect(() => {
            const handleClickOutside = (e) => {
                if (searchRef.current && !searchRef.current.contains(e.target)) {
                    setShowSuggestions(false);
                }
            };
            document.addEventListener("mousedown", handleClickOutside);
            return () => document.removeEventListener("mousedown", handleClickOutside);
        }, []);

        const handleSuggestionClick = (suggestion) => {
            commitSearch(suggestion.text);
        };

        const handleKeyDown = (e) => {
            if (e.key === "Enter") {
                e.preventDefault();
                if (showSuggestions && highlightedIndex >= 0 && suggestions[highlightedIndex]) {
                    commitSearch(suggestions[highlightedIndex].text);
                } else if (searchTerm.trim()) {
                    commitSearch(searchTerm.trim());
                }
                return;
            }
            if (!showSuggestions || suggestions.length === 0) return;
            if (e.key === "ArrowDown") {
                e.preventDefault();
                setHighlightedIndex((prev) => (prev + 1) % suggestions.length);
            } else if (e.key === "ArrowUp") {
                e.preventDefault();
                setHighlightedIndex((prev) => (prev - 1 + suggestions.length) % suggestions.length);
            } else if (e.key === "Escape") {
                setShowSuggestions(false);
                setHighlightedIndex(-1);
            }
        };

        const handleCategoryChange = (event) => {
            const selectedCategory = event.target.value;
            if(selectedCategory === "all"){
                params.delete("category");
            } else  {
                params.set("category",selectedCategory);
            }
            navigate(`${pathName}?${params}`);

            setCategory(event.target.value);
        };

        const toggleSortOrder = () =>{
            setSortOrder((prevOrder) => {
                const newOrder = (prevOrder === "asc" ) ? "desc": "asc";
                params.set("sortBy",newOrder);
                navigate(`${pathName}?${params}`);
                return newOrder;
            })
        };

        const handleClearFilters = () =>{
            navigate({pathname :window.location.pathname});
        };
        return (
            <div className="flex lg:flex-row flex-col-reverse lg:justify-between justify-center items-center gap-4 ">
                {/* Search Bar*/}
                <div ref={searchRef} className="relative flex items-center 2xl:w-112.5 sm:w-105 w-full">
                    <input
                        type="text"
                        placeholder="Search Products"
                        value={searchTerm}
                        onChange={(e) => { setSearchTerm(e.target.value); setShowSuggestions(true); setHighlightedIndex(-1); }}
                        onFocus={() => setShowSuggestions(true)}
                        onKeyDown={handleKeyDown}
                        className="w-full rounded-xl border border-gray-300 bg-white py-2.5 pl-10 pr-4 text-slate-800 transition
                        placeholder:text-gray-400 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30
                        dark:border-gray-700 dark:bg-gray-900 dark:text-white" />
                        <FiSearch className="absolute left-3 top-1/2 transform -translate-y-1/2 text-slate-800 dark:text-gray-400 w-5 h-5" />

                        {showSuggestions && suggestions.length > 0 && (
                            <div className="absolute top-full left-0 right-0 mt-1 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded-md shadow-lg z-50 max-h-60 overflow-y-auto">
                                {searchTerm.trim().length < 2 && suggestions.some((s) => s.type === "history") && (
                                    <div className="flex justify-between items-center px-4 py-1.5 text-xs text-gray-500 dark:text-gray-400 border-b border-gray-200 dark:border-gray-700">
                                        <span>Recent searches</span>
                                        <button
                                            type="button"
                                            onClick={(e) => { e.stopPropagation(); clearSearchHistory(); }}
                                            className="text-rose-600 hover:text-rose-800 dark:text-rose-400"
                                        >
                                            Clear
                                        </button>
                                    </div>
                                )}
                                <ul>
                                    {suggestions.map((suggestion, index) => (
                                        <li
                                            key={`${suggestion.type}-${suggestion.text}-${index}`}
                                            onClick={() => handleSuggestionClick(suggestion)}
                                            onMouseEnter={() => setHighlightedIndex(index)}
                                            className={`px-4 py-2 cursor-pointer text-sm transition ${
                                                highlightedIndex === index
                                                    ? "bg-blue-100 dark:bg-blue-900 text-blue-700 dark:text-blue-200"
                                                    : "text-slate-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700"
                                            }`}
                                        >
                                            <div className="flex items-center justify-between w-full">
                                                <span className="flex items-center gap-2">
                                                    {suggestion.type === "history" && <FiClock className="w-4 h-4 text-gray-400" />}
                                                    {suggestion.text}
                                                </span>
                                                {suggestion.type === "history" && (
                                                    <button
                                                        type="button"
                                                        onClick={(e) => { e.stopPropagation(); removeFromSearchHistory(suggestion.text); }}
                                                        className="p-1 text-gray-400 hover:text-rose-600"
                                                        aria-label="Remove from history"
                                                    >
                                                        <FiX className="w-3.5 h-3.5" />
                                                    </button>
                                                )}
                                            </div>
                                        </li>
                                    ))}
                                </ul>
                            </div>
                        )}
                </div>
                  {/*Category Selection*/}
                  <div className="flex sm:flex-row  flex-col gap-4 items-center">
                    <FormControl
                        className="text-slate-800 dark:text-gray-200 border-slate-700 dark:border-gray-600" 
                        variant="outlined"
                        size="small">
                            <InputLabel id ="category-select-label">Category</InputLabel>
                            <Select
                                labelId ="category-select-label"
                                value={category}
                                onChange={handleCategoryChange}
                                label ="Category"
                                className="min-w-30 text-slate-800 dark:text-gray-200 border-slate-700 dark:border-gray-600"
                                >
                                    <MenuItem value ="all">All</MenuItem>
                                    {categories.map ((item) => (
                                        <MenuItem  key ={item.categoryId} value ={item.categoryName}>
                                            
                                            {item.categoryName}</MenuItem>
                                    ))}
                                </Select>
                    </FormControl>
                    { /* SORT BUTTON & CLEAR FILTER*/}
                    <Tooltip title= "Sorted by price:asc">
                        <button
                            type="button"
                            onClick={toggleSortOrder}
                            className="flex h-10 items-center gap-2 rounded-xl border border-gray-300 bg-white px-4
                                       text-sm font-semibold text-slate-700 transition hover:border-gray-400 hover:bg-gray-50
                                       dark:border-gray-700 dark:bg-gray-900 dark:text-gray-200 dark:hover:bg-gray-800">
                            Sort By
                            {sortOrder === "asc" ? (<FiArrowUp size={16}/>) : (<FiArrowDown size={16}/>)}
                        </button>
                    
                    </Tooltip>
                    <button
                        type="button"
                        className="flex h-10 items-center gap-2 rounded-xl px-4 text-sm font-semibold text-gray-500
                                   transition hover:bg-gray-100 hover:text-gray-800
                                   dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-gray-100"
                        onClick={handleClearFilters}
                    >
                        <FiRefreshCw size={16} />
                        <span>Clear Filter</span>
                    </button>
                  </div>
            </div>
        );


}

export default Filter;