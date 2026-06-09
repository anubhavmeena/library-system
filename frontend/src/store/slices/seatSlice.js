import { createSlice, createAsyncThunk } from '@reduxjs/toolkit'
import api from '../../services/api'

export const fetchSeatAvailability = createAsyncThunk('seat/fetchAvailability',
    async ({ shift, date } = {}, { rejectWithValue }) => {
        try {
            const params = new URLSearchParams()
            if (shift) params.append('shift', shift)
            if (date)  params.append('date',  date)
            const res = await api.get(`/seats/availability?${params}`)
            return res.data.data?.seats ?? []
        } catch (err) { return rejectWithValue(err.response?.data?.message) }
    }
)

export const bookSeat = createAsyncThunk('seat/bookSeat',
    async (data, { rejectWithValue }) => {
        try {
            const res = await api.post('/seats/book', data)
            return res.data.data
        } catch (err) { return rejectWithValue(err.response?.data?.message) }
    }
)

const seatSlice = createSlice({
    name: 'seat',
    initialState: { seats: [], selectedSeat: null, isLoading: false, error: null },
    reducers: {
        selectSeat:      (state, a) => { state.selectedSeat = a.payload },
        clearSelectedSeat:(state)   => { state.selectedSeat = null },
    },
    extraReducers: (builder) => {
        builder
            .addCase(fetchSeatAvailability.pending,   (state) => { state.isLoading = true })
            .addCase(fetchSeatAvailability.fulfilled, (state, a) => { state.isLoading = false; state.seats = a.payload })
            .addCase(fetchSeatAvailability.rejected,  (state, a) => { state.isLoading = false; state.error = a.payload })
    }
})

export const { selectSeat, clearSelectedSeat } = seatSlice.actions
export default seatSlice.reducer