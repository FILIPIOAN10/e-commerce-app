import React from 'react'
import { useSelector } from 'react-redux'
import { Navigate, Outlet, useLocation } from 'react-router-dom';

const PrivateRoute = ({ publicPage = false, adminOnly = false }) => {
    const { user } = useSelector((state) => state.auth);
    const isAdmin = user && user?.roles?.includes("ROLE_ADMIN");
    const isSeller = user && user?.roles?.includes("ROLE_SELLER");
    const isUser = user && user?.roles?.includes("ROLE_USER");
    const location = useLocation();

    if (publicPage) {
        return user ? <Navigate to="/" /> : <Outlet />
    }

    if (!user) {
        return <Navigate to="/login" replace />
    }

    if (adminOnly) {
        // ✅ admin trece liber
        if (isAdmin) return <Outlet />

        // ✅ seller are acces doar la orders și products
        if (isSeller) {
            const sellerAllowedPaths = ["/admin/orders", "/admin/products"];
            const sellerAllowed = sellerAllowedPaths.some(path =>
                location.pathname.startsWith(path)
            );
            return sellerAllowed ? <Outlet /> : <Navigate to="/" replace />
        }

        // ✅ user normal nu are acces
        return <Navigate to="/" replace />
    }

    return <Outlet />
}

export default PrivateRoute