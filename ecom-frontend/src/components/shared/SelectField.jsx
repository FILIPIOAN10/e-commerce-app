import React from 'react';

const SelectField = ({ label, id, options, register, required, message, errors, disabled, placeholder }) => {
  return (
    <div className="flex flex-col w-full text-left">
      <label htmlFor={id} className="text-sm font-semibold text-slate-700 dark:text-gray-300 mb-1">
        {label} {required && <span className="text-red-500">*</span>}
      </label>
      <select
        id={id}
        disabled={disabled}
        className={`border rounded-md px-3 py-2 outline-none h-11 focus:border-custom-blue ${
          errors[id] ? 'border-red-500' : 'border-slate-300 dark:border-gray-600'
        } ${disabled ? 'bg-slate-100 dark:bg-gray-700 cursor-not-allowed text-slate-400 dark:text-gray-500' : 'bg-white dark:bg-gray-700 text-slate-800 dark:text-gray-200'}`}
        {...register(id, { required: required ? message : false })}
      >
        <option value="">{placeholder}</option>
        
        {/* AM MODIFICAT MAP-UL SĂ ACCEPTE ȘI TEXT SIMPLU ȘI OBIECTE */}
        {options?.map((option, index) => {
          // Dacă opțiunea este obiect, îi luăm .name (pentru backend), altfel e textul în sine
          const isObject = typeof option === 'object' && option !== null;
          const optionValue = isObject ? option.name : option;

          return (
            <option key={index} value={optionValue}>
              {optionValue}
            </option>
          );
        })}
      </select>
      {errors[id] && (
        <span className="text-red-500 text-xs mt-1 font-medium">{errors[id].message}</span>
      )}
    </div>
  );
};

export default SelectField;