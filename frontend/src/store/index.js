import { configureStore } from '@reduxjs/toolkit'
import authReducer       from './slices/authSlice'
import membershipReducer from './slices/membershipSlice'
import seatReducer       from './slices/seatSlice'

export const store = configureStore({
    reducer: {
        auth:       authReducer,
        membership: membershipReducer,
        seat:       seatReducer,
    },
})