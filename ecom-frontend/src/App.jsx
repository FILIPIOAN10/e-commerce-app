

import './App.css'
import { BrowserRouter as Router,Routes,Route } from 'react-router-dom'
import Navbar from './components/shared/Navbar'
import { Toaster } from 'react-hot-toast'
import React, { Suspense, lazy } from 'react'
import PrivateRoute from './components/PrivateRoute'
import Loader from './components/shared/Loader'

const Home = lazy(() => import('./components/home/Home'))
const Products = lazy(() => import('./components/products/Products'))
const About = lazy(() => import('./components/About'))
const Contact = lazy(() => import('./components/Contact'))
const Cart = lazy(() => import('./components/cart/cart'))
const LogIn = lazy(() => import('./components/auth/Login'))
const Register = lazy(() => import('./components/auth/Register'))
const VerifyEmail = lazy(() => import('./components/auth/VerifyEmail'))
const ForgotPassword = lazy(() => import('./components/auth/ForgotPassword'))
const ResetPassword = lazy(() => import('./components/auth/ResetPassword'))
const Checkout = lazy(() => import('./components/checkout/Checkout'))
const PaymentConfirmation = lazy(() => import('./components/checkout/PaymentConfirmation'))
const AdminLayout = lazy(() => import('./components/admin/AdminLayout'))
const Dashboard = lazy(() => import('./components/admin/dashboard/Dashboard'))
const AdminProducts = lazy(() => import('./components/admin/products/AdminProducts'))
const Category = lazy(() => import('./components/admin/categories/Category'))
const Sellers = lazy(() => import('./components/admin/sellers/Sellers'))
const Coupons = lazy(() => import('./components/admin/coupons/Coupons'))
const LowStockAlerts = lazy(() => import('./components/admin/lowstock/LowStockAlerts'))
const Orders = lazy(() => import('./components/admin/orders/Orders'))
const AdminReturns = lazy(() => import('./components/admin/returns/AdminReturns'))
const Profile = lazy(() => import('./components/profile/Profile'))
const ProfileSettings = lazy(() => import('./components/profile/ProfileSettings'))
const ProfileOrders = lazy(() => import('./components/profile/ProfileOrders'))
const Wishlist = lazy(() => import('./components/wishlist/Wishlist'))
const ComparePage = lazy(() => import('./components/compare/ComparePage'))
const TrackOrder = lazy(() => import('./components/track/TrackOrder'))
const OAuth2Redirect = lazy(() => import('./components/shared/OAuth2Redirect'))
const NotFound = lazy(() => import('./components/shared/NotFound'))
const AdminUsers = lazy(() => import('./components/admin/users/AdminUsers'))

function App() {
  return (
    <React.Fragment>
    <Router>
      <div className="min-h-screen dark:bg-gray-950">
      <Navbar/>
      <Suspense fallback={<Loader />}>
      <Routes>
        <Route  path='/' element ={<Home/>} />
        <Route  path='/products' element ={<Products/>} />
        <Route  path='/about' element ={<About/>} />
        <Route  path='/contact' element ={<Contact/>} />
        <Route  path='/cart' element ={<Cart/>} />
        <Route path='/oauth2/redirect' element={<OAuth2Redirect />} />
        <Route path='/verify-email' element={<VerifyEmail />} />
        <Route path='/forgot-password' element={<ForgotPassword />} />
        <Route path='/reset-password' element={<ResetPassword />} />
        <Route path='/' element={<PrivateRoute />}>
          <Route path="/profile" element={<Profile />} />
          <Route path="/profile/settings" element={<ProfileSettings />} />
          <Route path ="/checkout" element ={<Checkout/>}/>
          <Route path ="/checkout/order-confirm" element ={<PaymentConfirmation />}/>
        </Route>
        <Route path='/' element={<PrivateRoute publicPage/>}>
             <Route  path='/login' element ={<LogIn/>} />
              <Route  path='/Register' element ={<Register/>} />
        </Route>

        <Route path='/' element ={<PrivateRoute adminOnly />}>
          <Route path='/admin' element={<AdminLayout/>}>
           <Route path='' element={<Dashboard />}/>
            <Route path='products' element={<AdminProducts />}/>
            <Route path='sellers' element={<Sellers />}/>
            <Route path='orders' element={<Orders />}/>
            <Route path='returns' element={<AdminReturns />}/>
            <Route path='categories' element={<Category />}/>
            <Route path='coupons' element={<Coupons />}/>
            <Route path='low-stock' element={<LowStockAlerts />}/>
            <Route path='users' element={<AdminUsers />}/>
          </Route>
        </Route>

        <Route path='/' element={<PrivateRoute />}>
          <Route path='/profile/orders' element={<ProfileOrders />} />
          <Route path='/wishlist' element={<Wishlist />} />
          <Route path='/compare' element={<ComparePage />} />
          <Route path='/track-order' element={<TrackOrder />} />
        </Route>
        <Route path='*' element={<NotFound />} />
      </Routes>
      </Suspense>
      </div>
    </Router>
    <Toaster position='bottom-center'/>
    </React.Fragment>
  )
}

export default App
