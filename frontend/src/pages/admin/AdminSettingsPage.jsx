import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import api from '../../services/api'
import toast from 'react-hot-toast'

function SettingField({ label, value, onChange, integer = false, prefix }) {
    return (
        <div>
            <label className="label">{label}</label>
            <div className="flex items-center gap-1">
                {prefix && <span className="text-primary-400">{prefix}</span>}
                <input
                    type="number"
                    min="0"
                    step={integer ? '1' : '0.01'}
                    value={value}
                    onChange={e => onChange(e.target.value)}
                    className="input w-full"
                />
            </div>
        </div>
    )
}

export default function AdminSettingsPage() {
    const { t } = useTranslation()

    const [loading, setLoading] = useState(true)
    const [saving,  setSaving]  = useState(false)

    const [wifiName,        setWifiName]        = useState('')
    const [wifiPassword,    setWifiPassword]    = useState('')
    const [showPassword,    setShowPassword]    = useState(false)
    const [graceDays,       setGraceDays]       = useState('')
    const [convenienceFee,  setConvenienceFee]  = useState('')
    const [waterTankerRate, setWaterTankerRate] = useState('')

    const num = v => parseFloat(v) || 0

    const load = async () => {
        setLoading(true)
        try {
            const res = await api.get('/admin/settings')
            const d = res.data.data ?? {}
            setWifiName(d.wifiName ?? '')
            setWifiPassword(d.wifiPassword ?? '')
            setGraceDays(d.graceDays ?? 10)
            setConvenienceFee(d.convenienceFee ?? 0)
            setWaterTankerRate(d.waterTankerRate ?? 0)
        } catch {
            toast.error(t('adminSettings.loadFailed'))
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => { load() }, [])

    const save = async () => {
        setSaving(true)
        try {
            await api.post('/admin/settings', {
                wifiName,
                wifiPassword,
                graceDays: parseInt(graceDays || '0', 10),
                convenienceFee: num(convenienceFee),
                waterTankerRate: num(waterTankerRate),
            })
            toast.success(t('adminSettings.saved'))
        } catch (e) {
            toast.error(e.response?.data?.message || t('adminSettings.saveFailed'))
        } finally {
            setSaving(false)
        }
    }

    return (
        <div className="max-w-2xl mx-auto">
            <div className="mb-6">
                <h1 className="page-header">{t('adminSettings.title')}</h1>
                <p className="text-primary-400">{t('adminSettings.subtitle')}</p>
            </div>

            {loading ? (
                <div className="space-y-3">
                    {[1, 2, 3].map(i => <div key={i} className="shimmer h-24 rounded-xl" />)}
                </div>
            ) : (
                <>
                    {/* WiFi */}
                    <div className="card p-5 mb-4">
                        <p className="text-primary-400 text-xs uppercase tracking-widest mb-3">{t('adminSettings.wifiSection')}</p>
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                            <div>
                                <label className="label">{t('adminSettings.wifiName')}</label>
                                <input
                                    type="text"
                                    className="input w-full"
                                    value={wifiName}
                                    onChange={e => setWifiName(e.target.value)}
                                />
                            </div>
                            <div>
                                <label className="label">{t('adminSettings.wifiPassword')}</label>
                                <div className="flex items-center gap-2">
                                    <input
                                        type={showPassword ? 'text' : 'password'}
                                        className="input w-full"
                                        value={wifiPassword}
                                        onChange={e => setWifiPassword(e.target.value)}
                                    />
                                    <button
                                        type="button"
                                        onClick={() => setShowPassword(s => !s)}
                                        className="text-xs px-2 py-2 rounded-lg bg-primary-700/50 text-primary-300 hover:text-white border border-primary-700/40 transition-all flex-shrink-0"
                                    >
                                        {showPassword ? '🙈' : '👁'}
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* Grace period */}
                    <div className="card p-5 mb-4">
                        <p className="text-primary-400 text-xs uppercase tracking-widest mb-3">{t('adminSettings.gracePeriodSection')}</p>
                        <SettingField label={t('adminSettings.graceDays')} value={graceDays} onChange={setGraceDays} integer />
                    </div>

                    {/* Convenience fee */}
                    <div className="card p-5 mb-4">
                        <p className="text-primary-400 text-xs uppercase tracking-widest mb-3">{t('adminSettings.convenienceFeeSection')}</p>
                        <SettingField label={t('adminSettings.convenienceFee')} value={convenienceFee} onChange={setConvenienceFee} prefix="₹" />
                    </div>

                    {/* Water tanker rate */}
                    <div className="card p-5 mb-6">
                        <p className="text-primary-400 text-xs uppercase tracking-widest mb-3">{t('adminSettings.waterTankerSection')}</p>
                        <SettingField label={t('adminSettings.waterTankerRate')} value={waterTankerRate} onChange={setWaterTankerRate} prefix="₹" />
                    </div>

                    <button
                        onClick={save}
                        disabled={saving}
                        className="btn-primary w-full py-3 text-base"
                    >
                        {saving ? t('adminSettings.saving') : t('adminSettings.save')}
                    </button>
                </>
            )}
        </div>
    )
}
