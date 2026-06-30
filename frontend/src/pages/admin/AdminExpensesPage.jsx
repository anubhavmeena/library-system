import { useEffect, useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import api from '../../services/api'
import toast from 'react-hot-toast'

const MONTHS = [
    'January','February','March','April','May','June',
    'July','August','September','October','November','December'
]

function ExpenseField({ label, value, onChange, integer = false }) {
    return (
        <div>
            <label className="label">{label}</label>
            <input
                type="number"
                min="0"
                step={integer ? '1' : '0.01'}
                value={value}
                onChange={e => onChange(integer ? parseInt(e.target.value || '0', 10) : e.target.value)}
                className="input w-full"
            />
        </div>
    )
}

export default function AdminExpensesPage() {
    const { t } = useTranslation()
    const now = new Date()

    const [year,  setYear]  = useState(now.getFullYear())
    const [month, setMonth] = useState(now.getMonth() + 1)
    const [loading,  setLoading]  = useState(true)
    const [saving,   setSaving]   = useState(false)

    const [tankerQty,   setTankerQty]   = useState(0)
    const [tankerPrice, setTankerPrice] = useState('')
    const [electricity, setElectricity] = useState('')
    const [internet,    setInternet]    = useState('')
    const [miscItems,   setMiscItems]   = useState([])   // [{description:'', amount:''}]

    const num = v => parseFloat(v) || 0

    const waterSubtotal = tankerQty * num(tankerPrice)
    const miscTotal     = miscItems.reduce((s, it) => s + num(it.amount), 0)
    const total         = waterSubtotal + num(electricity) + num(internet) + miscTotal
    const currency = n => `₹${n.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

    const load = async () => {
        setLoading(true)
        try {
            const res = await api.get('/admin/expenses', { params: { year, month } })
            const d = res.data.data ?? {}
            setTankerQty(d.waterTankerQty ?? 0)
            setTankerPrice(d.waterTankerPrice ?? '')
            setElectricity(d.electricityBill ?? '')
            setInternet(d.internetBill ?? '')
            setMiscItems((d.miscItems ?? []).map(it => ({
                description: it.description ?? '',
                amount: it.amount != null ? String(it.amount) : ''
            })))
        } catch {
            toast.error(t('adminExpenses.loadFailed'))
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => { load() }, [year, month])

    const addMiscItem    = () => setMiscItems(prev => [...prev, { description: '', amount: '' }])
    const removeMiscItem = i  => setMiscItems(prev => prev.filter((_, idx) => idx !== i))
    const updateMiscItem = (i, field, val) =>
        setMiscItems(prev => prev.map((it, idx) => idx === i ? { ...it, [field]: val } : it))

    const save = async () => {
        setSaving(true)
        try {
            await api.post('/admin/expenses', {
                year, month,
                waterTankerQty:   tankerQty,
                waterTankerPrice: num(tankerPrice),
                electricityBill:  num(electricity),
                internetBill:     num(internet),
                miscItems: miscItems
                    .filter(it => it.description.trim())
                    .map(it => ({ description: it.description.trim(), amount: num(it.amount) })),
            })
            toast.success(t('adminExpenses.saved'))
        } catch {
            toast.error(t('adminExpenses.saveFailed'))
        } finally {
            setSaving(false)
        }
    }

    const yearOptions = useMemo(() => {
        const y = now.getFullYear()
        return [y - 1, y, y + 1]
    }, [])

    return (
        <div className="max-w-2xl mx-auto">
            <div className="mb-6">
                <h1 className="page-header">{t('adminExpenses.title')}</h1>
                <p className="text-primary-400">{t('adminExpenses.subtitle')}</p>
            </div>

            {/* Month / Year picker */}
            <div className="card p-5 mb-6 flex flex-wrap gap-4 items-end">
                <div>
                    <label className="label">{t('adminExpenses.monthLabel')}</label>
                    <select
                        value={month}
                        onChange={e => setMonth(Number(e.target.value))}
                        className="input"
                    >
                        {MONTHS.map((m, i) => (
                            <option key={i + 1} value={i + 1}>{m}</option>
                        ))}
                    </select>
                </div>
                <div>
                    <label className="label">Year</label>
                    <select
                        value={year}
                        onChange={e => setYear(Number(e.target.value))}
                        className="input"
                    >
                        {yearOptions.map(y => <option key={y} value={y}>{y}</option>)}
                    </select>
                </div>
            </div>

            {loading ? (
                <div className="space-y-3">
                    {[1,2,3,4,5].map(i => <div key={i} className="shimmer h-16 rounded-xl" />)}
                </div>
            ) : (
                <>
                    {/* Water tankers */}
                    <div className="card p-5 mb-4">
                        <p className="text-primary-400 text-xs uppercase tracking-widest mb-3">{t('adminExpenses.waterTankers')}</p>
                        <div className="grid grid-cols-2 gap-4">
                            <ExpenseField label={t('adminExpenses.tankerQty')}   value={tankerQty}   onChange={setTankerQty}   integer />
                            <ExpenseField label={t('adminExpenses.tankerPrice')}  value={tankerPrice} onChange={setTankerPrice} />
                        </div>
                        <p className="text-primary-500 text-xs mt-2">
                            {t('adminExpenses.waterSubtotal')}: <span className="text-amber-400 font-semibold">{currency(waterSubtotal)}</span>
                        </p>
                    </div>

                    {/* Other bills */}
                    <div className="card p-5 mb-4 space-y-4">
                        <ExpenseField label={t('adminExpenses.electricityBill')} value={electricity} onChange={setElectricity} />
                        <ExpenseField label={t('adminExpenses.internetBill')}    value={internet}    onChange={setInternet} />
                    </div>

                    {/* Miscellaneous line items */}
                    <div className="card p-5 mb-6">
                        <div className="flex items-center justify-between mb-3">
                            <p className="text-primary-400 text-xs uppercase tracking-widest">{t('adminExpenses.miscellaneous')}</p>
                            <button
                                onClick={addMiscItem}
                                className="flex items-center gap-1 text-xs px-3 py-1.5 rounded-lg bg-amber-500/20 text-amber-400 hover:bg-amber-500/30 border border-amber-500/30 transition-all font-medium"
                            >
                                + Add
                            </button>
                        </div>

                        {miscItems.length === 0 ? (
                            <p className="text-primary-600 text-sm text-center py-3">No items — click + Add to record a miscellaneous expense</p>
                        ) : (
                            <div className="space-y-2">
                                {miscItems.map((item, i) => (
                                    <div key={i} className="flex gap-2 items-center">
                                        <input
                                            type="text"
                                            placeholder="Description"
                                            value={item.description}
                                            onChange={e => updateMiscItem(i, 'description', e.target.value)}
                                            className="input flex-1 text-sm py-2"
                                        />
                                        <input
                                            type="number"
                                            min="0"
                                            step="0.01"
                                            placeholder="₹ Amount"
                                            value={item.amount}
                                            onChange={e => updateMiscItem(i, 'amount', e.target.value)}
                                            className="input w-32 text-sm py-2"
                                        />
                                        <button
                                            onClick={() => removeMiscItem(i)}
                                            className="text-primary-500 hover:text-red-400 transition-colors text-lg leading-none px-1"
                                            title="Remove"
                                        >
                                            ✕
                                        </button>
                                    </div>
                                ))}
                            </div>
                        )}

                        {miscItems.length > 0 && (
                            <p className="text-primary-500 text-xs mt-3 text-right">
                                Subtotal: <span className="text-amber-400 font-semibold">{currency(miscTotal)}</span>
                            </p>
                        )}
                    </div>

                    {/* Total */}
                    <div className="card p-5 mb-6 bg-gradient-to-br from-amber-500/10 to-transparent border-amber-500/20">
                        <div className="flex items-center justify-between">
                            <p className="text-primary-300 font-medium">{t('adminExpenses.totalExpense')}</p>
                            <p className="text-3xl font-bold text-amber-400">{currency(total)}</p>
                        </div>
                        <div className="mt-3 space-y-1 text-xs text-primary-500">
                            <div className="flex justify-between"><span>{t('adminExpenses.waterTankers')}</span><span>{currency(waterSubtotal)}</span></div>
                            <div className="flex justify-between"><span>{t('adminExpenses.electricityBill')}</span><span>{currency(num(electricity))}</span></div>
                            <div className="flex justify-between"><span>{t('adminExpenses.internetBill')}</span><span>{currency(num(internet))}</span></div>
                            <div className="flex justify-between"><span>{t('adminExpenses.miscellaneous')}</span><span>{currency(miscTotal)}</span></div>
                        </div>
                    </div>

                    <button
                        onClick={save}
                        disabled={saving}
                        className="btn-primary w-full py-3 text-base"
                    >
                        {saving ? t('adminExpenses.saving') : t('adminExpenses.save')}
                    </button>
                </>
            )}
        </div>
    )
}
