import { useState, useEffect, useRef } from 'react'
import { useSelector } from 'react-redux'
import toast from 'react-hot-toast'
import { useTranslation } from 'react-i18next'
import api from '../../services/api'
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider'
import { DatePicker } from '@mui/x-date-pickers/DatePicker'
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFnsV3'
import { parseISO, format } from 'date-fns'

const DATE_PICKER_SX = {
    '& .MuiOutlinedInput-root': {
        backgroundColor: 'rgba(13,27,75,0.6)', borderRadius: '12px', color: '#f0f4ff',
        fontFamily: '"DM Sans", system-ui, sans-serif', fontSize: '0.875rem',
        '& fieldset': { borderColor: 'rgba(32,53,163,0.4)' },
        '&:hover fieldset': { borderColor: 'rgba(251,191,36,0.4)' },
        '&.Mui-focused fieldset': { borderColor: 'rgba(245,158,11,0.6)', boxShadow: '0 0 0 2px rgba(245,158,11,0.1)' },
    },
    '& .MuiInputAdornment-root .MuiIconButton-root': { color: '#6080f0', '&:hover': { color: '#fbbf24' } },
    '& .MuiInputBase-input': { padding: '12px 16px', color: '#f0f4ff' },
}

const DATE_PICKER_POPPER_SX = {
    '& .MuiPaper-root': { backgroundColor: '#1c2e84', border: '1px solid rgba(32,53,163,0.3)', borderRadius: '12px', color: '#f0f4ff' },
    '& .MuiPickersDay-root': {
        color: '#8aa6f8', backgroundColor: 'transparent',
        '&:hover': { backgroundColor: 'rgba(245,158,11,0.15)' },
        '&.Mui-selected': { backgroundColor: '#f59e0b', color: '#1a2a68', '&:hover': { backgroundColor: '#fbbf24' } },
    },
    '& .MuiPickersCalendarHeader-root': { color: '#f0f4ff' },
    '& .MuiPickersArrowSwitcher-button': { color: '#6080f0', '&:hover': { color: '#fbbf24' } },
    '& .MuiDayCalendar-weekDayLabel': { color: '#6080f0' },
    '& .MuiPickersYear-yearButton.Mui-selected': { backgroundColor: '#f59e0b', color: '#1a2a68' },
}

export default function ProfilePage() {
    const { user } = useSelector(s => s.auth)
    const { t } = useTranslation()
    const [profile, setProfile]     = useState(null)
    const [editing, setEditing]     = useState(false)
    const [loading, setLoading]     = useState(true)
    const [saving, setSaving]       = useState(false)
    const [uploading, setUploading] = useState(false)
    const [form, setForm] = useState({ name:'', fatherName:'', address:'', gender:'', dateOfBirth:'', email:'' })
    const fileRef = useRef()

    useEffect(() => {
        api.get('/users/me').then(r => {
            const p = r.data.data
            setProfile(p)
            setForm({ name: p.name||'', fatherName: p.fatherName||'', address: p.address||'', gender: p.gender||'', dateOfBirth: p.dateOfBirth||'', email: p.email||'' })
        }).catch(() => toast.error(t('profile.toasts.loadFailed')))
            .finally(() => setLoading(false))
    }, [])

    const handleSave = async () => {
        setSaving(true)
        try {
            const r = await api.patch('/users/me', form)
            setProfile(r.data.data)
            setEditing(false)
            toast.success(t('profile.toasts.profileUpdated'))
        } catch (e) {
            toast.error(e.response?.data?.message || t('profile.toasts.updateFailed'))
        } finally { setSaving(false) }
    }

    const handlePhoto = async (e) => {
        const file = e.target.files?.[0]
        if (!file) return
        if (file.size > 5 * 1024 * 1024) return toast.error(t('profile.toasts.fileTooLarge'))
        const formData = new FormData()
        formData.append('file', file)
        setUploading(true)
        try {
            const r = await api.post('/users/me/photo', formData)
            setProfile(p => ({ ...p, photoUrl: r.data.data.photoUrl }))
            toast.success(t('profile.toasts.photoUpdated'))
        } catch (e) {
            toast.error(e.response?.data?.message || t('profile.toasts.uploadFailed'))
        } finally { setUploading(false) }
    }

    if (loading) return (
        <div className="space-y-4">
            <div className="shimmer h-40 rounded-2xl" /><div className="shimmer h-64 rounded-2xl" />
        </div>
    )

    const initials = profile?.name?.split(' ').map(n=>n[0]).join('').toUpperCase().slice(0,2) || 'S'

    const fieldDefs = [
        { label: t('profile.fields.fullName'),   key: 'name',        editable: true },
        { label: t('profile.fields.fatherName'), key: 'fatherName',  editable: true },
        { label: t('profile.fields.email'),      key: 'email',       editable: true },
        { label: t('profile.fields.mobile'),     key: 'mobile',      editable: false },
        { label: t('profile.fields.dob'),        key: 'dateOfBirth', editable: true, type: 'date' },
        { label: t('profile.fields.gender'),     key: 'gender',      editable: true, type: 'select',
          options: [
              { value: 'Male',   label: t('profile.gender.male') },
              { value: 'Female', label: t('profile.gender.female') },
              { value: 'Other',  label: t('profile.gender.other') },
          ]
        },
    ]

    return (
        <div>
            <div className="mb-8">
                <h1 className="page-header">{t('profile.title')}</h1>
                <p className="text-primary-400">{t('profile.subtitle')}</p>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                <div className="card p-6 flex flex-col items-center text-center">
                    <div className="relative mb-4">
                        {profile?.photoUrl ? (
                            <img src={profile.photoUrl} alt="Profile" className="w-28 h-28 rounded-full object-cover border-4 border-primary-700/40" />
                        ) : (
                            <div className="w-28 h-28 rounded-full bg-gradient-to-br from-amber-400 to-primary-600 flex items-center justify-center text-3xl font-bold text-white border-4 border-primary-700/40">
                                {initials}
                            </div>
                        )}
                        {uploading && (
                            <div className="absolute inset-0 rounded-full bg-primary-900/70 flex items-center justify-center">
                                <div className="w-6 h-6 border-2 border-amber-400 border-t-transparent rounded-full animate-spin" />
                            </div>
                        )}
                    </div>
                    <h2 className="text-white font-semibold text-lg">{profile?.name}</h2>
                    <p className="text-primary-400 text-sm mt-1">{profile?.mobile || profile?.email}</p>
                    <span className="mt-2 px-3 py-1 rounded-full bg-amber-500/20 border border-amber-500/30 text-amber-400 text-xs font-medium">{profile?.role}</span>
                    <input ref={fileRef} type="file" accept="image/jpeg,image/png,image/webp" className="hidden" onChange={handlePhoto} />
                    <button onClick={() => fileRef.current?.click()} disabled={uploading} className="mt-5 btn-outline text-sm px-4 py-2 w-full">
                        {uploading ? t('profile.uploading') : t('profile.changePhoto')}
                    </button>
                    {profile?.photoUrl && (
                        <button onClick={async () => {
                            try { await api.delete('/users/me/photo'); setProfile(p=>({...p,photoUrl:null})); toast.success(t('profile.toasts.photoRemoved')) }
                            catch { toast.error(t('profile.toasts.removeFailed')) }
                        }} className="mt-2 text-primary-500 text-xs hover:text-red-400 transition-colors">
                            {t('profile.removePhoto')}
                        </button>
                    )}
                    <div className="mt-6 w-full pt-5 border-t border-primary-700/30 text-left space-y-2">
                        <p className="text-primary-500 text-xs">{t('profile.memberSince')}</p>
                        <p className="text-white text-sm">{profile?.createdAt?.split('T')[0] || '—'}</p>
                    </div>
                </div>

                <div className="card p-6 lg:col-span-2">
                    <div className="flex items-center justify-between mb-6">
                        <h2 className="section-title">{t('profile.personalDetails')}</h2>
                        {!editing ? (
                            <button onClick={() => setEditing(true)} className="btn-outline text-sm px-4 py-2">{t('profile.edit')}</button>
                        ) : (
                            <div className="flex gap-2">
                                <button onClick={() => setEditing(false)} className="btn-ghost text-sm px-4 py-2">{t('profile.cancel')}</button>
                                <button onClick={handleSave} disabled={saving} className="btn-primary text-sm px-4 py-2">
                                    {saving ? t('profile.saving') : t('profile.saveChanges')}
                                </button>
                            </div>
                        )}
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
                        {fieldDefs.map(({ label, key, editable, type, options }) => (
                            <div key={key}>
                                <label className="label">{label}</label>
                                {editing && editable ? (
                                    type === 'select' ? (
                                        <select className="input" value={form[key]} onChange={e=>setForm(f=>({...f,[key]:e.target.value}))}>
                                            <option value="">{t('profile.selectGender')}</option>
                                            {options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
                                        </select>
                                    ) : type === 'date' ? (
                                        <LocalizationProvider dateAdapter={AdapterDateFns}>
                                            <DatePicker
                                                value={form[key] ? parseISO(form[key]) : null}
                                                onChange={(d) => setForm(f => ({ ...f, [key]: d ? format(d, 'yyyy-MM-dd') : '' }))}
                                                sx={{ width: '100%', ...DATE_PICKER_SX }}
                                                slotProps={{ textField: { size: 'small' }, popper: { sx: DATE_PICKER_POPPER_SX } }}
                                            />
                                        </LocalizationProvider>
                                    ) : (
                                        <input className="input" type={type||'text'} value={form[key]} onChange={e=>setForm(f=>({...f,[key]:e.target.value}))} />
                                    )
                                ) : (
                                    <div className="input bg-primary-900/30 text-primary-300 cursor-default">
                                        {profile?.[key] || <span className="text-primary-600">{t('profile.notProvided')}</span>}
                                    </div>
                                )}
                            </div>
                        ))}
                        <div className="sm:col-span-2">
                            <label className="label">{t('profile.fields.address')}</label>
                            {editing ? (
                                <textarea className="input resize-none" rows={3} value={form.address}
                                          onChange={e=>setForm(f=>({...f,address:e.target.value}))} placeholder={t('profile.addressPlaceholder')} />
                            ) : (
                                <div className="input bg-primary-900/30 text-primary-300 cursor-default min-h-[80px]">
                                    {profile?.address || <span className="text-primary-600">{t('profile.notProvided')}</span>}
                                </div>
                            )}
                        </div>
                    </div>
                    {editing && (
                        <p className="text-primary-500 text-xs mt-4">{t('profile.mobileNote')}</p>
                    )}
                </div>
            </div>
        </div>
    )
}
