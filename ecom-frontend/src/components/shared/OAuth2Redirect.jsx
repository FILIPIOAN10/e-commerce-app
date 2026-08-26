import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { useLazyGetUserDetailsQuery } from "../../store/api/authApi";
import toast from "react-hot-toast";

const OAuth2Redirect = () => {
    const navigate = useNavigate();
    const [trigger, { data, error, isSuccess, isError }] = useLazyGetUserDetailsQuery();

    useEffect(() => {
        trigger();
    }, [trigger]);

    useEffect(() => {
        if (isSuccess && data) {
            toast.success("Login successful!");
            navigate("/");
        }
        if (isError && error) {
            toast.error("Could not load user data.");
            navigate("/login");
        }
    }, [isSuccess, isError, data, error, navigate]);

    return (
        <div className="min-h-[calc(100vh-64px)] flex justify-center items-center">
            <p className="text-slate-600 text-lg">Logging you in...</p>
        </div>
    );
};

export default OAuth2Redirect;