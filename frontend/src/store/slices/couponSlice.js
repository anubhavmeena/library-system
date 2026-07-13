import { createSlice, createAsyncThunk } from '@reduxjs/toolkit'
import api from '../../services/api'

// ── Student-facing ───────────────────────────────────────────────────────────

export const fetchActiveCoupons = createAsyncThunk('coupon/fetchActive', async (_, { rejectWithValue }) => {
    try { const res = await api.get('/payments/coupons/active'); return res.data.data }
    catch (err) { return rejectWithValue(err.response?.data?.message) }
})

export const validateCoupon = createAsyncThunk('coupon/validate', async (code, { rejectWithValue }) => {
    try { const res = await api.post('/payments/validate-coupon', { code }); return res.data.data }
    catch (err) { return rejectWithValue(err.response?.data?.message) }
})

// ── Admin ─────────────────────────────────────────────────────────────────────

export const fetchAdminCoupons = createAsyncThunk('coupon/fetchAdminAll', async (_, { rejectWithValue }) => {
    try { const res = await api.get('/admin/coupons'); return res.data.data }
    catch (err) { return rejectWithValue(err.response?.data?.message) }
})

export const createCoupon = createAsyncThunk('coupon/create', async (data, { rejectWithValue }) => {
    try { const res = await api.post('/admin/coupons', data); return res.data.data }
    catch (err) { return rejectWithValue(err.response?.data?.message) }
})

export const updateCoupon = createAsyncThunk('coupon/update', async ({ id, ...data }, { rejectWithValue }) => {
    try { const res = await api.patch(`/admin/coupons/${id}`, data); return res.data.data }
    catch (err) { return rejectWithValue(err.response?.data?.message) }
})

export const deleteCoupon = createAsyncThunk('coupon/delete', async (id, { rejectWithValue }) => {
    try { await api.delete(`/admin/coupons/${id}`); return id }
    catch (err) { return rejectWithValue(err.response?.data?.message) }
})

const couponSlice = createSlice({
    name: 'coupon',
    initialState: {
        activeCoupons: [],
        applied: null,       // { code, discountPercent } once validated at checkout
        adminCoupons: [],
        isLoading: false,
        error: null,
    },
    reducers: {
        clearAppliedCoupon: (state) => { state.applied = null }
    },
    extraReducers: (builder) => {
        builder
            .addCase(fetchActiveCoupons.fulfilled, (state, a) => { state.activeCoupons = a.payload })
            .addCase(validateCoupon.fulfilled,     (state, a) => { state.applied = a.payload })
            .addCase(validateCoupon.rejected,      (state, a) => { state.applied = null; state.error = a.payload })
            .addCase(fetchAdminCoupons.fulfilled,  (state, a) => { state.adminCoupons = a.payload })
            .addCase(createCoupon.fulfilled,       (state, a) => { state.adminCoupons.unshift(a.payload) })
            .addCase(updateCoupon.fulfilled,       (state, a) => {
                state.adminCoupons = state.adminCoupons.map(c => c.id === a.payload.id ? a.payload : c)
            })
            .addCase(deleteCoupon.fulfilled,       (state, a) => {
                state.adminCoupons = state.adminCoupons.filter(c => c.id !== a.payload)
            })
    }
})

export const { clearAppliedCoupon } = couponSlice.actions
export default couponSlice.reducer
