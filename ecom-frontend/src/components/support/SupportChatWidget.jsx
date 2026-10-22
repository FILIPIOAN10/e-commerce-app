import { useEffect, useRef, useState } from "react";
import { useSelector } from "react-redux";
import { useLocation } from "react-router-dom";
import toast from "react-hot-toast";
import { FaCommentDots, FaPaperPlane, FaPlus, FaTimes } from "react-icons/fa";
import api from "../../api/api";

const POLL_MS = 15000;
const GREETING = { role: "assistant", content: "Hi! I can check an order, explain our policies, or start a refund request." };

// The floating support chat. Talks only to the shop's /api/support/* — the shop
// decides who the customer is and forwards to SupportPilot; no key ever reaches
// the browser. While a refund waits for an admin the conversation is paused, so
// the input is disabled and the widget polls until the decision lands.
const SupportChatWidget = () => {
    const { user } = useSelector((state) => state.auth);
    const location = useLocation();
    const [enabled, setEnabled] = useState(false);
    const [open, setOpen] = useState(false);
    const [loaded, setLoaded] = useState(false);
    const [conversationId, setConversationId] = useState(null);
    const [status, setStatus] = useState("open");
    const [messages, setMessages] = useState([GREETING]);
    const [draft, setDraft] = useState("");
    const [sending, setSending] = useState(false);
    const listRef = useRef(null);

    const onAdminPage = /^\/[a-z]{2}\/admin/.test(location.pathname);

    useEffect(() => {
        if (!user) return; // rendering is gated on user below, so a stale flag never shows
        api.get("/support/status")
            .then(({ data }) => setEnabled(Boolean(data.enabled)))
            .catch(() => setEnabled(false));
    }, [user]);

    const showConversation = (data) => {
        setConversationId(data.conversationId);
        setStatus(data.status);
        setMessages(data.messages.length ? data.messages : [GREETING]);
    };

    // Resume the latest conversation (from any device) the first time the panel opens.
    useEffect(() => {
        if (!open || loaded) return;
        api.get("/support/conversations/latest")
            .then(({ status: httpStatus, data }) => {
                if (httpStatus === 200 && data) showConversation(data);
            })
            .catch(() => {})
            .finally(() => setLoaded(true));
    }, [open, loaded]);

    // Paused for an admin: poll until the decision arrives in the conversation.
    useEffect(() => {
        if (!open || status !== "awaiting_approval" || !conversationId) return;
        const timer = setInterval(() => {
            api.get(`/support/conversations/${conversationId}`)
                .then(({ data }) => showConversation(data))
                .catch(() => {});
        }, POLL_MS);
        return () => clearInterval(timer);
    }, [open, status, conversationId]);

    useEffect(() => {
        listRef.current?.scrollTo({ top: listRef.current.scrollHeight });
    }, [messages, open]);

    if (!user || !enabled || onAdminPage) return null;

    const send = async (e) => {
        e.preventDefault();
        const text = draft.trim();
        if (!text || sending) return;
        setSending(true);
        setDraft("");
        setMessages((prev) => [...prev, { role: "customer", content: text }]);
        try {
            const { data } = await api.post("/support/chat", { message: text, conversationId });
            setConversationId(data.conversationId);
            setStatus(data.status);
            setMessages((prev) => [...prev, { role: "assistant", content: data.reply }]);
        } catch (error) {
            setMessages((prev) => prev.slice(0, -1));
            setDraft(text);
            toast.error(error?.response?.data?.message || "The assistant could not answer. Please try again.");
        } finally {
            setSending(false);
        }
    };

    const startOver = () => {
        setConversationId(null);
        setStatus("open");
        setMessages([GREETING]);
    };

    const paused = status === "awaiting_approval";

    return (
        <div className="fixed bottom-5 right-5 z-50 flex flex-col items-end gap-3">
            {open && (
                <section
                    aria-label="Support chat"
                    className="w-[calc(100vw-2.5rem)] max-w-sm h-[28rem] flex flex-col bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700 rounded-xl shadow-xl"
                >
                    <header className="flex items-center justify-between px-4 py-3 border-b border-gray-200 dark:border-gray-700">
                        <h2 className="font-semibold text-gray-800 dark:text-white">Support assistant</h2>
                        <div className="flex items-center gap-1">
                            <button type="button" onClick={startOver} title="New conversation" aria-label="New conversation"
                                    className="p-2 rounded text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-800">
                                <FaPlus />
                            </button>
                            <button type="button" onClick={() => setOpen(false)} title="Close" aria-label="Close support chat"
                                    className="p-2 rounded text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-800">
                                <FaTimes />
                            </button>
                        </div>
                    </header>

                    <ol ref={listRef} aria-live="polite" className="flex-1 overflow-y-auto px-4 py-3 space-y-2">
                        {messages.map((m, i) => (
                            <li key={i} className={m.role === "customer" ? "flex justify-end" : "flex justify-start"}>
                                <span className={`max-w-[85%] whitespace-pre-wrap rounded-lg px-3 py-2 text-sm ${
                                    m.role === "customer"
                                        ? "bg-blue-600 text-white"
                                        : "bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-100"
                                }`}>
                                    {m.content}
                                </span>
                            </li>
                        ))}
                        {sending && <li className="text-xs text-gray-400">The assistant is typing…</li>}
                    </ol>

                    {paused && (
                        <p className="px-4 py-2 text-xs text-amber-700 bg-amber-50 dark:bg-amber-900/30 dark:text-amber-300">
                            A colleague is reviewing your refund request. The answer will appear here.
                        </p>
                    )}

                    <form onSubmit={send} className="flex items-center gap-2 p-3 border-t border-gray-200 dark:border-gray-700">
                        <label htmlFor="support-message" className="sr-only">Message</label>
                        <input
                            id="support-message"
                            value={draft}
                            onChange={(e) => setDraft(e.target.value)}
                            maxLength={2000}
                            disabled={sending || paused}
                            placeholder={paused ? "Waiting for a colleague…" : "Ask about an order or a policy"}
                            className="flex-1 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-800 dark:text-gray-100 disabled:opacity-60"
                        />
                        <button type="submit" disabled={sending || paused || !draft.trim()} aria-label="Send"
                                className="p-2 rounded-lg bg-blue-600 text-white disabled:opacity-50">
                            <FaPaperPlane />
                        </button>
                    </form>
                </section>
            )}

            <button
                type="button"
                onClick={() => setOpen((v) => !v)}
                aria-expanded={open}
                aria-label={open ? "Close support chat" : "Open support chat"}
                className="h-14 w-14 rounded-full bg-blue-600 text-white shadow-lg flex items-center justify-center text-xl hover:bg-blue-700"
            >
                {open ? <FaTimes /> : <FaCommentDots />}
            </button>
        </div>
    );
};

export default SupportChatWidget;
