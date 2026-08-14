import { MdArrowBack, MdShoppingCart } from "react-icons/md";
import EmptyState from "../shared/EmptyState";

const CartEmpty = () => {
    return (
        <EmptyState
            icon={MdShoppingCart}
            title="Your cart is empty"
            message="Add some products to get started"
            action={{ label: "Start Shopping", path: "/", icon: MdArrowBack }}
        />
    );
};

export default CartEmpty;