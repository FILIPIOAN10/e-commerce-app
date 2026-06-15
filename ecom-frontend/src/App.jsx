

import './App.css'
import Products from './components/products/Products'

import { BrowserRouter as Router,Routes,Route } from 'react-router-dom'
import Home from './components/home/Home'
import Navbar from './components/shared/Navbar'
import About from './components/About'
import Contact from './components/Contact'
import { Toaster } from 'react-hot-toast'
import React, {useState} from 'react'
import Cart from './components/cart/cart'
import LogIn from './components/auth/Login'
import PrivateRoute from './components/PrivateRoute'
import Register from './components/auth/Register'
import Checkout from './components/checkout/Checkout'
import PaymentConfirmation from './components/checkout/PaymentConfirmation'
import AdminLayout from './components/admin/AdminLayout'
import Dashboard from './components/admin/dashboard/Dashboard'
import AdminProducts from './components/admin/products/AdminProducts'
import Category from './components/admin/categories/Category'
import Sellers from './components/admin/sellers/Sellers'
import Orders from './components/admin/orders/Orders'
import Profile from './components/profile/Profile'
import ProfileOrders from './components/profile/ProfileOrders'

import OAuth2Redirect from './components/shared/OAuth2Redirect'

function App() {
  return (
    <React.Fragment>
    <Router>
      <Navbar/>
      <Routes>
        <Route  path='/' element ={<Home/>} />
        <Route  path='/products' element ={<Products/>} />
        <Route  path='/about' element ={<About/>} />
        <Route  path='/contact' element ={<Contact/>} />
        <Route  path='/cart' element ={<Cart/>} />
        <Route path='/oauth2/redirect' element={<OAuth2Redirect />} />
        <Route path='/' element={<PrivateRoute />}>
          <Route path="/profile" element={<Profile />} />
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
            <Route path='categories' element={<Category />}/>
          </Route>
        </Route>

        <Route path='/' element ={<PrivateRoute />}>
          <Route path='/profile/orders' element={<ProfileOrders />} />
        </Route>
      </Routes>
    </Router>
    <Toaster position='bottom-center'/>
    </React.Fragment>
  )
}

export default App
