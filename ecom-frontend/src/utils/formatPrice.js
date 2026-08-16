export const formatPrice = (amount) => {
    const value = Number(amount);
    if (Number.isNaN(value)) {
        return "—";
    }
    return new Intl.NumberFormat("en-US",{
        style:"currency",
        currency:"USD",
    }).format(value);
}



export const formatPriceCalculation = (quantity,price) => {
    return  (Number(quantity) * Number(price)).toFixed(2);
}

export const formatRevenue = (value) =>{
    if (value >= 1e9) {
        return (value / 1e9).toFixed(1) +"B";
    } else if (value>= 1e6){
          return (value / 1e6).toFixed(1) +"M";
    } else if (value>= 1e3){
          return (value / 1e3).toFixed(1) +"K";
    } else{
        return value;
    } 
};
