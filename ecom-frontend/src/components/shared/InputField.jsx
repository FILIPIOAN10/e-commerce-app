const InputField = ({
    label,
    id,
    type,
    errors,
    register,
    required,
    message,
    className,
    min,
    minValue,
    maxValue,
    step,
    value,
    placeHolder,
}) => {
    const validationRules = {
        required: {value: required,message},
        ...(type !== "number" && min
            ? {minLength: {value:min,message:`Minimun ${min} character is required`}}
            :{}),
        ...(minValue !== undefined 
            ? {min : {value:minValue,message:`Minimum value is ${minValue}`}}
            :{}),
        ...(maxValue !== undefined 
            ? {max : {value:maxValue,message:`Maximum value is ${maxValue}`}}
            :{}),
        ...(type === "number" 
            ? {setValueAs : (inputValue) => inputValue === "" ? undefined : Number(inputValue) }
            :{}),
        pattern:
            type === "email"
                ? {
                    value: /^[\w.-]+@([\w-]+\.)+[\w-]{2,4}$/,
                    message: "Invalid email"
                }
                : type === "url"
                ? {
                    value: /^(https?:\/\/)?([\w-]+\.)+[\w-]{2,}(\/[\w\-./?%&=]*)?$/,
                    message: "Invalid URL"
                }
                : null
    };
    return  (
        <div className="flex flex-col gap-1 w-full">
            <label
            
            htmlFor={id}
            className={`${
                className ? className : ""
            } font-semibold text-sm text-slate-800}`}>
                {label}
            </label>
            <input
            
            type={type}
            id={id}
            min={minValue}
            max={maxValue}
            step={step}
            placeholder={placeHolder}
            className={`${
                className ? className : ""
            } px-2 py-2 border outline-none bg-transparent text-slate-800 rounded-md ${
                errors[id]?.message ? "border-red-500": "border-slate-700"
            }`}

            {...register(id,validationRules)}
            />

            {errors[id]?.message && (
                <p className="text-sm font-semibold text-red-600 mt-0">
                    {errors[id]?.message}
                </p>
            )}

        </div>
    );
};

export default InputField;