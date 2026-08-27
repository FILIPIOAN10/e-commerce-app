import { useState } from "react";
import { FaEnvelope, FaMapMarkedAlt, FaPhone, FaPaperPlane } from "react-icons/fa";
import api from "../api/api";
import toast from "react-hot-toast";
import Spinners from "./shared/Spinners";
import { useTranslation } from "react-i18next";

const Contact = () => {
    const [loader, setLoader] = useState(false);
    const [form, setForm] = useState({ name: "", email: "", message: "" });
    const { t } = useTranslation("contact");

    const handleChange = (e) => {
        setForm({ ...form, [e.target.name]: e.target.value });
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setLoader(true);
        try {
            const { data } = await api.post("/public/contact", form);
            toast.success(data.message || t("messageSentSuccess"));
            setForm({ name: "", email: "", message: "" });
        } catch (error) {
            toast.error(error?.response?.data?.message || t("messageSentFailed"));
        } finally {
            setLoader(false);
        }
    };

    return (
        <div className="flex flex-col items-center justify-center min-h-screen py-12 bg-gradient-to-br from-slate-100 to-blue-50 dark:from-gray-950 dark:to-gray-900">
            <div className="bg-white shadow-lg rounded-lg p-8 w-full max-w-lg dark:bg-gray-800 dark:text-white">
                <h1 className="text-4xl font-bold text-center mb-6 dark:text-white">
                    {t("contactUs")}
                </h1>
                <p className="text-gray-600 text-center mb-4 dark:text-gray-300">
                    {t("contactDescription")}
                </p>
                <form onSubmit={handleSubmit} className="space-y-4">
                    <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300">
                            {t("name")}
                        </label>
                        <input
                            type="text"
                            name="name"
                            value={form.name}
                            onChange={handleChange}
                            required
                            className="mt-1 block w-full border border-gray-300 rounded-lg p-2 focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:border-gray-600 dark:text-white"
                        />
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300">
                            {t("email")}
                        </label>
                        <input
                            type="email"
                            name="email"
                            value={form.email}
                            onChange={handleChange}
                            required
                            className="mt-1 block w-full border border-gray-300 rounded-lg p-2 focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:border-gray-600 dark:text-white"
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300">
                            {t("message")}
                        </label>
                        <textarea
                            rows="4"
                            name="message"
                            value={form.message}
                            onChange={handleChange}
                            required
                            className="mt-1 block w-full border border-gray-300 rounded-lg p-2 focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:border-gray-600 dark:text-white"
                        />
                    </div>
                    <button
                        type="submit"
                        disabled={loader}
                        className="w-full bg-blue-500 text-white py-2 rounded-lg hover:bg-blue-600 transition duration-300 flex items-center justify-center gap-2 disabled:opacity-50"
                    >
                        {loader ? (
                            <>
                                <Spinners /> {t("sending", { ns: "common"})}
                            </>
                        ) : (
                            <>
                                <FaPaperPlane /> {t("sendMessage")}
                            </>
                        )}
                    </button>
                </form>
                <div className="mt-8 text-center">
                    <h2 className="text-lg font-semibold dark:text-white">
                        {t("contactInformation")}
                    </h2>
                    <div className="flex flex-col items-center space-y-2 mt-4">
                        <div className="flex items-center">
                            <FaPhone className="text-blue-500 mr-2"/>
                            <span className="text-gray-600 dark:text-gray-300">+40 743 301 377</span>
                        </div>
                        <div className="flex items-center">
                            <FaEnvelope className="text-blue-500 mr-2"/>
                            <span className="text-gray-600 dark:text-gray-300">FilipOfficial@gmail.com</span>
                        </div>

                        <div className="flex items-center">
                            <FaMapMarkedAlt className="text-blue-500 mr-2"/>
                            <span className="text-gray-600 dark:text-gray-300">Strada Salcamilor Nr 1</span>
                        </div>
                    </div>

                </div>
            </div>

        </div>
    );
};

export default Contact;