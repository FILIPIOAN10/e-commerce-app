import { bannerImageOne, bannerImageThree, bannerImageTwo } from "./constant";
import { FaBoxOpen, FaBullhorn, FaChartBar, FaClipboardList, FaExclamationTriangle, FaFileImport, FaHome, FaShoppingCart, FaStore, FaSync, FaSyncAlt, FaTag, FaThList, FaUndo, FaUsers } from "react-icons/fa";

export const bannerLists =[

    {
        id:1,
        image: bannerImageOne,
        title: "Home Comfort",
        subtitle : "Living Room",
        description :"Upgrade your space with cozy and stylish sofas",
    },
    {
        id:2,
        image: bannerImageTwo,
        title: "Entertaiment Hub",
        subtitle : "Smart TV",
        description :"Experience the latest in home entertainment",
    },
    {
        id:3,
        image: bannerImageThree,
        title: "Playful Picks",
        subtitle : "Kids Clothing",
        description :"Bright and fun styles for kids, up to 20% off",
    }


];


// This is the menu definition
export const adminNavigation = [
    { name: "Dashboard", 
      href :"/admin",
      icon : FaHome,
      current:true
    },
    {
      name:"Orders",
      href: "/admin/orders",
      icon:FaShoppingCart,
      current:true
    },
    { name: "Products", 
      href :"/admin/products",
      icon : FaBoxOpen
    },
    { name: "Bundles", 
      href :"/admin/bundles",
      icon : FaTag
    },
    { name: "Subscriptions", 
      href :"/admin/subscriptions",
      icon : FaSync
    },
    { name: "Categories", 
      href :"/admin/categories",
      icon : FaThList
    },
    { name: "Sellers", 
      href :"/admin/sellers",
      icon : FaStore
    },
    { name: "Coupons", 
      href :"/admin/coupons",
      icon : FaTag
    },
    { name: "Returns", 
      href :"/admin/returns",
      icon : FaUndo
    },
    { name: "Low Stock", 
      href :"/admin/low-stock",
      icon : FaExclamationTriangle
    },
    { name: "Activity Log", 
      href :"/admin/activity-logs",
      icon : FaClipboardList
    },
    { name: "Product Import", 
      href :"/admin/product-import",
      icon : FaFileImport
    },
    { name: "Promo Campaigns", 
      href :"/admin/promo-campaigns",
      icon : FaBullhorn
    },
    { name: "Users", 
      href :"/admin/users",
      icon : FaUsers
    }
];


export const sellerNavigation = [

    {
      name:"Orders",
      href: "/admin/orders",
      icon:FaShoppingCart,
      current:true
    },
    { name: "Products", 
      href :"/admin/products",
      icon : FaBoxOpen
    },
    { name: "Low Stock", 
      href :"/admin/low-stock",
      icon : FaExclamationTriangle
    }
  
];

