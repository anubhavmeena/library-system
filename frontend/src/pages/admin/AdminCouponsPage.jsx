import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useDispatch, useSelector } from 'react-redux'
import toast from 'react-hot-toast'
import api from '../../services/api'
import Toggle from '../../components/common/Toggle'
import { fetchAdminCoupons, createCoupon, updateCoupon, deleteCoupon } from '../../store/slices/couponSlice'

export default function AdminCouponsPage() {
    const { t } = useTranslation()
    const dispatch = useDispatch()
    const { adminCoupons } = useSelector(s => s.coupon)

    const [loading, setLoading] = useState(true)
    const [couponsEnabled, setCouponsEnabled] = useState(true)
    const [togglingGlobal, setTogglingGlobal] = useState(false)

    const [code, setCode] = useState('')
    const [discountPercent, setDiscountPercent] = useState('')
    const [creating, setCreating] = useState(false)

    const load = async () => {
        setLoading(true)
        try {
            const [settingsRes] = await Promise.all([
                api.get('/admin/settings'),
                dispatch(fetchAdminCoupons()),
            ])
            setCouponsEnabled(settingsRes.data.data?.couponsEnabled ?? true)
        } catch {
            toast.error(t('adminCoupons.loadFailed'))
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => { load() }, [])

    // The backend saves app_settings as one whole object — fetch the latest
    // copy right before flipping this one field so we never clobber a value
    // an admin just changed on the Settings page in another tab.
    const toggleGlobal = async (next) => {
        setTogglingGlobal(true)
        const prev = couponsEnabled
        setCouponsEnabled(next)
        try {
            const current = await api.get('/admin/settings')
            await api.post('/admin/settings', { ...current.data.data, couponsEnabled: next })
        } catch (e) {
            setCouponsEnabled(prev)
            toast.error(e.response?.data?.message || t('adminCoupons.toggleFailed'))
        } finally {
            setTogglingGlobal(false)
        }
    }

    const handleCreate = async (e) => {
        e.preventDefault()
        const pct = parseInt(discountPercent, 10)
        if (!pct || pct < 1 || pct > 100) return toast.error(t('adminCoupons.createFailed'))
        setCreating(true)
        try {
            await dispatch(createCoupon({ code: code.trim() || undefined, discountPercent: pct })).unwrap()
            toast.success(t('adminCoupons.createSuccess'))
            setCode('')
            setDiscountPercent('')
        } catch (msg) {
            toast.error(msg || t('adminCoupons.createFailed'))
        } finally {
            setCreating(false)
        }
    }

    const handleToggleActive = async (coupon) => {
        try {
            await dispatch(updateCoupon({ id: coupon.id, isActive: !coupon.isActive })).unwrap()
        } catch (msg) {
            toast.error(msg || t('adminCoupons.updateFailed'))
        }
    }

    const handleDelete = async (coupon) => {
        if (!window.confirm(t('adminCoupons.deleteConfirm'))) return
        try {
            await dispatch(deleteCoupon(coupon.id)).unwrap()
            toast.success(t('adminCoupons.deleteSuccess'))
        } catch (msg) {
            toast.error(msg || t('adminCoupons.deleteFailed'))
        }
    }

    return (
        <div className="max-w-2xl mx-auto">
            <div className="mb-6">
                <h1 className="page-header">{t('adminCoupons.title')}</h1>
                <p className="text-primary-400">{t('adminCoupons.subtitle')}</p>
            </div>

            {loading ? (
                <div className="space-y-3">
                    {[1, 2].map(i => <div key={i} className="shimmer h-24 rounded-xl" />)}
                </div>
            ) : (
                <>
                    <div className="card p-5 mb-4 flex items-center justify-between gap-4">
                        <div>
                            <p className="text-white font-semibold">{t('adminCoupons.globalToggle')}</p>
                            <p className="text-primary-400 text-xs mt-1">{t('adminCoupons.globalToggleHint')}</p>
                        </div>
                        <Toggle
                            checked={couponsEnabled}
                            onChange={toggleGlobal}
                            disabled={togglingGlobal}
                            label={t('adminCoupons.globalToggle')}
                        />
                    </div>

                    <form onSubmit={handleCreate} className="card p-5 mb-4">
                        <p className="text-primary-400 text-xs uppercase tracking-widest mb-3">{t('adminCoupons.createTitle')}</p>
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-4">
                            <div>
                                <label className="label">{t('adminCoupons.codeLabel')}</label>
                                <input
                                    type="text"
                                    className="input w-full"
                                    placeholder={t('adminCoupons.codePlaceholder')}
                                    value={code}
                                    onChange={e => setCode(e.target.value.toUpperCase())}
                                    maxLength={30}
                                />
                            </div>
                            <div>
                                <label className="label">{t('adminCoupons.discountLabel')}</label>
                                <input
                                    type="number"
                                    min="1"
                                    max="100"
                                    step="1"
                                    className="input w-full"
                                    value={discountPercent}
                                    onChange={e => setDiscountPercent(e.target.value)}
                                />
                            </div>
                        </div>
                        <button type="submit" disabled={creating} className="btn-primary px-5 py-2.5">
                            {creating ? t('adminCoupons.creating') : t('adminCoupons.createBtn')}
                        </button>
                    </form>

                    <div className="card p-5">
                        <p className="text-primary-400 text-xs uppercase tracking-widest mb-3">{t('adminCoupons.listTitle')}</p>
                        {adminCoupons.length === 0 ? (
                            <p className="text-primary-500 text-sm">{t('adminCoupons.noCoupons')}</p>
                        ) : (
                            <div className="overflow-x-auto">
                                <table className="w-full text-sm">
                                    <thead>
                                        <tr className="text-left text-primary-500 text-xs uppercase tracking-wide border-b border-primary-700/30">
                                            <th className="py-2 pr-3">{t('adminCoupons.colCode')}</th>
                                            <th className="py-2 pr-3">{t('adminCoupons.colDiscount')}</th>
                                            <th className="py-2 pr-3">{t('adminCoupons.colStatus')}</th>
                                            <th className="py-2 pr-3">{t('adminCoupons.colCreated')}</th>
                                            <th className="py-2 pr-3 text-right">{t('adminCoupons.colActions')}</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {adminCoupons.map(c => (
                                            <tr key={c.id} className="border-b border-primary-800/30 last:border-0">
                                                <td className="py-2.5 pr-3 font-mono text-amber-400">{c.code}</td>
                                                <td className="py-2.5 pr-3 text-white">{c.discountPercent}%</td>
                                                <td className="py-2.5 pr-3">
                                                    <Toggle
                                                        checked={c.isActive}
                                                        onChange={() => handleToggleActive(c)}
                                                        label={c.isActive ? t('adminCoupons.active') : t('adminCoupons.inactive')}
                                                    />
                                                </td>
                                                <td className="py-2.5 pr-3 text-primary-400 text-xs">
                                                    {c.createdAt ? new Date(c.createdAt).toLocaleDateString('en-IN') : '—'}
                                                </td>
                                                <td className="py-2.5 pr-3 text-right">
                                                    <button onClick={() => handleDelete(c)} className="text-red-400 hover:text-red-300 text-xs">
                                                        {t('adminCoupons.deleteBtn')}
                                                    </button>
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        )}
                    </div>
                </>
            )}
        </div>
    )
}
