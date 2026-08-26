import React from 'react'
import { useSelector } from 'react-redux'
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useLanguage } from '../context/LanguageContext';

const PrivateRoute = ({ publicPage = false, adminOnly = false }) => {
    const { user } = useSelector((state) => state.auth);
    const isAdmin = user && user?.roles?.includes("ROLE_ADMIN");
    const isSeller = user && user?.roles?.includes("ROLE_SELLER");
    const location = useLocation();
    const lang = useLanguage();

    if (publicPage) {
        return user ? <Navigate to={`/${lang}`} /> : <Outlet />
    }

    if (!user) {
        return <Navigate to={`/${lang}/login`} replace />
    }

    if (adminOnly) {
     
        if (isAdmin) return <Outlet />

        if (isSeller) {
            const sellerAllowedPaths = ["/admin/orders", "/admin/products"];
            const currentPath = location.pathname.replace(/^\/[a-z]{2}/, '');
            const sellerAllowed = sellerAllowedPaths.some(path =>
                currentPath.startsWith(path)
            );
            return sellerAllowed ? <Outlet /> : <Navigate to={`/${lang}`} replace />
        }

        return <Navigate to={`/${lang}`} replace />
    }

    return <Outlet />
}

export default PrivateRoute