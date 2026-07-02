import { createSlice, createAsyncThunk } from '@reduxjs/toolkit'
import api from '../../services/api'

const initialState = {
    user:         JSON.parse(localStorage.getItem('user') || 'null'),
    token:        localStorage.getItem('token') || null,
    isLoading:    false,
    error:        null,
    otpSent:      false,
    otpVerified:  false,
    sessionToken: null,
    isNewUser:    false,
}

export const sendOtp = createAsyncThunk('auth/sendOtp', async ({ contact, contactType }, { rejectWithValue }) => {
    try {
        await api.post('/auth/send-otp', { contact, contactType })
        return { contact, contactType }
    } catch (err) { return rejectWithValue(err.response?.data?.message || 'Failed to send OTP') }
})

export const verifyOtp = createAsyncThunk('auth/verifyOtp', async ({ contact, otp }, { rejectWithValue }) => {
    try {
        const res = await api.post('/auth/verify-otp', { contact, otp })
        return res.data.data
    } catch (err) { return rejectWithValue(err.response?.data?.message || 'Invalid OTP') }
})

export const registerUser = createAsyncThunk('auth/register', async (data, { rejectWithValue }) => {
    try {
        const res = await api.post('/auth/register', data)
        return res.data.data
    } catch (err) { return rejectWithValue(err.response?.data?.message || 'Registration failed') }
})

export const loginUser = createAsyncThunk('auth/login', async (data, { rejectWithValue }) => {
    try {
        const res = await api.post('/auth/login', data)
        return res.data.data
    } catch (err) { return rejectWithValue(err.response?.data?.message || 'Login failed') }
})

export const adminLogin = createAsyncThunk('auth/adminLogin', async (data, { rejectWithValue }) => {
    try {
        const res = await api.post('/auth/admin/login', data)
        return res.data.data
    } catch (err) { return rejectWithValue(err.response?.data?.message || 'Admin login failed') }
})

const authSlice = createSlice({
    name: 'auth',
    initialState,
    reducers: {
        logout: (state) => {
            state.user = null; state.token = null
            state.otpSent = false; state.otpVerified = false; state.sessionToken = null
            localStorage.removeItem('token'); localStorage.removeItem('user')
        },
        resetAuthState: (state) => {
            state.otpSent = false; state.otpVerified = false
            state.sessionToken = null; state.error = null
        },
        clearError: (state) => { state.error = null }
    },
    extraReducers: (builder) => {
        builder
            .addCase(sendOtp.pending,   (state) => { state.isLoading = true;  state.error = null })
            .addCase(sendOtp.fulfilled, (state) => { state.isLoading = false; state.otpSent = true })
            .addCase(sendOtp.rejected,  (state, a) => { state.isLoading = false; state.error = a.payload })

            .addCase(verifyOtp.pending,   (state) => { state.isLoading = true; state.error = null })
            .addCase(verifyOtp.fulfilled, (state, a) => {
                state.isLoading = false; state.otpVerified = true
                // Backend field is `isNewUser` (boolean) but Lombok's getter for an
                // "is"-prefixed boolean is isNewUser(), which Jackson serializes by
                // stripping the "is" prefix — so the JSON key is actually "newUser".
                state.sessionToken = a.payload.sessionToken; state.isNewUser = a.payload.newUser
            })
            .addCase(verifyOtp.rejected,  (state, a) => { state.isLoading = false; state.error = a.payload })

            .addCase(registerUser.pending,   (state) => { state.isLoading = true })
            .addCase(registerUser.fulfilled, (state, a) => {
                state.isLoading = false; state.user = a.payload.user; state.token = a.payload.accessToken
                localStorage.setItem('token', a.payload.accessToken)
                localStorage.setItem('user', JSON.stringify(a.payload.user))
            })
            .addCase(registerUser.rejected, (state, a) => { state.isLoading = false; state.error = a.payload })

            .addCase(loginUser.fulfilled, (state, a) => {
                state.user = a.payload.user; state.token = a.payload.accessToken
                localStorage.setItem('token', a.payload.accessToken)
                localStorage.setItem('user', JSON.stringify(a.payload.user))
            })
            .addCase(adminLogin.fulfilled, (state, a) => {
                state.user = a.payload.user; state.token = a.payload.accessToken
                localStorage.setItem('token', a.payload.accessToken)
                localStorage.setItem('user', JSON.stringify(a.payload.user))
            })
    }
})

export const { logout, resetAuthState, clearError } = authSlice.actions
export default authSlice.reducer