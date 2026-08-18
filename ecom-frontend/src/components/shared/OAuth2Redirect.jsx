import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { useDispatch } from "react-redux";
import { fetchUserDetails } from "../../store/actions";
import toast from "react-hot-toast";

const OAuth2Redirect = () => {
    const navigate = useNavigate();
    const dispatch = useDispatch();

    useEffect(() => {
        dispatch(fetchUserDetails())
            .then(() => {
                toast.success("Login successful!");
                navigate("/");
            })
            .catch(() => {
                toast.error("Could not load user data.");
                navigate("/login");
            });
    }, [navigate, dispatch]);

    return (
        <div className="min-h-[calc(100vh-64px)] flex justify-center items-center">
            <p className="text-slate-600 text-lg">Logging you in...</p>
        </div>
    );
};

export default OAuth2Redirect;