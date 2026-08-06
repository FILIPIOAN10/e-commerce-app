import { useState, useRef } from "react";
import { useSelector, useDispatch } from "react-redux";
import { useForm } from "react-hook-form";
import toast from "react-hot-toast";
import { updateProfile, changePassword, uploadAvatar } from "../../store/actions";
import { FaUserCircle, FaCamera, FaLock, FaEnvelope, FaPhone } from "react-icons/fa";
import Spinners from "../shared/Spinners";

const ProfileSettings = () => {
    const { user } = useSelector((state) => state.auth);
    const dispatch = useDispatch();
    const fileInputRef = useRef(null);

    const [profileLoader, setProfileLoader] = useState(false);
    const [passwordLoader, setPasswordLoader] = useState(false);
    const [avatarLoader, setAvatarLoader] = useState(false);

    const {
        register: registerProfile,
        handleSubmit: handleProfileSubmit,
        formState: { errors: profileErrors },
    } = useForm({
        mode: "onTouched",
        defaultValues: {
            email: user?.email || "",
            phone: user?.phone || "",
        },
    });

    const {
        register: registerPassword,
        handleSubmit: handlePasswordSubmit,
        reset: resetPassword,
        formState: { errors: passwordErrors },
    } = useForm({
        mode: "onTouched",
    });

    const onProfileSubmit = (data) => {
        dispatch(updateProfile(data, toast, setProfileLoader));
    };

    const onPasswordSubmit = (data) => {
        dispatch(
            changePassword(
                { currentPassword: data.currentPassword, newPassword: data.newPassword },
                toast,
                setPasswordLoader,
                resetPassword
            )
        );
    };

    const handleAvatarClick = () => {
        fileInputRef.current?.click();
    };

    const handleAvatarChange = (e) => {
        const file = e.target.files?.[0];
        if (!file) return;

        if (file.size > 5 * 1024 * 1024) {
            toast.error("File size must be less than 5MB");
            return;
        }

        const allowedTypes = ["image/jpeg", "image/png", "image/gif", "image/webp"];
        if (!allowedTypes.includes(file.type)) {
            toast.error("Only JPEG, PNG, GIF, and WebP images are allowed");
            return;
        }

        dispatch(uploadAvatar(file, toast, setAvatarLoader));
    };

    return (
        <div className="max-w-2xl mx-auto p-6 mt-10 dark:text-white min-h-screen">
            <h1 className="text-2xl font-bold mb-6 text-slate-800 dark:text-white">Profile Settings</h1>

            {/* Avatar Section */}
            <div className="bg-white dark:bg-gray-800 shadow rounded-lg p-6 mb-6">
                <h2 className="text-lg font-semibold mb-4 dark:text-white flex items-center gap-2">
                    <FaCamera className="text-slate-600 dark:text-slate-300" />
                    Avatar
                </h2>
                <div className="flex items-center gap-6">
                    <div className="relative">
                        {user?.avatarUrl ? (
                            <img
                                src={user.avatarUrl}
                                alt="Avatar"
                                className="w-24 h-24 rounded-full object-cover border-4 border-slate-200 dark:border-gray-600"
                            />
                        ) : (
                            <FaUserCircle className="w-24 h-24 text-slate-300 dark:text-gray-500" />
                        )}
                        {avatarLoader && (
                            <div className="absolute inset-0 flex items-center justify-center bg-black bg-opacity-50 rounded-full">
                                <Spinners />
                            </div>
                        )}
                    </div>
                    <div>
                        <button
                            onClick={handleAvatarClick}
                            disabled={avatarLoader}
                            className="bg-button-gradient text-white px-4 py-2 rounded-md hover:opacity-90 transition disabled:opacity-50"
                        >
                            {avatarLoader ? "Uploading..." : "Upload Avatar"}
                        </button>
                        <p className="text-sm text-slate-500 dark:text-slate-400 mt-2">
                            JPEG, PNG, GIF, WebP. Max 5MB.
                        </p>
                        <input
                            ref={fileInputRef}
                            type="file"
                            accept="image/jpeg,image/png,image/gif,image/webp"
                            onChange={handleAvatarChange}
                            className="hidden"
                        />
                    </div>
                </div>
            </div>

            {/* Profile Info Section */}
            <div className="bg-white dark:bg-gray-800 shadow rounded-lg p-6 mb-6">
                <h2 className="text-lg font-semibold mb-4 dark:text-white flex items-center gap-2">
                    <FaEnvelope className="text-slate-600 dark:text-slate-300" />
                    Profile Information
                </h2>
                <form onSubmit={handleProfileSubmit(onProfileSubmit)} className="space-y-4">
                    <div>
                        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
                            Username
                        </label>
                        <input
                            type="text"
                            value={user?.username || ""}
                            disabled
                            className="w-full px-3 py-2 border border-slate-300 dark:border-gray-600 rounded-md bg-slate-100 dark:bg-gray-700 text-slate-500 dark:text-slate-400"
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
                            Email
                        </label>
                        <input
                            type="email"
                            {...registerProfile("email", {
                                required: "*Email is required",
                                pattern: {
                                    value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
                                    message: "*Invalid email format",
                                },
                            })}
                            placeholder="Enter your email"
                            className="w-full px-3 py-2 border border-slate-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-slate-800 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                        />
                        {profileErrors.email && (
                            <p className="text-red-500 text-sm mt-1">{profileErrors.email.message}</p>
                        )}
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
                            Phone
                        </label>
                        <input
                            type="tel"
                            {...registerProfile("phone", {
                                maxLength: { value: 20, message: "*Phone must be less than 20 characters" },
                            })}
                            placeholder="Enter your phone number"
                            className="w-full px-3 py-2 border border-slate-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-slate-800 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                        />
                        {profileErrors.phone && (
                            <p className="text-red-500 text-sm mt-1">{profileErrors.phone.message}</p>
                        )}
                    </div>
                    <button
                        type="submit"
                        disabled={profileLoader}
                        className="bg-button-gradient text-white px-6 py-2 rounded-md hover:opacity-90 transition disabled:opacity-50 flex items-center gap-2"
                    >
                        {profileLoader ? (
                            <>
                                <Spinners /> Saving...
                            </>
                        ) : (
                            "Save Changes"
                        )}
                    </button>
                </form>
            </div>

            {/* Change Password Section */}
            <div className="bg-white dark:bg-gray-800 shadow rounded-lg p-6">
                <h2 className="text-lg font-semibold mb-4 dark:text-white flex items-center gap-2">
                    <FaLock className="text-slate-600 dark:text-slate-300" />
                    Change Password
                </h2>
                <form onSubmit={handlePasswordSubmit(onPasswordSubmit)} className="space-y-4">
                    <div>
                        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
                            Current Password
                        </label>
                        <input
                            type="password"
                            {...registerPassword("currentPassword", {
                                required: "*Current password is required",
                            })}
                            placeholder="Enter current password"
                            className="w-full px-3 py-2 border border-slate-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-slate-800 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                        />
                        {passwordErrors.currentPassword && (
                            <p className="text-red-500 text-sm mt-1">{passwordErrors.currentPassword.message}</p>
                        )}
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
                            New Password
                        </label>
                        <input
                            type="password"
                            {...registerPassword("newPassword", {
                                required: "*New password is required",
                                minLength: { value: 6, message: "*Password must be at least 6 characters" },
                                maxLength: { value: 40, message: "*Password must be less than 40 characters" },
                            })}
                            placeholder="Enter new password"
                            className="w-full px-3 py-2 border border-slate-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-slate-800 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                        />
                        {passwordErrors.newPassword && (
                            <p className="text-red-500 text-sm mt-1">{passwordErrors.newPassword.message}</p>
                        )}
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
                            Confirm New Password
                        </label>
                        <input
                            type="password"
                            {...registerPassword("confirmPassword", {
                                required: "*Please confirm your password",
                                validate: (value, formValues) =>
                                    value === formValues.newPassword || "*Passwords do not match",
                            })}
                            placeholder="Confirm new password"
                            className="w-full px-3 py-2 border border-slate-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-slate-800 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                        />
                        {passwordErrors.confirmPassword && (
                            <p className="text-red-500 text-sm mt-1">{passwordErrors.confirmPassword.message}</p>
                        )}
                    </div>
                    <button
                        type="submit"
                        disabled={passwordLoader}
                        className="bg-button-gradient text-white px-6 py-2 rounded-md hover:opacity-90 transition disabled:opacity-50 flex items-center gap-2"
                    >
                        {passwordLoader ? (
                            <>
                                <Spinners /> Changing...
                            </>
                        ) : (
                            "Change Password"
                        )}
                    </button>
                </form>
            </div>
        </div>
    );
};

export default ProfileSettings;
