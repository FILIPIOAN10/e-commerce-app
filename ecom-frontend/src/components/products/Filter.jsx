import { useEffect, useState, useRef } from "react";
import { Button, FormControl, IconButton, InputLabel,MenuItem,Select, Tooltip } from "@mui/material";
import { FiArrowDown, FiArrowUp, FiRefreshCw, FiSearch } from "react-icons/fi";
import { useLocation, useNavigate, useSearchParams } from "react-router-dom";
import api from "../../api/api";

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
        const searchRef = useRef(null);

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
                if (searchTerm){
                    searchParams.set("keyword",searchTerm);
                } else {
                    searchParams.delete("keyword");
                }
                navigate(`${pathName}?${searchParams.toString()}`);
            },700);
            return () =>{
                clearTimeout(handler);
            };
        }, [searchParams,searchTerm,navigate,pathName]);

        useEffect(() => {
            if (searchTerm.trim().length < 2) {
                setSuggestions([]);
                return;
            }
            const fetchSuggestions = setTimeout(() => {
                api.get(`/public/products/autocomplete?q=${encodeURIComponent(searchTerm.trim())}`)
                    .then(({ data }) => {
                        setSuggestions(data);
                        setShowSuggestions(true);
                    })
                    .catch(() => setSuggestions([]));
            }, 200);
            return () => clearTimeout(fetchSuggestions);
        }, [searchTerm]);

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
            setSearchTerm(suggestion);
            setShowSuggestions(false);
            setHighlightedIndex(-1);
        };

        const handleKeyDown = (e) => {
            if (!showSuggestions || suggestions.length === 0) return;
            if (e.key === "ArrowDown") {
                e.preventDefault();
                setHighlightedIndex((prev) => (prev + 1) % suggestions.length);
            } else if (e.key === "ArrowUp") {
                e.preventDefault();
                setHighlightedIndex((prev) => (prev - 1 + suggestions.length) % suggestions.length);
            } else if (e.key === "Enter" && highlightedIndex >= 0) {
                e.preventDefault();
                handleSuggestionClick(suggestions[highlightedIndex]);
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
                        onChange={(e) => { setSearchTerm(e.target.value); setHighlightedIndex(-1); }}
                        onFocus={() => suggestions.length > 0 && setShowSuggestions(true)}
                        onKeyDown={handleKeyDown}
                        className="border border-gray-400 text-slate-800 rounded-md py-2 pl-10 pr-4 w-full focus:outline-none focus:ring-2 focus:ring-[#1976d2] dark:bg-gray-800 dark:text-white dark:border-gray-600" />
                        <FiSearch className="absolute left-3 top-1/2 transform -translate-y-1/2 text-slate-800 dark:text-gray-400 w-5 h-5" />

                        {showSuggestions && suggestions.length > 0 && (
                            <ul className="absolute top-full left-0 right-0 mt-1 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded-md shadow-lg z-50 max-h-60 overflow-y-auto">
                                {suggestions.map((suggestion, index) => (
                                    <li
                                        key={index}
                                        onClick={() => handleSuggestionClick(suggestion)}
                                        onMouseEnter={() => setHighlightedIndex(index)}
                                        className={`px-4 py-2 cursor-pointer text-sm transition ${
                                            highlightedIndex === index
                                                ? "bg-blue-100 dark:bg-blue-900 text-blue-700 dark:text-blue-200"
                                                : "text-slate-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700"
                                        }`}
                                    >
                                        {suggestion}
                                    </li>
                                ))}
                            </ul>
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
                        <Button variant="contained"
                        onClick={toggleSortOrder} 
                        color="primary" 
                        className="flex items-center gap-2 h-10">
                                    Sort By
                                    {sortOrder === "asc" ? (<FiArrowUp size={20}/>) : (<FiArrowDown size={20}/>)}
                                    
                        </Button>
                    
                    </Tooltip>
                    <button
                    className="flex items-center gap-2 bg-rose-900 text-white px-3 py-2 rounded-md transition duration-300 ease-in shadow-md focus:outline-none"
                    onClick={handleClearFilters}
                    >
                        <FiRefreshCw className="font-semibold size ={16}" />
                        <span className="font-semibold">Clear Filter</span> 
                    </button>
                  </div>
            </div>
        );


}

export default Filter;