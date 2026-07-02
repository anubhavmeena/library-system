import { createSlice, createAsyncThunk } from '@reduxjs/toolkit'
import api from '../../services/api'

export const fetchMyMembership = createAsyncThunk('membership/fetchMy', async (_, { rejectWithValue }) => {
    try { const res = await api.get('/memberships/my'); return res.data.data }
    catch (err) { return rejectWithValue(err.response?.data?.message) }
})

export const fetchQueuedMembership = createAsyncThunk('membership/fetchQueued', async (_, { rejectWithValue }) => {
    try { const res = await api.get('/memberships/my/queued'); return res.data.data }
    catch (err) { return rejectWithValue(err.response?.data?.message) }
})

export const fetchPlans = createAsyncThunk('membership/fetchPlans', async (_, { rejectWithValue }) => {
    try { const res = await api.get('/plans'); return res.data.data }
    catch (err) { return rejectWithValue(err.response?.data?.message) }
})

export const createPaymentOrder = createAsyncThunk('membership/createOrder', async (data, { rejectWithValue }) => {
    try { const res = await api.post('/payments/create-order', data); return res.data.data }
    catch (err) { return rejectWithValue(err.response?.data?.message) }
})

export const verifyPayment = createAsyncThunk('membership/verifyPayment', async (data, { rejectWithValue }) => {
    try { const res = await api.post('/payments/verify', data); return res.data.data }
    catch (err) { return rejectWithValue(err.response?.data?.message) }
})

export const createDuesOrder = createAsyncThunk('membership/createDuesOrder', async (_, { rejectWithValue }) => {
    try { const res = await api.post('/payments/dues/create-order'); return res.data.data }
    catch (err) { return rejectWithValue(err.response?.data?.message) }
})

export const verifyDuesPayment = createAsyncThunk('membership/verifyDuesPayment', async (data, { rejectWithValue }) => {
    try { const res = await api.post('/payments/dues/verify', data); return res.data.data }
    catch (err) { return rejectWithValue(err.response?.data?.message) }
})

const membershipSlice = createSlice({
    name: 'membership',
    initialState: { current: null, queued: null, plans: [], isLoading: false, order: null, error: null },
    reducers: {
        setOrder: (state, action) => { state.order = action.payload }
    },
    extraReducers: (builder) => {
        builder
            .addCase(fetchMyMembership.fulfilled,   (state, a) => { state.current = a.payload })
            .addCase(fetchQueuedMembership.fulfilled,(state, a) => { state.queued  = a.payload })
            .addCase(fetchPlans.fulfilled,          (state, a) => { state.plans   = a.payload })
            .addCase(createPaymentOrder.fulfilled,  (state, a) => { state.order   = a.payload })
            .addCase(verifyPayment.fulfilled,       (state, a) => {
                if (a.payload?.status === 'QUEUED') { state.queued  = a.payload }
                else                               { state.current = a.payload }
            })
            .addCase(createDuesOrder.fulfilled,     (state, a) => { state.order   = a.payload })
            .addCase(verifyDuesPayment.fulfilled,   (state, a) => { state.current = a.payload })
    }
})

export const { setOrder } = membershipSlice.actions
export default membershipSlice.reducer