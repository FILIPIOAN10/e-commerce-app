import { Menu, MenuButton, MenuItem, MenuItems } from "@headlessui/react";
import { FaGlobe, FaCheck } from "react-icons/fa";
import { useTranslation } from "react-i18next";
import { SUPPORTED_LANGUAGES } from "../../i18n";

const LanguageSwitcher = () => {
  const { i18n } = useTranslation();
  const current =
    SUPPORTED_LANGUAGES.find((l) => l.code === i18n.language) ??
    SUPPORTED_LANGUAGES[0];

  const changeLanguage = (code) => {
    if (code === current.code) return;
    i18n.changeLanguage(code);
  };

  return (
    <Menu as="div" className="relative">
      <MenuButton
        data-testid="language-switcher"
        aria-label="Select language"
        className="flex items-center gap-1.5 text-white hover:text-yellow-300 transition-colors"
      >
        <FaGlobe className="text-lg" />
        <span className="text-sm font-medium uppercase">{current.code}</span>
      </MenuButton>

      <MenuItems className="absolute right-0 mt-2 w-44 origin-top-right rounded-lg bg-white dark:bg-gray-800 shadow-xl ring-1 ring-black/5 focus:outline-none z-50 py-1">
        {SUPPORTED_LANGUAGES.map((lang) => (
          <MenuItem key={lang.code}>
            {({ focus }) => (
              <button
                onClick={() => changeLanguage(lang.code)}
                lang={lang.code}
                data-testid={`language-option-${lang.code}`}
                className={`w-full flex items-center justify-between px-4 py-2 text-sm ${
                  focus ? "bg-gray-100 dark:bg-gray-700" : ""
                } text-gray-800 dark:text-gray-100`}
              >
                <span className="flex items-center gap-2">
                  <span aria-hidden="true">{lang.flag}</span>
                  {lang.label}
                </span>
                {lang.code === current.code && (
                  <FaCheck className="text-green-500 text-xs" />
                )}
              </button>
            )}
          </MenuItem>
        ))}
      </MenuItems>
    </Menu>
  );
};

export default LanguageSwitcher;
