import { Avatar, Button, Menu, MenuItem } from '@mui/material';
import React from 'react'
import { useDispatch, useSelector } from 'react-redux';
import { Link, useNavigate } from 'react-router-dom';
import { BiUser } from 'react-icons/bi';
import { FaShoppingCart, FaUserShield } from 'react-icons/fa';
import { IoExitOutline } from 'react-icons/io5';
import BackDrop from './BackDrop';
import { logOutUser } from '../store/actions';

const UserMenu = () => {
  const [anchorEl, setAnchorEl] = React.useState(null);
  const open = Boolean(anchorEl);
  const {user} = useSelector((state) => state.auth);
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const handleClick = (event) => {
    setAnchorEl(event.currentTarget);
  };
  const handleClose = () => {
    setAnchorEl(null);
  };

  const logOutHandler = () => {
    dispatch(logOutUser(navigate));
  };



  const isAdmin = user && user?.roles.includes("ROLE_ADMIN");
  
  const isSeller = user && user?.roles.includes("ROLE_SELLER");
  return (
    <div className='relative z-30'>
      <div
      className='sm:border-[1px] sm:border-slate-400 flex flex-row items-center gap-1 rounded-full cursor-pointer hover:shadow-md transition text-slate-700'
        onClick={handleClick}
      >
        <Avatar alt='Menu' src='' />
      </div>
      <Menu
        sx={{width:"400px"}}
        id="basic-menu"
        anchorEl={anchorEl}
        open={open}
        onClose={handleClose}
        slotProps={{
          list: {
            'aria-labelledby': 'basic-button',
            sx: {width: 160},
          },
        }}
      >
        <Link to="/profile">
        <MenuItem  className='flex ga-2' 
            onClick={handleClose}>
                <BiUser className="text-xl" />
                <span className='font-bold tex-[16px] mt-1'>
                    {user?.username}
                </span>
        </MenuItem>
        </Link>

        <Link to="/profile/orders">
        <MenuItem  className='flex ga-2' 
            onClick={logOutHandler}>
                <FaShoppingCart className="text-xl" />
                <span className='font-semibold'>
                    Order
                </span>
        </MenuItem>
        </Link>

      {(isAdmin || isSeller) &&(
      <Link to={isAdmin ?"/admin" : "/admin/orders"}>
        <MenuItem  className='flex ga-2' 
            onClick={handleClose}>
                <FaUserShield className="text-xl" />
                <span className='font-semibold'>
                   { isAdmin ? "Admin Panel" : "Seller Panel"} 
                </span>
        </MenuItem>
        </Link>
        )}
      
        
        
  
        <MenuItem  className='flex ga-2' 
            onClick={logOutHandler}>
                <div className='font-semibold w-full flex gap-2 items-center bg-button-gradient px-4 py-1 text-white rounded-sm'>
                <IoExitOutline className="text-xl" />
                <span className='font-semibold'>
                    Logout
                </span>
                </div>
        </MenuItem>
      
      </Menu>
      {open && <BackDrop/>}
    </div>
  );
}

export default UserMenu
