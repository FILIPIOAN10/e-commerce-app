import { Badge } from "@mui/material";
import { useState } from "react";
import { FaShoppingCart, FaSignInAlt, FaStore, FaHeart, FaTruck, FaMoon, FaSun, FaBalanceScale, FaTags, FaSync } from "react-icons/fa";
import { Link, useLocation } from "react-router-dom";
import { IoIosMenu } from "react-icons/io";
import { RxCross2 } from "react-icons/rx";
import { useSelector } from "react-redux";
import UserMenu from "../UserMenu";
import { useTheme } from "../../hooks/useTheme";
import NotificationBell from "./NotificationBell";

const Navbar = () => {
    const path = useLocation().pathname;
    const [navbarOpen, setNavbarOpen] = useState(false);
    const { cart } = useSelector((state) => state.carts);
    const { user } = useSelector((state) => state.auth);
    const { compareList } = useSelector((state) => state.products);
    const { isDark, toggleTheme } = useTheme();

    // ✅ verifică rolul
    const isAdmin = Boolean(user?.roles?.includes("ROLE_ADMIN"));

    return (
        <div className="h-17.5 bg-custom-gradient text-white z-50 flex items-center sticky top-0 dark:bg-gray-900">
            <div className="lg:px-14 sm:px-8 px-4 w-full flex justify-between">
                <Link to="/" className="flex items-center text-2xl font-bold">
                    <FaStore className="mr-2 text-3xl" />
                    <span className="font-[Poppins]">E-Shop</span>
                </Link>

                <ul className={`flex sm:gap-10 gap-4 sm:items-center text-slate-800 dark:text-gray-200 sm:static absolute left-0 top-17.5 sm:shadow-none shadow-md ${
                    navbarOpen ? "h-fit sm:pb-0 pb-5" : "h-0 overflow-hidden"
                } transition-all duration-100 sm:h-fit sm:bg-none bg-custom-gradient text-white sm:w-fit w-full sm:flex-row flex-col px-4 sm:px-0`}>
                    
                    <li className="font-medium transition-all duration-150">
                        <Link className={`${path === "/" ? "text-white font-semibold" : "text-gray-200"}`} to="/">
                            Home
                        </Link>
                    </li>

                    <li className="font-medium transition-all duration-150">
                        <Link className={`${path === "/products" ? "text-white font-semibold" : "text-gray-200"}`} to="/products">
                            Products
                        </Link>
                    </li>

                    <li className="font-medium transition-all duration-150">
                        <Link className={`${path === "/bundles" ? "text-white font-semibold" : "text-gray-200"}`} to="/bundles">
                            Bundles
                        </Link>
                    </li>

                    <li className="font-medium transition-all duration-150">
                        <Link className={`${path === "/subscriptions" ? "text-white font-semibold" : "text-gray-200"}`} to="/subscriptions">
                            Subscriptions
                        </Link>
                    </li>

                    <li className="font-medium transition-all duration-150">
                        <Link className={`${path === "/about" ? "text-white font-semibold" : "text-gray-200"}`} to="/about">
                            About
                        </Link>
                    </li>

                    <li className="font-medium transition-all duration-150">
                        <Link className={`${path === "/contact" ? "text-white font-semibold" : "text-gray-200"}`} to="/contact">
                            Contact
                        </Link>
                    </li>

                    {/* ✅ track order vizibil doar pentru non-admin logat */}
                    {(user && user.id) && !isAdmin && (
                        <li className="font-medium transition-all duration-150">
                            <Link className={`${path === "/track-order" ? "text-white font-semibold" : "text-gray-200"}`} to="/track-order">
                                <FaTruck size={22} />
                            </Link>
                        </li>
                    )}

                    {/* ✅ wishlist vizibil doar pentru non-admin logat */}
                    {(user && user.id) && !isAdmin && (
                        <li className="font-medium transition-all duration-150">
                            <Link className={`${path === "/wishlist" ? "text-white font-semibold" : "text-gray-200"}`} to="/wishlist">
                                <FaHeart size={22} />
                            </Link>
                        </li>
                    )}

                    {/* ✅ compare vizibil doar pentru non-admin logat */}
                    {(user && user.id) && !isAdmin && (
                        <li className="font-medium transition-all duration-150">
                            <Link className={`${path === "/compare" ? "text-white font-semibold" : "text-gray-200"}`} to="/compare">
                                <Badge
                                    badgeContent={compareList?.length || 0}
                                    color="secondary"
                                    overlap="circular"
                                    anchorOrigin={{ vertical: 'top', horizontal: 'right' }}>
                                    <FaBalanceScale size={22} />
                                </Badge>
                            </Link>
                        </li>
                    )}

                    {/* ✅ cart vizibil doar pentru non-admin */}
                    {!isAdmin && (
                        <li className="font-medium transition-all duration-150">
                            <Link className={`${path === "/cart" ? "text-white font-semibold" : "text-gray-200"}`} to="/cart">
                                <Badge
                                    showZero
                                    badgeContent={cart?.length || 0}
                                    color="primary"
                                    overlap="circular"
                                    anchorOrigin={{ vertical: 'top', horizontal: 'right' }}>
                                    <FaShoppingCart size={25} />
                                </Badge>
                            </Link>
                        </li>
                    )}

                    {(user && user.id) ? (
                        <li className="font-medium transition-all duration-150">
                            <UserMenu />
                        </li>
                    ) : (
                        <li className="font-medium transition-all duration-150">
                            <Link className="flex items-center space-x-2 px-4 py-1.5
                                        bg-linear-to-r from-purple-600 to-red-500
                                        text-white font-semibold rounded-md shadow-lg
                                        hover:from-purple-500 hover:to-red-400 transition
                                        duration-300 ease-in-out transform"
                                to="/login">
                                <FaSignInAlt />
                                <span>Login</span>
                            </Link>
                        </li>
                    )}
                </ul>

                <div className="flex items-center gap-3">
                    {user?.id && <NotificationBell />}
                    <button
                        onClick={toggleTheme}
                        className="text-white hover:text-yellow-300 transition-colors text-xl"
                        title={isDark ? "Switch to light mode" : "Switch to dark mode"}
                    >
                        {isDark ? <FaSun /> : <FaMoon />}
                    </button>
                    <button
                        onClick={() => setNavbarOpen(!navbarOpen)}
                        className="sm:hidden flex items-center sm:mt-0 mt-2">
                        {navbarOpen ? (
                            <RxCross2 className="text-white text-3xl" />
                        ) : (
                            <IoIosMenu className="text-white text-3xl" />
                        )}
                    </button>
                </div>
            </div>
        </div>
    );
};

export default Navbar;